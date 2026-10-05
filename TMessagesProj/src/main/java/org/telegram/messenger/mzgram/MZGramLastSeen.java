/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * An approximate "last seen" for people who hide it. The server then only
 * says "recently", "within a week" or "within a month"; this device notes
 * when it last saw a sign of the person being online: a new message from
 * them, typing, reading the owner's messages, or a status change the server
 * sends. The status line shows that time, marked as approximate, instead of
 * the vague one, while it falls inside what the server says.
 *
 * Kept in the archive database, so it stays after a restart.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.ConcurrentHashMap;

public class MZGramLastSeen {

    private static final int DAY = 24 * 60 * 60;

    private static final ConcurrentHashMap<Long, Integer> seen = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    // A sign that the user was online at "date".
    public static void record(long userId, int date) {
        if (userId <= 0 || date <= 0) {
            return;
        }
        ensureLoaded();
        Integer known = seen.get(userId);
        if (known != null && known >= date) {
            return;
        }
        seen.put(userId, date);
        Utilities.globalQueue.postRunnable(() -> MZGramHistoryDatabase.getInstance().putLastSeen(userId, date));
    }

    public static int lastSeen(long userId) {
        ensureLoaded();
        Integer at = seen.get(userId);
        return at == null ? 0 : at;
    }

    // The status line for a user who hides their last seen: the noted time,
    // marked as approximate, when it fits what the server says (expires is
    // the app's code for "recently", "within a week", "within a month").
    // Null otherwise, and the usual text is shown.
    public static String approximateStatus(TLRPC.User user, int now) {
        if (user == null || user.bot || user.self || user.status == null) {
            return null;
        }
        final int maxAge;
        switch (user.status.expires) {
            case -100:
            case -1000:
                maxAge = 3 * DAY;
                break;
            case -101:
            case -1001:
                maxAge = 7 * DAY;
                break;
            case -102:
            case -1002:
                maxAge = 30 * DAY;
                break;
            default:
                return null;
        }
        int at = lastSeen(user.id);
        if (at <= 0 || at > now + 60 || now - at > maxAge) {
            return null;
        }
        return LocaleController.formatString(R.string.MZGramLastSeenApprox, LocaleController.formatDateOnline(Math.min(at, now), null));
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (MZGramLastSeen.class) {
            if (!loaded) {
                seen.putAll(MZGramHistoryDatabase.getInstance().getAllLastSeen());
                loaded = true;
            }
        }
    }

    // After the archive is cleared.
    public static void forget() {
        seen.clear();
    }
}
