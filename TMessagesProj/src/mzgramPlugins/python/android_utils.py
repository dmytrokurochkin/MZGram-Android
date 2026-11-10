# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Android runtime helpers for plugins (UI thread, clipboard, log).
# Lazy resolution of Java classes so importing on a host interpreter is safe.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import sys
import threading

__all__ = [
    "run_on_ui_thread", "is_ui_thread", "log", "copy_to_clipboard",
    "get_clipboard_text", "safe_call",
]


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def is_ui_thread() -> bool:
    """Whether the calling thread is the Android main (UI) thread."""
    try:
        Looper = _jclass("android.os.Looper")
        Thread = _jclass("java.lang.Thread")
        return Looper.getMainLooper().getThread() == Thread.currentThread()
    except Exception:
        return threading.current_thread() is threading.main_thread()


class _RunnableWrapper:
    def __init__(self, fn):
        self.fn = fn

    def run(self):
        try:
            self.fn()
        except Exception as e:
            log("UI thread runnable failed: %s: %s" % (type(e).__name__, e))


def run_on_ui_thread(fn):
    """Run a callable on the Android UI thread."""
    if not callable(fn):
        return
    try:
        from java import dynamic_proxy
        Runnable = _jclass("java.lang.Runnable")

        class ProxyRunnable(dynamic_proxy(Runnable)):
            def __init__(self, target):
                super().__init__()
                self.target = target

            def run(self):
                try:
                    self.target()
                except Exception as e:
                    log("UI thread callback error: %s: %s" % (type(e).__name__, e))

        Utilities = _jclass("org.telegram.messenger.AndroidUtilities")
        Utilities.runOnUIThread(ProxyRunnable(fn))
    except Exception:
        # Fallback for desktop / host interpreter or when JVM is absent
        try:
            fn()
        except Exception as e:
            log("run_on_ui_thread fallback error: %s: %s" % (type(e).__name__, e))


def log(data):
    """Log a message to MZGram plugin engine log."""
    msg = str(data) if isinstance(data, (str, int, float, bool)) or data is None else repr(data)
    try:
        from java import jclass
        jclass("org.telegram.messenger.mzgram.plugins.MZGramPluginEngine").log("sdk", msg)
    except Exception:
        print("[mzgram:sdk] %s" % (msg,), file=sys.stderr)


def copy_to_clipboard(text: str):
    """Copy string to Android system clipboard."""
    if text is None:
        return
    s = str(text)
    try:
        _jclass("org.telegram.messenger.AndroidUtilities").addToClipboard(s)
    except Exception:
        pass


def get_clipboard_text() -> str:
    """Read plain text from system clipboard, or empty string."""
    try:
        ApplicationLoader = _jclass("org.telegram.messenger.ApplicationLoader")
        context = ApplicationLoader.applicationContext
        cm = context.getSystemService("clipboard")
        if cm is not None and cm.hasPrimaryClip():
            clip = cm.getPrimaryClip()
            if clip is not None and clip.getItemCount() > 0:
                item = clip.getItemAt(0)
                text = item.getText()
                return str(text) if text is not None else ""
    except Exception:
        pass
    return ""


def safe_call(fn, *args, **kwargs):
    """Call a function catching and logging any exception."""
    try:
        return fn(*args, **kwargs)
    except Exception as e:
        log("safe_call caught exception in %r: %s: %s" % (fn, type(e).__name__, e))
        return None
