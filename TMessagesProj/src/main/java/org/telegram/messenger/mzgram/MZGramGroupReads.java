/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Who read the owner's message in a group. The server lists the readers
 * (messages.getMessageReadParticipants) only in small groups and only for
 * about a week; whenever the message menu gets that list, this keeps it in
 * the archive database, so message menu > Details still shows the readers
 * and their times later. Only lists the server already sent are kept:
 * nothing extra is asked.
 *
 * Idea from AyuGram (GPL-3.0), com/radolyn/ayugram/messages/AyuSpyController.java
 * (reads kept locally past the server's limit).
 */

package org.telegram.messenger.mzgram;

import android.util.Pair;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.util.List;

public final class MZGramGroupReads {

    public interface Callback {
        // {userId, readAt} pairs, earliest first; readAt 0 when unknown.
        void run(List<long[]> readers);
    }

    private MZGramGroupReads() {
    }

    // MessageSeenView: the server's list of readers for one message.
    public static void onReadParticipants(int account, long dialogId, int messageId, List<Pair<Long, Integer>> readers) {
        if (!MZGramConfig.keepGroupReads || readers == null || readers.isEmpty() || dialogId >= 0 || messageId <= 0) {
            return;
        }
        final long accountUserId = UserConfig.getInstance(account).getClientUserId();
        final long[] userIds = new long[readers.size()];
        final int[] readAt = new int[readers.size()];
        int count = 0;
        for (Pair<Long, Integer> reader : readers) {
            if (reader.first == null || reader.first <= 0) {
                continue;
            }
            userIds[count] = reader.first;
            readAt[count] = reader.second == null ? 0 : reader.second;
            count++;
        }
        if (count == 0) {
            return;
        }
        final long[] ids = java.util.Arrays.copyOf(userIds, count);
        final int[] dates = java.util.Arrays.copyOf(readAt, count);
        Utilities.globalQueue.postRunnable(() -> MZGramHistoryDatabase.getInstance().putGroupReads(accountUserId, dialogId, messageId, ids, dates));
    }

    // The kept readers, called back on the UI thread.
    public static void load(int account, long dialogId, int messageId, Callback callback) {
        final long accountUserId = UserConfig.getInstance(account).getClientUserId();
        Utilities.globalQueue.postRunnable(() -> {
            List<long[]> readers = MZGramHistoryDatabase.getInstance().getGroupReads(accountUserId, dialogId, messageId);
            AndroidUtilities.runOnUIThread(() -> callback.run(readers));
        });
    }
}
