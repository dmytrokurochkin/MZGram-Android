# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Helper functions for method hooking and class lookup in plugins.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

from mzgram_plugins import hooks as _hooks
from mzgram_plugins import permissions as _permissions

__all__ = ["find_class", "hook_method", "hook_all_methods", "hook_all_constructors", "unhook_method"]


def find_class(name: str):
    """The Java class with this name (a jclass), or None."""
    if not isinstance(name, str) or not name:
        return None
    try:
        from java import jclass
        return jclass(name)
    except Exception:
        return None


def _resolve_target_class(target):
    if isinstance(target, str):
        return find_class(target)
    return target


def hook_method(class_name, method_name: str, before=None, after=None, replace=None):
    """Hooks a method with before/after/replace callbacks or a MethodHook instance.

    Requires the 'hooks' permission.
    """
    _permissions.require("hooks", "hook_method")
    cls = _resolve_target_class(class_name)
    if cls is None:
        return None
    if isinstance(before, _hooks.BaseHook):
        hook_obj = before
    elif isinstance(replace, _hooks.BaseHook):
        hook_obj = replace
    elif callable(replace):
        hook_obj = _hooks.MethodReplacement(replace)
    else:
        hook_obj = _hooks.MethodHook(before=before, after=after, replace=replace)
    return _hooks.hook_method(cls, method_name, hook_obj)


def hook_all_methods(class_name, method_name: str, hook):
    """Hooks all overloads of method_name in class_name."""
    _permissions.require("hooks", "hook_all_methods")
    cls = _resolve_target_class(class_name)
    if cls is None:
        return []
    return _hooks.hook_all_methods(cls, method_name, hook)


def hook_all_constructors(class_name, hook):
    """Hooks all constructors of class_name."""
    _permissions.require("hooks", "hook_all_constructors")
    cls = _resolve_target_class(class_name)
    if cls is None:
        return []
    return _hooks.hook_all_constructors(cls, hook)


def unhook_method(unhook_id):
    """Removes a previously placed hook by id."""
    _hooks.unhook_method(unhook_id)
