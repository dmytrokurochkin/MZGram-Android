# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# The settings of plugins, Python side:
#
# - values: get_setting / set_setting, kept by MZGramPluginSettingsStore on
#   the Java side (SharedPreferences of each plugin, values as JSON), so
#   they outlive the interpreter; an in-memory store stands in when there is
#   no Java (tests on a computer);
# - pages: the plugin engine registers each plugin's create_settings();
#   settings_json() turns its rows (ui.settings) into the JSON that
#   MZGramPluginSettingsActivity draws, and keeps the callbacks here, by row,
#   for setting_changed_json() and setting_click_json().
#
# Everything Java calls takes and returns one JSON string and never raises.
#
# Based on exteraless extera_utils/plugin_loader.py (get_settings_json,
# notify_setting_changed, dispatch_setting_click) and plugin_settings.py
# (GPL-3.0): the JSON of the rows, the ids of rows and the conversion of
# stored values.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import hashlib
import inspect
import json
import threading

from mzgram_plugins import permissions
from ui import settings as ui_settings

STORE_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginSettingsStore"

# Sub pages inside sub pages: deeper than this is cut off (a plugin calling
# itself would never end)
MAX_DEPTH = 8

_lock = threading.RLock()


# ---------- values ----------

class _MemoryStore:
    def __init__(self):
        self._values = {}

    def get(self, plugin_id, key):
        return self._values.get(plugin_id, {}).get(key)

    def set(self, plugin_id, key, raw):
        if raw is None:
            self._values.get(plugin_id, {}).pop(key, None)
        else:
            self._values.setdefault(plugin_id, {})[key] = raw

    def export(self, plugin_id):
        return json.dumps(self._values.get(plugin_id, {}), ensure_ascii=False)


class _JavaStore:
    def __init__(self, store):
        self._store = store

    def get(self, plugin_id, key):
        raw = self._store.get(plugin_id, key)
        return None if raw is None else str(raw)

    def set(self, plugin_id, key, raw):
        self._store.set(plugin_id, key, raw)

    def export(self, plugin_id):
        return str(self._store.export(plugin_id))


_store = None


def _backend():
    global _store
    if _store is None:
        try:
            from java import jclass
            _store = _JavaStore(jclass(STORE_CLASS))
        except Exception:
            _store = _MemoryStore()
    return _store


def use_memory_store():
    """Keep values in memory only (tests); returns the store."""
    global _store
    _store = _MemoryStore()
    return _store


_TRUE_WORDS = ("true", "1", "yes", "on")
_FALSE_WORDS = ("false", "0", "no", "off", "")


def coerce_value(value, default):
    """A stored value in the type of the default: a Switch stays a bool."""
    if value is None or default is None:
        return value
    if isinstance(default, bool):
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            word = value.strip().lower()
            if word in _TRUE_WORDS:
                return True
            if word in _FALSE_WORDS:
                return False
            return default
        if isinstance(value, (int, float)):
            return bool(value)
        return default
    if isinstance(default, (int, float)) and not isinstance(value, bool):
        if isinstance(value, (int, float)):
            return type(default)(value)
        if isinstance(value, str):
            try:
                return type(default)(value.strip())
            except ValueError:
                return default
        return default
    if isinstance(default, str) and not isinstance(value, str):
        return str(value)
    return value


def get_setting(plugin_id, key, default=None):
    with _lock:
        raw = _backend().get(plugin_id, str(key))
    if raw is None:
        return default
    try:
        value = json.loads(raw)
    except (ValueError, TypeError):
        value = raw
    return coerce_value(value, default)


def set_setting(plugin_id, key, value):
    """Keeps value (anything JSON can hold); None removes the key."""
    raw = None if value is None else json.dumps(value, ensure_ascii=False)
    with _lock:
        _backend().set(plugin_id, str(key), raw)


def remove_setting(plugin_id, key):
    set_setting(plugin_id, key, None)


def get_settings(plugin_id):
    with _lock:
        raw = _backend().export(plugin_id)
    try:
        data = json.loads(raw)
    except (ValueError, TypeError):
        return {}
    out = {}
    for key, value in data.items():
        try:
            out[key] = json.loads(value)
        except (ValueError, TypeError):
            out[key] = value
    return out


# ---------- pages ----------

class _Page:
    def __init__(self, create_settings, log):
        self.create_settings = create_settings
        self.log = log
        self.change_callbacks = {}
        self.click_callbacks = {}
        self.long_click_callbacks = {}


