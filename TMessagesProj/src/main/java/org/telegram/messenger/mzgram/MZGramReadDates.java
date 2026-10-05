/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * When another person read the owner's own message, for the message's info
 * (message menu > Details).
 *
 * The server tells it for private chats (messages.getOutboxReadDate) while
 * both sides' privacy allows it and the message is recent. Otherwise the
 * time this device got the read event (updateReadHistoryOutbox,
 * updateReadChannelOutbox) is used: every own message up to the event's
 * max_id was read by then. Both are kept in the archive database, so they
 * stay after the server stops telling.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class MZGramReadDates {

    // The read time and whether the server told it (true) or it is the time
    // this device learned of it (false).
    public interface Callback {
        void run(int readAt, boolean fromServer);
    }

    // MessagesController: another person read the own messages of a chat up
    // to maxId, as this device learned at "now".
    public static void onOutboxRead(int account, long dialogId, int maxId, int now) {
        if (maxId <= 0 || now <= 0) {
            return;
        }
        long accountUserId = UserConfig.getInstance(account).getClientUserId();
        Utilities.globalQueue.postRunnable(() -> MZGramHistoryDatabase.getInstance().addOutboxRead(accountUserId, dialogId, maxId, now, false));
    }

    // The time kept for a message, or {0, 0} when none is.
    public static int[] keptReadDate(int account, long dialogId, int messageId) {
        long accountUserId = UserConfig.getInstance(account).getClientUserId();
        return MZGramHistoryDatabase.getInstance().getReadAt(accountUserId, dialogId, messageId);
    }

    // The read time of an own message: from the server where it tells,
    // otherwise the kept time. Called back on the UI thread; readAt is 0
    // when the time is not known.
    public static void load(int account, MessageObject messageObject, Callback callback) {
        final long dialogId = messageObject.getDialogId();
        final int messageId = messageObject.getId();
        Utilities.globalQueue.postRunnable(() -> {
            int[] kept = keptReadDate(account, dialogId, messageId);
            if (kept[1] == 1 || !canAskServer(account, dialogId)) {
                AndroidUtilities.runOnUIThread(() -> callback.run(kept[0], kept[1] == 1));
                return;
            }
            TLRPC.TL_messages_getOutboxReadDate req = new TLRPC.TL_messages_getOutboxReadDate();
            req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
            req.msg_id = messageId;
            ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_outboxReadDate) {
                    int date = ((TLRPC.TL_outboxReadDate) res).date;
                    long accountUserId = UserConfig.getInstance(account).getClientUserId();
                    Utilities.globalQueue.postRunnable(() -> MZGramHistoryDatabase.getInstance().addOutboxRead(accountUserId, dialogId, messageId, date, true));
                    AndroidUtilities.runOnUIThread(() -> callback.run(date, true));
                } else {
                    AndroidUtilities.runOnUIThread(() -> callback.run(kept[0], false));
                }
            });
        });
    }

    private static boolean canAskServer(int account, long dialogId) {
        if (!DialogObject.isUserDialog(dialogId) || dialogId == UserConfig.getInstance(account).getClientUserId()) {
            return false;
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
        return user != null && !user.bot;
    }
}
