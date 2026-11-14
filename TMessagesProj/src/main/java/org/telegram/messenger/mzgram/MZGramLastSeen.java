/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * An approximate "last seen" for people who hide it. The server then only
 * says "recently", "within a week" or "within a month"; this device notes
 * when it last saw a sign of the person being online: a new message from
 * them, typing, reading the owner's messages, or a reaction (at the time it
 * was set). The status line shows that time, the way any last seen time is
 * shown, instead of the vague one, while it falls inside what the server says.
 *
 * Kept in the archive database, so it stays after a restart.
 *
 * Exact statuses count too (recordStatus): while the person still shows
 * "online" or "last seen at", that time is noted, so after they hide their
 * last seen the line keeps the last time this device knew.
 *
 * Only real times: a read or typing counts only live, within half a minute
 * of now, as for Telegram's own "online" (a read the app catches up on after
 * being offline happened some time before, and loading the chat list makes
 * reads of its own with no time), and a time far ahead of
 * the clock (a broken import, a wrong clock) is dropped, since it would hide
 * every later sign. Away, greeting and scheduled messages go out without the
 * person, and a "recently" status from the server carries no time (it also
 * comes when someone changes their privacy), so neither is a sign. Off
 * (Archive > Save last online), nothing is noted and no estimate is shown.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.ConcurrentHashMap;

public class MZGramLastSeen {

    private static final int DAY = 24 * 60 * 60;
    // A later sign within this many seconds of the noted one changes nothing
    // on the status line (it shows minutes), so it is not written again
    private static final int SAME_TIME = 30;

    private static final ConcurrentHashMap<Long, Integer> seen = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    // A sign that the user was online at "date".
    public static void record(long userId, int date) {
        if (userId <= 0 || date <= 0 || !MZGramConfig.saveLastOnline) { // Archive > Save last online
            return;
        }
        final int latest = latestPossible();
        if (date > latest) {
            return;
        }
        ensureLoaded();
        final boolean[] changed = {false};
        seen.compute(userId, (id, known) -> {
            // A noted time past the clock is broken and gives way to any real one
            if (known != null && known <= latest && known >= date - SAME_TIME) {
                return known;
            }
            changed[0] = true;
            return date;
        });
        if (changed[0]) {
            Utilities.globalQueue.postRunnable(() -> MZGramHistoryDatabase.getInstance().putLastSeen(userId, date, latest));
        }
    }

    // The latest time a sign can have: now by the server's clock, a day of slack.
    static int latestPossible() {
        int now;
        try {
            now = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
        } catch (Throwable e) {
            now = 0;
        }
        if (now <= 0) {
            now = (int) (System.currentTimeMillis() / 1000);
        }
        return now + DAY;
    }

    // A status from the server: "online" is a sign at "now", "last seen at"
    // at that time. Hidden statuses carry no time and are left to record().
    public static void recordStatus(long userId, TLRPC.UserStatus status, int now) {
        if (!MZGramConfig.lastSeenExact) {
            return;  // off: only the signs of record(), as before
        }
        if (status instanceof TLRPC.TL_userStatusOnline) {
            record(userId, now);
        } else if (status instanceof TLRPC.TL_userStatusOffline && status.expires > 0) {
            record(userId, Math.min(status.expires, now));
        }
    }

    // Reactions carry the time each was set: a sign of that person at that
    // time, the owner's own excepted.
    public static void recordReactions(TLRPC.MessageReactions reactions, long selfId) {
        if (reactions == null || reactions.recent_reactions == null) {
            return;
        }
        for (int a = 0, N = reactions.recent_reactions.size(); a < N; a++) {
            TLRPC.MessagePeerReaction reaction = reactions.recent_reactions.get(a);
            if (reaction != null && reaction.peer_id instanceof TLRPC.TL_peerUser && reaction.peer_id.user_id != selfId) {
                record(reaction.peer_id.user_id, reaction.date);
            }
        }
    }

    public static int lastSeen(long userId) {
        ensureLoaded();
        Integer at = seen.get(userId);
        return at == null ? 0 : at;
    }

    // The status line for a user who hides their last seen: the noted time,
    // as a usual last seen line, when it fits what the server says (expires is
    // the app's code for "recently", "within a week", "within a month").
    // Null otherwise, and the usual text is shown.
    public static String approximateStatus(TLRPC.User user, int now) {
        return approximateStatus(user, now, null);
    }

    // madeShorter: as for LocaleController.formatDateOnline, where the place
    // has little room ("yesterday at" without "last seen").
    public static String approximateStatus(TLRPC.User user, int now, boolean[] madeShorter) {
        if (!MZGramConfig.saveLastOnline || user == null || user.bot || user.self || user.status == null) {
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
        // Telegram's own text: the status line of a chat or profile is Telegram's, and an
        // approximate last seen reads the same as a real one
        return LocaleController.formatDateOnline(Math.min(at, now), madeShorter);
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
