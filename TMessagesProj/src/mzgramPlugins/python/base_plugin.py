# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# What a plugin imports first:
#
#   from base_plugin import BasePlugin, MenuItemData, MenuItemType, AppEvent
#
#   class MyPlugin(BasePlugin):
#       def on_plugin_load(self):
#           self.add_menu_item(MenuItemData(MenuItemType.CHAT_ACTION_MENU, "Hello", self.hello))
#
#       def on_plugin_unload(self):
#           self.log("bye")
#
# The life of a plugin (mzgram_plugins.loader): the file is imported, its one
# BasePlugin subclass is made, on_plugin_load() runs when the plugin is
# turned on or MZGram starts, on_app_event() gets START, PAUSE, RESUME and
# STOP while it runs, and on_plugin_unload() runs when it is turned off,
# updated or deleted. Menu items and the settings page go away with it.
#
# Names and signatures follow the exteraless plugin SDK, base_plugin.py
# (GPL-3.0), so plugins written for it fit. Hooks of requests, of sent
# messages and of Java methods are accepted but do nothing yet: they come
# with plugins.runtime.xposed-hooks; a plugin that adds one gets a line in
# its log.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

from typing import Any, Optional

from mzgram_plugins import loader as _loader
from mzgram_plugins import menus as _menus
from mzgram_plugins import settings as _settings
from mzgram_plugins.menus import MenuItemData, MenuItemType

__all__ = [
    "BasePlugin", "AppEvent", "MenuItemData", "MenuItemType", "HookStrategy", "HookResult",
    "MethodHook", "MethodReplacement",
]


class AppEvent:
    """Events of the app given to on_app_event(); the same names as in exteraless."""
    START = "START"
    STOP = "STOP"
    PAUSE = "PAUSE"
    RESUME = "RESUME"


class HookStrategy:
    """What a request hook returns (used with plugins.runtime.xposed-hooks)."""
    DEFAULT = "DEFAULT"
    CANCEL = "CANCEL"
    MODIFY = "MODIFY"
    MODIFY_FINAL = "MODIFY_FINAL"


class HookResult:
    def __init__(self, strategy: str = HookStrategy.DEFAULT, request=None, response=None,
                 update=None, updates=None, params=None, result=None):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params
        self.result = result

    def __repr__(self):
        return "HookResult(strategy=%r)" % (self.strategy,)


class MethodHook:
    """A hook of a Java method (used with plugins.runtime.xposed-hooks)."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement:
    """A replacement of a Java method (used with plugins.runtime.xposed-hooks)."""

    def replace_hooked_method(self, param):
        return None


_HOOKS_LATER = "hooks are not available in this version of MZGram yet: "


class BasePlugin:
    """Every plugin file defines exactly one subclass of this class."""

    #: The plugin id (__id__); plugins written for exteraless read self.id
    id: Optional[str] = None
    enabled: bool = True
    name: Optional[str] = None
    description: Optional[str] = None
    author: Optional[str] = None
    version: Optional[str] = None
    icon: Optional[str] = None
    app_version: Optional[str] = None
    sdk_version: Optional[str] = None
    requirements: Optional[list] = None

    MenuType = MenuItemType

    # ---------- life (override in the subclass) ----------

    def on_plugin_load(self):
        """The plugin starts: it was turned on, or MZGram started."""

    def on_plugin_unload(self):
        """The plugin stops: it was turned off, updated or deleted."""

    def on_app_event(self, event_type: str):
        """An AppEvent while the plugin runs."""

    def create_settings(self) -> Optional[list]:
        """The rows of the settings page (ui.settings), or None."""
        return None

    # ---------- request and update hooks (plugins.runtime.xposed-hooks) ----------

    def pre_request_hook(self, request_name, account, request):
        return None

    def post_request_hook(self, request_name, account, response, error):
        return None

    def on_send_message_hook(self, account, params):
        return None

    def on_update_hook(self, update_name, account, update):
        return None

    def on_updates_hook(self, container_name, account, updates):
        return None

    # ---------- set by the loader ----------

    def _mzgram_attach(self, plugin_id: str, meta: dict):
        self.__dict__["_mzgram_plugin_id"] = plugin_id
        self.id = plugin_id
        for key in ("name", "description", "author", "version", "icon",
                    "app_version", "sdk_version", "requirements"):
            try:
                setattr(self, key, meta.get(key))
            except Exception:
                continue

    @property
    def plugin_id(self) -> Optional[str]:
        return self.__dict__.get("_mzgram_plugin_id")

    # ---------- log ----------

    def log(self, msg):
        """A line in the activity log of the plugin (MZGram → Plugins → Activity log)."""
        _loader.log(self.plugin_id or "?", msg)

    # ---------- settings values ----------

    def get_setting(self, key: str, default=None):
        if not self.plugin_id:
            return default
        return _settings.get_setting(self.plugin_id, key, default)

    def set_setting(self, key: str, value, reload_settings: bool = False):
        # reload_settings: the open settings page reads the values again when
        # it comes back to the screen, so nothing more is needed
        if self.plugin_id:
            _settings.set_setting(self.plugin_id, key, value)

    def export_settings(self) -> dict:
        if not self.plugin_id:
            return {}
        return _settings.get_settings(self.plugin_id)

    def import_settings(self, values: dict, reload_settings: bool = True):
        if not self.plugin_id or not isinstance(values, dict):
            return
        for key, value in values.items():
            _settings.set_setting(self.plugin_id, str(key), value)

    # ---------- menus ----------

    def add_menu_item(self, menu_item_data: Any, text=None, *, on_click=None, condition=None,
                      priority=0, icon=None, subtext=None, item_id=None) -> Optional[str]:
        """Adds a menu item; its item_id, or None when it was refused."""
        if isinstance(menu_item_data, str) and text is not None:
            menu_item_data = MenuItemData(menu_item_data, text, on_click, item_id, icon, subtext,
                                          condition, priority)
        if not self.plugin_id:
            return None
        return _menus.add_menu_item(self.plugin_id, menu_item_data, self.log)

    def remove_menu_item(self, item_id: str):
        if self.plugin_id:
            _menus.remove_menu_item(self.plugin_id, item_id)

    # ---------- hooks: accepted, not working yet ----------

    def add_hook(self, name: str, match_substring: bool = False, priority: int = 0):
        self.log(_HOOKS_LATER + "add_hook(%r)" % (name,))

    def remove_hook(self, name: str):
        pass

    def add_on_send_message_hook(self, priority: int = 0):
        self.log(_HOOKS_LATER + "add_on_send_message_hook()")

    def hook_method(self, method, handler=None, priority: int = 50, filters=None,
                    before=None, after=None, before_filters=None, after_filters=None):
        self.log(_HOOKS_LATER + "hook_method()")
        return None

    def hook_all_methods(self, clazz, method_name, handler=None, priority: int = 50,
                         filters=None, before=None, after=None,
                         before_filters=None, after_filters=None) -> list:
        self.log(_HOOKS_LATER + "hook_all_methods(%r)" % (method_name,))
        return []

    def hook_all_constructors(self, clazz, handler=None, priority: int = 50,
                              filters=None, before=None, after=None,
                              before_filters=None, after_filters=None) -> list:
        self.log(_HOOKS_LATER + "hook_all_constructors()")
        return []

    def unhook_method(self, unhook_obj):
        pass
