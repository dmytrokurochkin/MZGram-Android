# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Menu items of plugins, Python side. A plugin adds an item with
# add_menu_item(plugin_id, MenuItemData(...)); the item's text, icon and
# place go to MZGramPluginMenus on the Java side, which shows it in its menu,
# and the callback stays here. A tap comes back through menu_click_json()
# with the context of the tap as a dict (account, dialog_id, message_id and
# so on), never Java objects.
#
# Places: MESSAGE_CONTEXT_MENU (menu of a message), CHAT_ACTION_MENU (the
# three dots of a chat), PROFILE_ACTION_MENU (the three dots of a profile),
# MAIN_MENU and DRAWER_MENU (both the menu of the chat list; MZGram has no
# side drawer). When a place has more than two items of plugins, they are
# folded into one "Plugins" item that opens their list.
#
# Everything Java calls takes and returns one JSON string and never raises.
#
# Based on exteraless base_plugin.py (MenuItemType, MenuItemData,
# add_menu_item, remove_menu_item) and plugins/MenuItemRecord.java
# (GPL-3.0): the same names and fields, so plugins written for it fit.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import hashlib
import inspect
import json
import re
import threading
from dataclasses import dataclass
from typing import Any, Callable, Dict, Optional

MENUS_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginMenus"

# Longest text of an item; longer is cut
MAX_TEXT = 64
# Items of one plugin; more are refused
MAX_ITEMS_PER_PLUGIN = 16

_ITEM_ID = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")


class MenuItemType:
    """Places of menu items; the same names as in exteraless."""
    MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU"
    DRAWER_MENU = "DRAWER_MENU"
    MAIN_MENU = "MAIN_MENU"
    CHAT_ACTION_MENU = "CHAT_ACTION_MENU"
    PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU"
    CHAT_CONTEXT = MESSAGE_CONTEXT_MENU


PLACES = (
    MenuItemType.MESSAGE_CONTEXT_MENU,
    MenuItemType.DRAWER_MENU,
    MenuItemType.MAIN_MENU,
    MenuItemType.CHAT_ACTION_MENU,
    MenuItemType.PROFILE_ACTION_MENU,
)


@dataclass
class MenuItemData:
    """A menu item of a plugin.

    condition is accepted for plugins written for exteraless, where it is an
    MVEL expression; MZGram does not evaluate it and shows the item, so
    on_click should check the context itself.
    """
    menu_type: str
    text: str
    on_click: Optional[Callable[[Dict[str, Any]], None]] = None
    item_id: Optional[str] = None
    icon: Optional[str] = None
    subtext: Optional[str] = None
    condition: Optional[str] = None
    priority: int = 0


class _MemoryMenus:
    """Stands in for MZGramPluginMenus without Java (tests on a computer)."""

    def __init__(self):
        self.items = {}

    def add(self, plugin_id, raw):
        item = json.loads(raw)
        self.items[(plugin_id, item["item_id"])] = item
        return item["item_id"]

    def remove(self, plugin_id, item_id):
        self.items.pop((plugin_id, item_id), None)

    def removeAll(self, plugin_id):
        for key in [key for key in self.items if key[0] == plugin_id]:
            del self.items[key]


_lock = threading.RLock()
_menus = None
# (plugin_id, item_id) -> on_click
_callbacks = {}


def _backend():
    global _menus
    if _menus is None:
        try:
            from java import jclass
            _menus = jclass(MENUS_CLASS)
        except Exception:
            _menus = _MemoryMenus()
    return _menus


def use_memory_menus():
    """Keep items in memory only (tests); returns the stand-in."""
    global _menus
    _menus = _MemoryMenus()
    with _lock:
        _callbacks.clear()
    return _menus


def _default_id(plugin_id, place, text):
    digest = hashlib.sha256(json.dumps([plugin_id, place, text], ensure_ascii=False).encode("utf-8"))
    return "item_" + digest.hexdigest()[:16]


def add_menu_item(plugin_id, data, log=None):
    """Adds the item; returns its item_id, or None when it was refused."""
    try:
        if not isinstance(data, MenuItemData):
            raise TypeError("add_menu_item() expects a MenuItemData")
        place = str(data.menu_type)
        if place not in PLACES:
            raise ValueError("unknown menu_type %r" % (data.menu_type,))
        text = data.text if isinstance(data.text, str) else str(data.text)
        text = " ".join(text.split())[:MAX_TEXT]
        if not text:
            raise ValueError("the text of a menu item is empty")
        item_id = data.item_id if data.item_id is not None else _default_id(plugin_id, place, text)
        if not isinstance(item_id, str) or not _ITEM_ID.match(item_id):
            raise ValueError("item_id must be 1 to 64 Latin letters, digits, '_', '.' or '-'")
        with _lock:
            own = [key for key in _callbacks if key[0] == plugin_id]
            if (plugin_id, item_id) not in _callbacks and len(own) >= MAX_ITEMS_PER_PLUGIN:
                raise ValueError("a plugin can add at most %d menu items" % MAX_ITEMS_PER_PLUGIN)
            payload = {"item_id": item_id, "menu_type": place, "text": text, "priority": int(data.priority or 0)}
            if isinstance(data.icon, str) and data.icon:
                payload["icon"] = data.icon
            if isinstance(data.subtext, str) and data.subtext:
                payload["subtext"] = " ".join(data.subtext.split())[:MAX_TEXT]
            stored = _backend().add(plugin_id, json.dumps(payload, ensure_ascii=False))
            if stored is None:
                raise ValueError("the menu item was refused")
            _callbacks[(plugin_id, item_id)] = data.on_click
        return item_id
    except Exception as e:
        if log is not None:
            try:
                log("add_menu_item failed: %s: %s" % (type(e).__name__, e))
            except Exception:
                pass
        return None


def remove_menu_item(plugin_id, item_id):
    with _lock:
        _callbacks.pop((plugin_id, item_id), None)
        _backend().remove(plugin_id, item_id)


def remove_all(plugin_id):
    """Removes every item of the plugin (when it is turned off)."""
    with _lock:
        for key in [key for key in _callbacks if key[0] == plugin_id]:
            del _callbacks[key]
        _backend().removeAll(plugin_id)


def _reply(**fields):
    return json.dumps(fields, ensure_ascii=False)


def _takes_argument(fn):
    try:
        signature = inspect.signature(fn)
    except (TypeError, ValueError):
        return True
    return any(p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD, p.VAR_POSITIONAL)
               for p in signature.parameters.values())


def menu_click_json(raw):
    """{"plugin", "item", "context"}: calls on_click(context).

    {"ok": true, "handled": bool} or {"ok": false, "error": "..."}.
    """
    try:
        data = json.loads(raw)
        if not isinstance(data, dict) or not isinstance(data.get("plugin"), str) \
                or not isinstance(data.get("item"), str):
            raise ValueError("bad request")
        context = data.get("context")
        if not isinstance(context, dict):
            context = {}
        with _lock:
            callback = _callbacks.get((data["plugin"], data["item"]))
        if callback is None:
            return _reply(ok=True, handled=False)
        if _takes_argument(callback):
            callback(context)
        else:
            callback()
        return _reply(ok=True, handled=True)
    except Exception as e:
        return _reply(ok=False, error="%s: %s" % (type(e).__name__, e))
