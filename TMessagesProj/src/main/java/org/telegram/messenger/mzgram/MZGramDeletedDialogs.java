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
 * Telegram's own deletion runs as usual (its tables and transactions are
 * never held back); MZGram only remembers the chat, in its own database.
 *
 * Idea from AyuGram (GPL-3.0), com/radolyn/ayugram/messages/
 * AyuDeletedDialogs.java (as in exteraless); written anew for MZGram,
 * without putting chats back into the chat list.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

public final class MZGramDeletedDialogs {

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
                }
            } catch (Exception e) {
                FileLog.e("MZGramDeletedDialogs.onDialogGone", e);
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
    // archive. Not on the UI thread.
    public static void forget(int accountId, long dialogId) {
        try {
            long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
            MZGramHistoryDatabase.getInstance().removeDeletedDialog(accountUserId, dialogId);
        } catch (Exception e) {
            FileLog.e("MZGramDeletedDialogs.forget", e);
        }
    }
}