_pages = {}


def register(plugin_id, create_settings, log=None):
    """The engine calls this for a plugin that has settings; log(text) gets
    the problems of its rows."""
    with _lock:
        _pages[plugin_id] = _Page(create_settings, log)


def unregister(plugin_id):
    with _lock:
        _pages.pop(plugin_id, None)


def is_registered(plugin_id):
    with _lock:
        return plugin_id in _pages


def _log(page, text):
    if page.log is not None:
        try:
            page.log(text)
        except Exception:
            pass


def _put(data, key, value):
    if value is not None:
        data[key] = value


def _identity(item, scope):
    kind = type(item).__name__
    key = getattr(item, "key", None)
    if key:
        return [scope, kind, "key", key]
    alias = getattr(item, "link_alias", None)
    if alias:
        return [scope, kind, "alias", alias]
    return [scope, kind, "text", getattr(item, "text", None) or getattr(item, "hint", None) or ""]


def _row_id(identity, occurrences):
    # Two rows alike on one page stay apart by their order
    base = json.dumps(identity, ensure_ascii=False)
    occurrence = occurrences.get(base, 0)
    occurrences[base] = occurrence + 1
    text = json.dumps([identity, occurrence], ensure_ascii=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:24]


def _callbacks(data, item, row_id, pending):
    on_click = getattr(item, "on_click", None)
    if callable(on_click):
        pending.click_callbacks[row_id] = on_click
        data["click"] = True
    on_long_click = getattr(item, "on_long_click", None)
    if callable(on_long_click):
        pending.long_click_callbacks[row_id] = on_long_click
        data["long_click"] = True
    on_change = getattr(item, "on_change", None)
    key = getattr(item, "key", None)
    if callable(on_change) and key:
        pending.change_callbacks[key] = on_change


def _sub_page(plugin_id, item, row_id, pending, depth):
    create = getattr(item, "create_sub_fragment", None)
    if not callable(create):
        return None
    if depth >= MAX_DEPTH:
        _log(pending, "settings: sub pages deeper than %d are not shown" % MAX_DEPTH)
        return None
    try:
        sub_items = create()
    except Exception as e:
        _log(pending, "create_sub_fragment() failed: %s: %s" % (type(e).__name__, e))
        return None
    rows = _serialize_list(plugin_id, sub_items, row_id, pending, depth + 1)
    return rows if rows else None


def _serialize(plugin_id, item, row_id, pending, depth):
    s = ui_settings
    if isinstance(item, s.Header):
        return {"type": "header", "text": str(item.text)}
    if isinstance(item, s.Divider):
        data = {"type": "divider"}
        _put(data, "text", None if item.text is None else str(item.text))
        return data
    if isinstance(item, s.Switch):
        default = bool(item.default)
        data = {"type": "switch", "key": str(item.key), "text": str(item.text),
                "value": bool(coerce_value(get_setting(plugin_id, item.key, default), default))}
    elif isinstance(item, s.Selector):
        entries = [str(entry) for entry in (item.items or [])]
        default = int(item.default) if isinstance(item.default, (int, float)) else 0
        value = get_setting(plugin_id, item.key, default)
        if not isinstance(value, int) or isinstance(value, bool) or not 0 <= value < len(entries):
            value = default if 0 <= default < len(entries) else 0
        data = {"type": "selector", "key": str(item.key), "text": str(item.text),
                "items": entries, "value": value, "default": default}
    elif isinstance(item, s.Input):
        default = "" if item.default is None else str(item.default)
        data = {"type": "input", "key": str(item.key), "text": str(item.text),
                "value": str(get_setting(plugin_id, item.key, default))}
    elif isinstance(item, s.EditText):
        default = "" if item.default is None else str(item.default)
        data = {"type": "edittext", "key": str(item.key), "hint": str(item.hint),
                "value": str(get_setting(plugin_id, item.key, default)),
                "multiline": bool(item.multiline)}
        if isinstance(item.max_length, int) and item.max_length > 0:
            data["max_length"] = item.max_length
    elif isinstance(item, s.Text):
        data = {"type": "text", "text": str(item.text),
                "accent": bool(item.accent), "red": bool(item.red)}
        _put(data, "sub_page", _sub_page(plugin_id, item, row_id, pending, depth))
    elif isinstance(item, s.Custom):
        # Drawn once the SDK has its UI components
        return {"type": "unsupported", "kind": "custom"}
    else:
        _log(pending, "settings: %s is not a settings row" % type(item).__name__)
        return None
    subtext = getattr(item, "subtext", None)
    _put(data, "subtext", None if subtext is None else str(subtext))
    icon = getattr(item, "icon", None)
    _put(data, "icon", icon if isinstance(icon, str) and icon else None)
    _callbacks(data, item, row_id, pending)
    return data


