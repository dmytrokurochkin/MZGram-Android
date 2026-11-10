# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# In-app bulletin notifications for plugins.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import os
import sys

_PARENT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _PARENT not in sys.path:
    sys.path.insert(0, _PARENT)

from android_utils import log, run_on_ui_thread

__all__ = ["show_bulletin", "show_error_bulletin", "show_success_bulletin"]


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def _toast(text: str):
    try:
        Toast = _jclass("android.widget.Toast")
        ApplicationLoader = _jclass("org.telegram.messenger.ApplicationLoader")
        Toast.makeText(ApplicationLoader.applicationContext, str(text), Toast.LENGTH_SHORT).show()
    except Exception:
        log("Bulletin toast fallback: %s" % (text,))


def show_bulletin(text: str, icon=None):
    """Show a simple bulletin banner at the bottom of the screen."""
    if not text:
        return

    def _show():
        try:
            LaunchActivity = _jclass("org.telegram.ui.LaunchActivity")
            fragment = LaunchActivity.getLastFragment()
            BulletinFactory = _jclass("org.telegram.ui.Components.BulletinFactory")
            factory = BulletinFactory.of(fragment) if fragment is not None else getattr(BulletinFactory, "global")()
            bulletin = factory.createSimpleBulletin(0, str(text))
            if bulletin is not None:
                bulletin.show()
                return
        except Exception as e:
            log("show_bulletin failed: %s: %s" % (type(e).__name__, e))
        _toast(text)

    run_on_ui_thread(_show)


def show_error_bulletin(text: str):
    """Show an error bulletin (with error styling or fallback)."""
    show_bulletin(text)


def show_success_bulletin(text: str):
    """Show a success bulletin."""
    show_bulletin(text)
