/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Deleted chats (Settings > MZGram > Archive > "Deleted chats", off by
 * default): a chat that leaves the chat list (deleted by the other side or
 * by the owner, the history emptied by the server) while the archive keeps
 * deleted messages of it is written down in deleted_dialogs, with its name
 * at that time. The Deleted chats screen lists them and opens the chat (the
 * kept messages show there) or, when Telegram no longer knows the chat, the
 * list of its kept messages.
 *
 * When deletedDialogsInMainList is enabled, deleted dialogs are also restored
 * back into the main chat list with their last kept message.
 *
 * Telegram's own deletion runs as usual (its tables and transactions are
 * never held back); MZGram only remembers the chat, in its own database.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

public final class MZGramDeletedDialogs {

    private static final boolean[] restored = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private MZGramDeletedDialogs() {
    }

    // The chat's name as Telegram knows it now, or null.
    public static String titleOf(int accountId, long dialogId) {
        MessagesController controller = MessagesController.getInstance(accountId);
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encrypted = controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            TLRPC.User user = encrypted != null ? controller.getUser(encrypted.user_id) : null;
            return user != null ? UserObject.getUserName(user) : null;
        }
        if (dialogId > 0) {
            TLRPC.User user = controller.getUser(dialogId);
            return user != null ? UserObject.getUserName(user) : null;
        }
        TLRPC.Chat chat = controller.getChat(-dialogId);
        return chat != null && !TextUtils.isEmpty(chat.title) ? chat.title : null;
    }

    // MessagesController: the chat left the chat list, or the server has no
    // message left in it. Called on the UI thread; the archive is read and
    // written in the background.
    public static void onDialogGone(int accountId, long dialogId) {
        if (!MZGramConfig.deletedDialogsList || dialogId == 0 || DialogObject.isFolderDialogId(dialogId)) {
            return;
        }
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        if (dialogId == accountUserId) {
            return;
        }
        final String title = titleOf(accountId, dialogId);
        final int now = (int) (System.currentTimeMillis() / 1000L);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
                if (db.hasDeleted(accountUserId, dialogId)) {
                    db.putDeletedDialog(accountUserId, dialogId, title, now);
                    if (MZGramConfig.deletedDialogsInMainList) {
                        TLRPC.Message message = MZGramHistoryController.getInstance().keptLastMessage(accountId, dialogId);
                        if (message != null) {
                            MZGramHistoryDatabase.DeletedDialog d = new MZGramHistoryDatabase.DeletedDialog(dialogId, title, now);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (apply(accountId, d, message)) {
                                    MessagesController.getInstance(accountId).sortDialogs(null);
                                    NotificationCenter.getInstance(accountId).postNotificationName(NotificationCenter.dialogsNeedReload);
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("MZGramDeletedDialogs.onDialogGone", e);
            }
        });
    }

    public static void restore(int account) {
        if (account < 0 || account >= restored.length || restored[account]) {
            return;
        }
        restored[account] = true;
        if (!MZGramConfig.deletedDialogsList || !MZGramConfig.deletedDialogsInMainList) {
            return;
        }
        final long accountUserId = UserConfig.getInstance(account).getClientUserId();
        if (accountUserId == 0) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                List<MZGramHistoryDatabase.DeletedDialog> all = MZGramHistoryDatabase.getInstance().getDeletedDialogs(accountUserId);
                if (all == null || all.isEmpty()) {
                    return;
                }
                final ArrayList<MZGramHistoryDatabase.DeletedDialog> found = new ArrayList<>();
                final ArrayList<TLRPC.Message> messages = new ArrayList<>();
                final ArrayList<Long> userIds = new ArrayList<>();
                final ArrayList<Long> chatIds = new ArrayList<>();
                for (int i = 0, N = all.size(); i < N; i++) {
                    MZGramHistoryDatabase.DeletedDialog d = all.get(i);
                    TLRPC.Message message = MZGramHistoryController.getInstance().keptLastMessage(account, d.dialogId);
                    if (message == null) {
                        continue;
                    }
                    found.add(d);
                    messages.add(message);
                    if (DialogObject.isUserDialog(d.dialogId)) {
                        userIds.add(d.dialogId);
                    } else {
                        chatIds.add(-d.dialogId);
                    }
                }
                if (found.isEmpty()) {
                    return;
                }
                MessagesStorage storage = MessagesStorage.getInstance(account);
                storage.getStorageQueue().postRunnable(() -> {
                    final ArrayList<TLRPC.User> users = !userIds.isEmpty() ? storage.getUsers(userIds) : new ArrayList<>();
                    final ArrayList<TLRPC.Chat> chats = !chatIds.isEmpty() ? storage.getChats(chatIds) : new ArrayList<>();
                    AndroidUtilities.runOnUIThread(() -> {
                        MessagesController controller = MessagesController.getInstance(account);
                        if (!users.isEmpty()) {
                            controller.putUsers(users, true);
                        }
                        if (!chats.isEmpty()) {
                            controller.putChats(chats, true);
                        }
                        boolean changed = false;
                        for (int i = 0; i < found.size(); i++) {
                            MZGramHistoryDatabase.DeletedDialog d = found.get(i);
                            if (DialogObject.isUserDialog(d.dialogId)) {
                                if (controller.getUser(d.dialogId) == null) {
                                    TLRPC.TL_user u = new TLRPC.TL_user();
                                    u.id = d.dialogId;
                                    u.first_name = d.title != null ? d.title : "";
                                    controller.putUser(u, true);
                                }
                            } else {
                                if (controller.getChat(-d.dialogId) == null) {
                                    TLRPC.TL_chat c = new TLRPC.TL_chat();
                                    c.id = -d.dialogId;
                                    c.title = d.title != null ? d.title : "";
                                    controller.putChat(c, true);
                                }
                            }
                            if (apply(account, d, messages.get(i))) {
                                changed = true;
                            }
                        }
                        if (changed) {
                            controller.sortDialogs(null);
                            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    });
                });
            } catch (Exception e) {
                FileLog.e("MZGramDeletedDialogs.restore", e);
            }
        });
    }

    private static boolean apply(int account, MZGramHistoryDatabase.DeletedDialog deletedDialog, TLRPC.Message message) {
        MessagesController controller = MessagesController.getInstance(account);
        long dialogId = deletedDialog.dialogId;
        TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
        ArrayList<MessageObject> current = controller.dialogMessage.get(dialogId);
        if (dialog != null && current != null && !current.isEmpty() && current.get(0) != null && current.get(0).messageOwner != null && !current.get(0).messageOwner.mzgramDeleted) {
            return false;
        }
        if (dialog == null) {
            dialog = new TLRPC.TL_dialog();
            dialog.id = dialogId;
            if (DialogObject.isUserDialog(dialogId)) {
                dialog.peer = new TLRPC.TL_peerUser();
                dialog.peer.user_id = dialogId;
            } else if (DialogObject.isChatDialog(dialogId)) {
                dialog.peer = new TLRPC.TL_peerChat();
                dialog.peer.chat_id = -dialogId;
            } else {
                dialog.peer = new TLRPC.TL_peerChannel();
                dialog.peer.channel_id = -dialogId;
            }
            dialog.folder_id = 0;
            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            controller.dialogs_dict.put(dialogId, dialog);
            controller.getAllDialogs().add(dialog);
        }
        dialog.top_message = message.id;
        dialog.last_message_date = message.date;
        dialog.unread_count = 0;
        dialog.unread_mark = false;
        MessageObject messageObject = new MessageObject(account, message, false, true);
        messageObject.deleted = true;
        messageObject.setIsRead();
        messageObject.setContentIsRead();
        ArrayList<MessageObject> list = new ArrayList<>(1);
        list.add(messageObject);
        controller.dialogMessage.put(dialogId, list);
        return true;
    }

    public static void removeRestored(int account) {
        if (account >= 0 && account < restored.length) {
            restored[account] = false;
        }
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController controller = MessagesController.getInstance(account);
            boolean changed = false;
            ArrayList<TLRPC.Dialog> all = controller.getAllDialogs();
            for (int i = all.size() - 1; i >= 0; i--) {
                TLRPC.Dialog d = all.get(i);
                if (d == null) continue;
                ArrayList<MessageObject> msgs = controller.dialogMessage.get(d.id);
                if (msgs != null && !msgs.isEmpty() && msgs.get(0) != null && msgs.get(0).messageOwner != null && msgs.get(0).messageOwner.mzgramDeleted) {
                    all.remove(i);
                    controller.dialogs_dict.remove(d.id);
                    controller.dialogMessage.remove(d.id);
                    changed = true;
                }
            }
            if (changed) {
                controller.sortDialogs(null);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        });
    }

    // The Deleted chats screen, without the chats back in the chat list
    // (shown = the dialog ids the chat list has now). Not on the UI thread.
    public static List<MZGramHistoryDatabase.DeletedDialog> list(int accountId, java.util.Set<Long> shown) {
        List<MZGramHistoryDatabase.DeletedDialog> result = new ArrayList<>();
        try {
            long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
            List<MZGramHistoryDatabase.DeletedDialog> all = MZGramHistoryDatabase.getInstance().getDeletedDialogs(accountUserId);
            for (int a = 0, N = all.size(); a < N; a++) {
                MZGramHistoryDatabase.DeletedDialog dialog = all.get(a);
                if (shown == null || !shown.contains(dialog.dialogId)) {
                    result.add(dialog);
                }
            }
        } catch (Exception e) {
            FileLog.e("MZGramDeletedDialogs.list", e);
        }
        return result;
    }

    // Takes a chat off the Deleted chats list; its kept messages stay in the
    // archive.
    public static void forget(int accountId, long dialogId) {
        try {
            long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
            MZGramHistoryDatabase.getInstance().removeDeletedDialog(accountUserId, dialogId);
            if (MZGramConfig.deletedDialogsInMainList) {
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController controller = MessagesController.getInstance(accountId);
                    TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
                    ArrayList<MessageObject> msgs = controller.dialogMessage.get(dialogId);
                    if (dialog != null && msgs != null && !msgs.isEmpty() && msgs.get(0) != null && msgs.get(0).messageOwner != null && msgs.get(0).messageOwner.mzgramDeleted) {
                        controller.dialogs_dict.remove(dialogId);
                        controller.dialogMessage.remove(dialogId);
                        controller.getAllDialogs().remove(dialog);
                        controller.sortDialogs(null);
                        NotificationCenter.getInstance(accountId).postNotificationName(NotificationCenter.dialogsNeedReload);
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e("MZGramDeletedDialogs.forget", e);
        }
    }
}
