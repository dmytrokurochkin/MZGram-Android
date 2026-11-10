# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# The plugin engine, Python side: the life of a plugin in memory.
#
#   load_json({"id", "path"})   reads the metadata again, imports the .plugin
#                               file as its own module, makes the one
#                               BasePlugin subclass, registers its settings
#                               page and calls on_plugin_load();
#   app_event_json({"event"})   calls on_app_event() of every running plugin;
#   unload_json({"id"})         calls on_plugin_unload() and takes back what
#                               the plugin added (hooks, menu items,
#                               settings page, its module).
#
# MZGramPluginEngine on the Java side decides when (the "Run plugins" switch
# and the switch of each plugin) and keeps the state shown on the screens.
# Everything Java calls takes and returns one JSON string and never raises;
# all of it runs on the one Python thread of MZGramPythonRuntime. Every call
# into plugin code (its module, its class, on_plugin_load/unload,
# on_app_event) runs inside permissions.plugin_context, so the permission
# checks know whose code it is.
#
# A plugin that fails in on_plugin_load() is unloaded again at once, with
# what it had added. Packages are never fetched: a plugin whose
# __requirements__ are not among the packages bundled with the build does
# not start. Before the first plugin starts, the sink gates go on
# (mzgram_plugins/sink_gate.py): the network, files, processes, native code
# and Java classes follow the permissions of the plugin. Only if they can't
# (an interpreter without audit hooks) Python sockets are closed instead.
# Each plugin has its own folder (sink_gate.data_dir), free to it.
#
# Based on exteraless extera_utils/plugin_loader.py (load_plugin,
# _import_module, _find_plugin_class, _unload_record; GPL-3.0): the order of
# the steps, the module made from a file with a .plugin extension, the
# search for the plugin class and the cleanup on unload; written anew for
# MZGram without its sandbox (that comes with the permission gates).
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import importlib.machinery
import importlib.util
import json
import os
import re
import sys
import threading
import traceback

from mzgram_plugins import hooks
from mzgram_plugins import menus
from mzgram_plugins import metadata
from mzgram_plugins import permissions
from mzgram_plugins import settings
from mzgram_plugins import sink_gate

ENGINE_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginEngine"

MODULE_PREFIX = "mzgram_plugin_"

# Longest error text handed to Java
MAX_ERROR = 600
# Longest log line of a plugin
MAX_LOG = 500

APP_EVENTS = ("START", "STOP", "PAUSE", "RESUME")

# Distribution name -> import name of the packages bundled with the build
# (mzgram_runtime.BUNDLED, TMessagesProj/build.gradle)
_BUNDLED_IMPORT_NAMES = {
    "requests": "requests",
    "beautifulsoup4": "bs4",
    "bs4": "bs4",
    "pillow": "PIL",
    "pil": "PIL",
    "pyyaml": "yaml",
    "yaml": "yaml",
    "packaging": "packaging",
}

_lock = threading.RLock()


class PluginLoadError(Exception):
    """The plugin cannot start; the message says why."""


class _Record:
    def __init__(self, plugin_id, path, module, module_name, instance):
        self.plugin_id = plugin_id
        self.path = path
        self.module = module
        self.module_name = module_name
        self.instance = instance


_plugins = {}


# ---------- Java ----------

_engine = None
_engine_resolved = False


def _java_engine():
    global _engine, _engine_resolved
    if not _engine_resolved:
        _engine_resolved = True
        try:
            from java import jclass
            _engine = jclass(ENGINE_CLASS)
        except Exception:
            _engine = None
    return _engine


def log(plugin_id, text):
    """A log line of the plugin: the activity log of MZGram, or stderr
    without Java (tests on a computer)."""
    line = " ".join(str(text).split())[:MAX_LOG]
    engine = _java_engine()
    if engine is not None:
        try:
            engine.log(plugin_id, line)
            return
        except Exception:
            pass
    print("[plugin:%s] %s" % (plugin_id, line), file=sys.stderr)


# ---------- network ----------

_network_closed = False


def _close_network():
    """Python sockets refuse to connect from now on (plugin code is the only
    Python that would; the bundled helpers of MZGram never connect)."""
    global _network_closed
    if _network_closed:
        return
    _network_closed = True
    import socket

    def refuse(*args, **kwargs):
        raise PermissionError("plugins have no network access: the sink gates of MZGram did not start")

    socket.socket.connect = refuse
    socket.socket.connect_ex = refuse
    socket.socket.sendto = refuse
    socket.create_connection = refuse
    socket.getaddrinfo = refuse
    socket.gethostbyname = refuse
    socket.gethostbyname_ex = refuse