def _serialize_list(plugin_id, items, scope, pending, depth):
    out = []
    occurrences = {}
    for item in items or []:
        try:
            row_id = _row_id(_identity(item, scope), occurrences)
            data = _serialize(plugin_id, item, row_id, pending, depth)
        except Exception as e:
            _log(pending, "settings row skipped: %s: %s" % (type(e).__name__, e))
            continue
        if data is not None:
            data["row_id"] = row_id
            _put(data, "link_alias", getattr(item, "link_alias", None) or None)
            out.append(data)
    return out


def _reply(**fields):
    return json.dumps(fields, ensure_ascii=False)


def settings_json(plugin_id):
    """{"ok": true, "rows": [...]} or {"ok": false, "error": "..."}."""
    with _lock:
        page = _pages.get(plugin_id)
    if page is None:
        return _reply(ok=False, error="the plugin has no settings page")
    try:
        with permissions.plugin_context(plugin_id):
            items = page.create_settings()
    except Exception as e:
        summary = "create_settings() failed: %s: %s" % (type(e).__name__, e)
        _log(page, summary)
        return _reply(ok=False, error=summary)
    pending = _Page(page.create_settings, page.log)
    try:
        rows = _serialize_list(plugin_id, items, "", pending, 0)
    except Exception as e:
        return _reply(ok=False, error="%s: %s" % (type(e).__name__, e))
    with _lock:
        # Callbacks of the rows now on the screen; old ones are dropped
        page.change_callbacks = pending.change_callbacks
        page.click_callbacks = pending.click_callbacks
        page.long_click_callbacks = pending.long_click_callbacks
    return _reply(ok=True, rows=rows)


def _takes_argument(fn):
    try:
        signature = inspect.signature(fn)
    except (TypeError, ValueError):
        return True
    return any(p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD, p.VAR_POSITIONAL)
               for p in signature.parameters.values())


def _call(fn, argument):
    if _takes_argument(fn):
        fn(argument)
    else:
        fn()


def _payload(raw):
    data = json.loads(raw)
    if not isinstance(data, dict) or not isinstance(data.get("plugin"), str):
        raise ValueError("bad request")
    return data


def setting_changed_json(raw):
    """{"plugin", "key", "value"}: keeps the value, then calls on_change(value)."""
    try:
        data = _payload(raw)
        plugin_id = data["plugin"]
        key = data.get("key")
        if not isinstance(key, str) or not key:
            raise ValueError("no key")
        value = data.get("value")
        set_setting(plugin_id, key, value)
        with _lock:
            page = _pages.get(plugin_id)
            callback = page.change_callbacks.get(key) if page is not None else None
        if callback is not None:
            try:
                with permissions.plugin_context(plugin_id):
                    _call(callback, value)
            except Exception as e:
                # The value is kept even when the plugin's callback fails
                _log(page, "on_change of %s failed: %s: %s" % (key, type(e).__name__, e))
                return _reply(ok=False, error="%s: %s" % (type(e).__name__, e))
        return _reply(ok=True)
    except Exception as e:
        return _reply(ok=False, error="%s: %s" % (type(e).__name__, e))


def setting_click_json(raw):
    """{"plugin", "row", "long"}: calls on_click or on_long_click of the row.

    The callback gets None where exteraless passes the row's view: views do
    not cross into Python yet.
    """
    try:
        data = _payload(raw)
        plugin_id = data["plugin"]
        row_id = data.get("row")
        with _lock:
            page = _pages.get(plugin_id)
            if page is None:
                return _reply(ok=True, handled=False)
            callbacks = page.long_click_callbacks if data.get("long") else page.click_callbacks
            callback = callbacks.get(row_id)
        if callback is None:
            return _reply(ok=True, handled=False)
        try:
            with permissions.plugin_context(plugin_id):
                _call(callback, None)
        except Exception as e:
            _log(page, "click callback failed: %s: %s" % (type(e).__name__, e))
            return _reply(ok=False, handled=True, error="%s: %s" % (type(e).__name__, e))
        return _reply(ok=True, handled=True)
    except Exception as e:
        return _reply(ok=False, error="%s: %s" % (type(e).__name__, e))
