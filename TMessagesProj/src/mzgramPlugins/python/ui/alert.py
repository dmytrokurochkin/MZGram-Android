# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Alert dialog helper and fluent AlertDialogBuilder for plugins.
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

__all__ = ["show_alert", "AlertDialogBuilder"]


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def _get_activity_context():
    try:
        LaunchActivity = _jclass("org.telegram.ui.LaunchActivity")
        fragment = LaunchActivity.getLastFragment()
        if fragment is not None and fragment.getParentActivity() is not None:
            return fragment.getParentActivity()
        return LaunchActivity.instance
    except Exception:
        return None


def show_alert(title: str, message: str, positive_btn: str = "OK", on_click=None):
    """Display an alert dialog with title, message and positive button."""
    builder = AlertDialogBuilder()
    if title:
        builder.set_title(title)
    if message:
        builder.set_message(message)
    builder.set_positive_button(positive_btn, on_click)
    builder.show()


class AlertDialogBuilder:
    """Fluent wrapper around Telegram's AlertDialog.Builder."""

    def __init__(self, context=None):
        self._context = context
        self._title = None
        self._message = None
        self._positive_text = None
        self._positive_action = None
        self._negative_text = None
        self._negative_action = None
        self._neutral_text = None
        self._neutral_action = None

    def set_title(self, title: str):
        self._title = str(title) if title is not None else None
        return self

    def set_message(self, message: str):
        self._message = str(message) if message is not None else None
        return self

    def set_positive_button(self, text: str, on_click=None):
        self._positive_text = str(text) if text is not None else "OK"
        self._positive_action = on_click
        return self

    def set_negative_button(self, text: str, on_click=None):
        self._negative_text = str(text) if text is not None else "Cancel"
        self._negative_action = on_click
        return self

    def set_neutral_button(self, text: str, on_click=None):
        self._neutral_text = str(text) if text is not None else None
        self._neutral_action = on_click
        return self

    def show(self):
        def _build_and_show():
            try:
                ctx = self._context or _get_activity_context()
                if ctx is None:
                    log("AlertDialogBuilder: no activity context available")
                    return
                Builder = _jclass("org.telegram.ui.ActionBar.AlertDialog$Builder")
                builder = Builder(ctx)
                if self._title:
                    builder.setTitle(self._title)
                if self._message:
                    builder.setMessage(self._message)

                if self._positive_text:
                    pos_act = self._positive_action
                    builder.setPositiveButton(self._positive_text, lambda d, w: pos_act() if callable(pos_act) else None)

                if self._negative_text:
                    neg_act = self._negative_action
                    builder.setNegativeButton(self._negative_text, lambda d, w: neg_act() if callable(neg_act) else None)

                if self._neutral_text:
                    neu_act = self._neutral_action
                    builder.setNeutralButton(self._neutral_text, lambda d, w: neu_act() if callable(neu_act) else None)

                dialog = builder.create()
                dialog.show()
            except Exception as e:
                log("AlertDialogBuilder.show failed: %s: %s" % (type(e).__name__, e))

        run_on_ui_thread(_build_and_show)
