# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# The permission model of plugins, Python side.
#
#   plugin_context(plugin_id)   marks the plugin whose code runs on this
#                               thread, in Python and on the Java side
#                               (MZGramPluginPermissions.enter/exit), around
#                               every call into plugin code: importing the
#                               file, on_plugin_load/unload, app events, menu
#                               and settings callbacks; hooks are marked by
#                               MZGramPluginMethodHook in Java;
#   current_plugin_id()         that plugin, or None;
#   has(key, plugin_id=None)    quiet check;
#   check(key, what, ...)       the check at a call site: a refusal is
#                               written to the activity log and told to the
#                               user once, with a button that grants it;
#   require(key, what, ...)     the same, raising PermissionError.
#
# Outside plugin code there is nothing to check: MZGram's own code is never
# asked. Without Java (tests on a computer) every check passes, so the code
# of a plugin can be tried without MZGram.
#
# Based on exteraless extera_utils/plugin_loader.py (plugin_context,
# java_runtime_mark, current_plugin_id, has_permission; GPL-3.0): the mark of
# the running plugin in Python and in Java and the quiet check; written anew
# for MZGram.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import contextlib
import threading

PERMISSIONS_CLASS = "org.telegram.messenger.mzgram.plugins.MZGramPluginPermissions"

UI = "ui"
MESSAGES_READ = "messages.read"
MESSAGES_SEND = "messages.send"
NETWORK = "network"
FILES = "files"
INTENTS = "intents"
SETTINGS = "settings"
HOOKS = "hooks"
NATIVE = "native"

# The same keys and order as MZGramPluginGrants.ALL and metadata.py
ALL = (UI, MESSAGES_READ, MESSAGES_SEND, NETWORK, FILES, INTENTS, SETTINGS, HOOKS, NATIVE)
DANGEROUS = (HOOKS, NATIVE)

_state = threading.local()

_UNSET = object()
_java_class = _UNSET


def _java():
    global _java_class
    if _java_class is _UNSET:
        try:
            from java import jclass
            _java_class = jclass(PERMISSIONS_CLASS)
        except Exception:
            _java_class = None
    return _java_class


def _set_java(facade):
    """Tests: a stand-in for the Java class, or None for no Java."""
    global _java_class
    _java_class = facade


def is_known(key):
    return key in ALL


def is_dangerous(key):
    return key in DANGEROUS


@contextlib.contextmanager
def plugin_context(plugin_id):
    """Plugin code of plugin_id runs inside; nests, and gives back the
    previous mark at the end."""
    previous = getattr(_state, "plugin_id", None)
    _state.plugin_id = plugin_id
    java = _java() if plugin_id is not None else None
    java_previous = None
    marked = False
    if java is not None:
        try:
            java_previous = java.enter(plugin_id)
            marked = True
        except Exception:
            marked = False
    try:
        yield plugin_id
    finally:
        if marked:
            try:
                java.exit(java_previous)
            except Exception:
                pass
        _state.plugin_id = previous


def current_plugin_id():
    """The plugin whose code runs on this thread, or None. The mark on the
    Java side decides (it is set by plugin_context and by hooks, and keeps
    the plugin with fewer permissions when they nest); the Python mark only
    without Java."""
    java = _java()
    if java is None:
        return getattr(_state, "plugin_id", None)
    try:
        found = java.current()
    except Exception:
        return getattr(_state, "plugin_id", None)
    return str(found) if found is not None else None


def _plugin(plugin_id):
    return plugin_id if plugin_id is not None else current_plugin_id()


def has(key, plugin_id=None):
    """Whether the plugin (by default the one running here) has the key.
    True outside plugin code and without Java; an unknown key is False."""
    if not is_known(key):
        return False
    pid = _plugin(plugin_id)
    java = _java()
    if pid is None or java is None:
        return True
    try:
        return bool(java.has(pid, key))
    except Exception:
        # A check that can't be asked refuses
        return False


def check(key, what="", plugin_id=None):
    """The check where a plugin does something; a refusal is logged and
    told to the user. what: the action, for the log (never user data)."""
    if not is_known(key):
        return False
    pid = _plugin(plugin_id)
    java = _java()
    if pid is None or java is None:
        return True
    try:
        return bool(java.check(pid, key, str(what or "")))
    except Exception:
        return False


def require(key, what="", plugin_id=None):
    """check(), raising PermissionError on a refusal."""
    if not check(key, what, plugin_id):
        raise PermissionError("MZGram: the plugin may not %s without the permission %r"
                              % (what or "do this", key))
