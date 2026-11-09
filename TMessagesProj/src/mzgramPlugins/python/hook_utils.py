# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# What a plugin needs to name the class it hooks:
#
#   from hook_utils import find_class
#
#   ChatActivity = find_class("org.telegram.ui.ChatActivity")
#   if ChatActivity is not None:
#       self.hook_all_methods(ChatActivity, "onResume", MyHook())
#
# find_class() gives None, not an error, when there is no such class, the
# way plugins written for exteraless expect (they check for None).
#
# The same name as in the exteraless plugin SDK, hook_utils.py (GPL-3.0);
# its other helpers (private fields) are not here yet.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

__all__ = ["find_class"]


def find_class(name: str):
    """The Java class with this name (a jclass), or None."""
    if not isinstance(name, str) or not name:
        return None
    try:
        from java import jclass
        return jclass(name)
    except Exception:
        return None