# ---------- errors ----------

def _plugin_line(exc, path):
    """The line of the plugin file where exc happened, or None."""
    try:
        frames = traceback.extract_tb(exc.__traceback__)
    except Exception:
        return None
    line = None
    for frame in frames:
        if path and os.path.abspath(frame.filename) == os.path.abspath(path):
            line = frame.lineno
    if line is None and isinstance(exc, SyntaxError) and exc.lineno:
        line = exc.lineno
    return line


def error_text(exc, path=None):
    """'Type: message (line N)': short enough for a screen."""
    if isinstance(exc, (PluginLoadError, metadata.PluginMetadataError)):
        text = str(exc)
    else:
        message = str(exc)
        text = type(exc).__name__ + (": " + message if message else "")
    line = _plugin_line(exc, path)
    if line is not None:
        text += " (line %d)" % line
    text = " ".join(text.split())
    return text[:MAX_ERROR]


def _reply(**fields):
    return json.dumps(fields, ensure_ascii=False)


def _request(raw):
    data = json.loads(raw)
    if not isinstance(data, dict):
        raise PluginLoadError("bad request")
    return data


# ---------- loading ----------

def module_name(plugin_id):
    return MODULE_PREFIX + plugin_id


def missing_requirements(requirements):
    """The requirements that are not among the bundled packages."""
    missing = []
    for raw in requirements or ():
        name = re.split(r"[\s\[<>=!~;,]", str(raw).strip(), 1)[0]
        if not name:
            continue
        import_name = _BUNDLED_IMPORT_NAMES.get(name.lower())
        if import_name is None:
            missing.append(name)
            continue
        try:
            found = importlib.util.find_spec(import_name) is not None
        except Exception:
            found = False
        if not found:
            missing.append(name)
    return missing


def _import(path, plugin_id):
    name = module_name(plugin_id)
    sys.modules.pop(name, None)
    # A .plugin file is plain Python source, but its extension is not one a
    # loader is picked by, so the loader is named
    loader = importlib.machinery.SourceFileLoader(name, path)
    spec = importlib.util.spec_from_file_location(name, path, loader=loader)
    if spec is None or spec.loader is None:
        raise PluginLoadError("cannot make a module of the plugin file")
    module = importlib.util.module_from_spec(spec)
    # dataclasses and pickle inside the plugin look the module up here
    sys.modules[name] = module
    # Its frames are the plugin's code for the gates
    sink_gate.register(plugin_id, module)
    try:
        # The module's own code is the plugin's code
        with permissions.plugin_context(plugin_id):
            spec.loader.exec_module(module)
    except BaseException:
        sys.modules.pop(name, None)
        sink_gate.unregister(plugin_id)
        raise
    return module, name


def find_plugin_class(module):
    """The BasePlugin subclass defined in the module itself; one imported
    from elsewhere only when the module defines none."""
    from base_plugin import BasePlugin
    fallback = None
    for value in vars(module).values():
        if not isinstance(value, type) or value is BasePlugin or not issubclass(value, BasePlugin):
            continue
        if value.__module__ == module.__name__:
            return value
        if fallback is None:
            fallback = value
    if fallback is not None:
        return fallback
    raise PluginLoadError("the plugin file has no subclass of BasePlugin")


def overrides(cls, name):
    from base_plugin import BasePlugin
    method = getattr(cls, name, None)
    return method is not None and method is not getattr(BasePlugin, name, None)


def _take_back(record):
    """What the plugin added goes away with it; never raises."""
    plugin_id = record.plugin_id
    instance = record.instance
    if instance is not None:
        try:
            instance.enabled = False
        except Exception:
            pass
    # Hooks first: the app goes on without the plugin's code at once
    hooks.unhook_all(plugin_id)
    try:
        menus.remove_all(plugin_id)
    except Exception:
        pass
    try:
        settings.unregister(plugin_id)
    except Exception:
        pass
    sys.modules.pop(record.module_name, None)
    sink_gate.unregister(plugin_id)


