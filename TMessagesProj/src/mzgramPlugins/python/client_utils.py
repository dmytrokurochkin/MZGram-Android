# This is the source code of MZGram for Android,
# a fork of Telegram for Android.
#
# Telegram client helpers: controllers, queues, requests, sending messages.
# All operations go strictly through permission checks and Sink Gates.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.

import os
import sys
import threading

_PARENT = os.path.dirname(os.path.abspath(__file__))
if _PARENT not in sys.path:
    sys.path.insert(0, _PARENT)

from android_utils import log, run_on_ui_thread, is_ui_thread
from mzgram_plugins import permissions as _permissions

__all__ = [
    "STAGE_QUEUE", "GLOBAL_QUEUE", "PLUGINS_QUEUE",
    "get_selected_account", "get_hook_account", "get_last_fragment",
    "get_current_chat_id", "get_message_by_id", "send_message", "send_text",
    "download_file", "send_request", "run_on_queue",
    "get_account_instance", "get_messages_controller", "get_connections_manager",
    "get_messages_storage", "get_send_messages_helper", "get_file_loader",
    "show_error_bulletin", "show_info_bulletin",
]

STAGE_QUEUE = "STAGE_QUEUE"
GLOBAL_QUEUE = "GLOBAL_QUEUE"
PLUGINS_QUEUE = "PLUGINS_QUEUE"

_hook_state = threading.local()


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def get_hook_account():
    """Account of the hook callback running on this thread, or None."""
    return getattr(_hook_state, "account", None)


def get_selected_account() -> int:
    """The account currently selected in the UI."""
    try:
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        return int(UserConfig.selectedAccount)
    except Exception:
        return 0


def _resolve_account(account=None) -> int:
    if account is not None:
        return int(account)
    hook_acc = get_hook_account()
    if hook_acc is not None:
        return int(hook_acc)
    return get_selected_account()


def get_account_instance(account=None):
    acc = _resolve_account(account)
    try:
        return _jclass("org.telegram.messenger.AccountInstance").getInstance(acc)
    except Exception:
        return None


def get_messages_controller(account=None):
    inst = get_account_instance(account)
    return inst.getMessagesController() if inst is not None else None


def get_connections_manager(account=None):
    inst = get_account_instance(account)
    return inst.getConnectionsManager() if inst is not None else None


def get_messages_storage(account=None):
    inst = get_account_instance(account)
    return inst.getMessagesStorage() if inst is not None else None


def get_send_messages_helper(account=None):
    inst = get_account_instance(account)
    return inst.getSendMessagesHelper() if inst is not None else None


def get_file_loader(account=None):
    inst = get_account_instance(account)
    return inst.getFileLoader() if inst is not None else None


def get_last_fragment():
    """The currently visible BaseFragment, or None."""
    try:
        launch = _jclass("org.telegram.ui.LaunchActivity")
        return launch.getLastFragment()
    except Exception:
        return None


def get_current_chat_id() -> int:
    """Gets the dialog id of the currently active ChatActivity, or 0.

    Requires the 'read' permission.
    """
    _permissions.require("read", "get_current_chat_id")
    try:
        fragment = get_last_fragment()
        if fragment is not None and hasattr(fragment, "getDialogId"):
            return int(fragment.getDialogId())
    except Exception as e:
        log("get_current_chat_id error: %s: %s" % (type(e).__name__, e))
    return 0


def get_message_by_id(msg_id: int, peer_id: int = 0, account=None):
    """Retrieve a MessageObject by its id from storage or memory.

    Requires the 'read' permission.
    """
    _permissions.require("read", "get_message_by_id")
    try:
        storage = get_messages_storage(account)
        if storage is not None:
            return storage.getMessage(int(msg_id))
    except Exception as e:
        log("get_message_by_id error: %s: %s" % (type(e).__name__, e))
    return None


def send_message(peer_id, text=None, reply_to=None, account=None):
    """Send a message to peer_id.

    Accepts either (peer_id, text, reply_to) or a single dict of params.
    Requires the 'messages.send' permission.
    """
    _permissions.require("messages.send", "send_message")
    if isinstance(peer_id, dict):
        params = peer_id
        target_peer = params.get("peer") or params.get("peer_id", 0)
        msg_text = params.get("message") or params.get("text", "")
        reply_to_msg = params.get("replyToMsg") or params.get("reply_to")
        acc = params.get("account", account)
    else:
        target_peer = peer_id
        msg_text = text or ""
        reply_to_msg = reply_to
        acc = account

    return send_text(target_peer, msg_text, replyToMsg=reply_to_msg, account=acc)


def send_text(peer_id, text: str, replyToMsg=None, parse_mode=None, account=None):
    """Send plain text message to peer_id.

    Requires the 'messages.send' permission.
    """
    _permissions.require("messages.send", "send_text")
    resolved_acc = _resolve_account(account)

    def _do_send():
        try:
            helper = get_send_messages_helper(resolved_acc)
            if helper is None:
                log("send_text: SendMessagesHelper unavailable")
                return
            SendMessageParams = _jclass("org.telegram.messenger.SendMessagesHelper$SendMessageParams")
            params = SendMessageParams.of(str(text), int(peer_id))
            if replyToMsg is not None and hasattr(replyToMsg, "getId"):
                params.replyToMsg = replyToMsg
            helper.sendMessage(params)
        except Exception as e:
            log("send_text failed: %s: %s" % (type(e).__name__, e))

    if is_ui_thread():
        _do_send()
    else:
        run_on_ui_thread(_do_send)


def download_file(file_location, account=None):
    """Start downloading a file given its location.

    Requires the 'files' permission.
    """
    _permissions.require("files", "download_file")
    resolved_acc = _resolve_account(account)
    try:
        loader = get_file_loader(resolved_acc)
        if loader is not None and file_location is not None:
            loader.loadFile(file_location, None, 0, 0)
    except Exception as e:
        log("download_file failed: %s: %s" % (type(e).__name__, e))


def send_request(request, callback, account=None) -> int:
    """Send an RPC request via ConnectionsManager.

    Requires the 'network' permission.
    """
    _permissions.require("network", "send_request")
    resolved_acc = _resolve_account(account)
    try:
        cm = get_connections_manager(resolved_acc)
        if cm is None:
            return 0
        from java import dynamic_proxy
        RequestDelegate = _jclass("org.telegram.tgnet.RequestDelegate")

        class ProxyDelegate(dynamic_proxy(RequestDelegate)):
            def __init__(self, target):
                super().__init__()
                self.target = target

            def run(self, response, error):
                try:
                    if callable(self.target):
                        self.target(response, error)
                except Exception as e:
                    log("send_request callback error: %s: %s" % (type(e).__name__, e))

        return int(cm.sendRequest(request, ProxyDelegate(callback)))
    except Exception as e:
        log("send_request failed: %s: %s" % (type(e).__name__, e))
        return 0


def run_on_queue(fn, queue: str = PLUGINS_QUEUE, delay: int = 0, delay_ms: int = None):
    """Run a callable on a background queue or thread."""
    if not callable(fn):
        return
    ms = delay_ms if delay_ms is not None else delay * 1000
    if ms <= 0:
        threading.Thread(target=fn, daemon=True).start()
    else:
        threading.Timer(ms / 1000.0, fn).start()


def show_error_bulletin(message: str, fragment=None):
    from ui.bulletin import show_error_bulletin as _show
    _show(message)


def show_info_bulletin(message: str, fragment=None):
    from ui.bulletin import show_bulletin as _show
    _show(message)