def _unload(plugin_id, call_unload):
    """Removes the running plugin; the error of on_plugin_unload() or None."""
    with _lock:
        record = _plugins.pop(plugin_id, None)
    if record is None:
        return None
    error = None
    try:
        if call_unload and record.instance is not None and overrides(type(record.instance), "on_plugin_unload"):
            with permissions.plugin_context(plugin_id):
                record.instance.on_plugin_unload()
    except Exception as e:
        error = error_text(e, record.path)
    finally:
        _take_back(record)
    return error


def load(plugin_id, path):
    """Starts the plugin; True and whether it has a settings page. Raises
    on any problem, with the plugin taken back."""
    if not isinstance(plugin_id, str) or not isinstance(path, str):
        raise PluginLoadError("bad request")
    if not path.endswith(metadata.PLUGIN_EXTENSION) or not os.path.isfile(path):
        raise PluginLoadError("the plugin file is missing")
    meta = metadata.read_metadata(path)
    if meta["id"] != plugin_id:
        raise PluginLoadError("the file belongs to another plugin (%s)" % meta["id"])
    missing = missing_requirements(meta.get("requirements"))
    if missing:
        raise PluginLoadError("needs packages that MZGram does not have: " + ", ".join(missing))

    # A plugin that runs already starts again from its new file
    unload_error = _unload(plugin_id, call_unload=True)
    if unload_error is not None:
        log(plugin_id, "on_plugin_unload() failed: " + unload_error)

    if not sink_gate.install():
        _close_network()
    sink_gate.data_dir(plugin_id)
    module, name = _import(path, plugin_id)
    record = _Record(plugin_id, path, module, name, None)
    try:
        plugin_class = find_plugin_class(module)
        with permissions.plugin_context(plugin_id):
            instance = plugin_class()
        instance._mzgram_attach(plugin_id, meta)
        record.instance = instance
        with _lock:
            _plugins[plugin_id] = record
        has_settings = overrides(plugin_class, "create_settings")
        if has_settings:
            settings.register(plugin_id, instance.create_settings, instance.log)
        if overrides(plugin_class, "on_plugin_load"):
            with permissions.plugin_context(plugin_id):
                instance.on_plugin_load()
    except BaseException:
        with _lock:
            if _plugins.get(plugin_id) is record:
                _plugins.pop(plugin_id, None)
        _take_back(record)
        raise
    return has_settings


def load_json(raw):
    """{"id", "path"} -> {"ok": true, "has_settings": bool} or {"ok": false, "error"}."""
    path = None
    try:
        data = _request(raw)
        path = data.get("path")
        has_settings = load(data.get("id"), path)
        return _reply(ok=True, has_settings=bool(has_settings))
    except Exception as e:
        return _reply(ok=False, error=error_text(e, path))
    except BaseException as e:
        # SystemExit or KeyboardInterrupt raised by plugin code
        return _reply(ok=False, error=error_text(e, path))


def unload_json(raw):
    """{"id"} -> {"ok": true, "error": null or the error of on_plugin_unload()}."""
    try:
        data = _request(raw)
        plugin_id = data.get("id")
        if not isinstance(plugin_id, str):
            raise PluginLoadError("bad request")
        return _reply(ok=True, error=_unload(plugin_id, call_unload=True))
    except BaseException as e:
        return _reply(ok=False, error=error_text(e))


def app_event_json(raw):
    """{"event": START|STOP|PAUSE|RESUME} -> {"ok": true, "errors": [{"id", "error"}]}."""
    try:
        data = _request(raw)
        event = data.get("event")
        if event not in APP_EVENTS:
            raise PluginLoadError("unknown event %r" % (event,))
        with _lock:
            records = list(_plugins.values())
        errors = []
        for record in records:
            instance = record.instance
            if instance is None or not overrides(type(instance), "on_app_event"):
                continue
            try:
                with permissions.plugin_context(record.plugin_id):
                    instance.on_app_event(event)
            except BaseException as e:
                errors.append({"id": record.plugin_id, "error": error_text(e, record.path)})
        return _reply(ok=True, errors=errors)
    except BaseException as e:
        return _reply(ok=False, error=error_text(e))


def loaded():
    with _lock:
        return sorted(_plugins)


def loaded_json(raw=None):
    return _reply(ok=True, plugins=loaded())


def instance_of(plugin_id):
    """The running instance (tests), or None."""
    with _lock:
        record = _plugins.get(plugin_id)
    return record.instance if record is not None else None
