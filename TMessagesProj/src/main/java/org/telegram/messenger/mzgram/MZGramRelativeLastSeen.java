/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > General > Relative last seen: a last seen time within
 * the last day reads "last seen 5 minutes ago" or "last seen 3 hours ago"
 * instead of "last seen today at 13:51", in Telegram's own words (its
 * LessMinuteAgo, MinuteAgo, MinutesAgo, HourAgo and HoursAgo texts), so it
 * follows Telegram's language like the rest of the status line. The chat
 * header refreshes it every minute while the chat is open. Older times and
 * the approximate ones stay as Telegram shows them. Off by default.
 *
 * Idea from exteraless (GPL-3.0), LocaleController.formatDateOnline with
 * relativeLastSeen; written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class MZGramRelativeLastSeen {

    public static final int DAY = 24 * 60 * 60;

    private MZGramRelativeLastSeen() {
    }

    // The status line for a last seen time (unix seconds), or null when it
    // stays Telegram's: the switch is off, or the time is a day or more ago
    // or in the future
    public static String format(long date) {
        if (!MZGramConfig.relativeLastSeen) {
            return null;
        }
        return format(date, System.currentTimeMillis() / 1000);
    }

    public static String format(long date, long now) {
        long diff = now - date;
        if (date <= 0 || diff < 0 || diff >= DAY) {
            return null;
        }
        // Telegram's own text: the status line is Telegram's, only the time in it is relative
        return LocaleController.formatString(R.string.LastSeenDateFormatted, ago(diff));
    }

    static String ago(long seconds) {
        long minutes = seconds / 60;
        if (minutes < 1) {
            // Telegram's own text: "just now" and the minutes and hours below, as Telegram writes them
            return LocaleController.getString(R.string.LessMinuteAgo);
        }
        if (minutes == 1) {
            // Telegram's own text: one minute ago
            return LocaleController.getString(R.string.MinuteAgo);
        }
        if (minutes < 60) {
            return LocaleController.formatPluralString("MinutesAgo", (int) minutes);
        }
        long hours = minutes / 60;
        if (hours == 1) {
            // Telegram's own text: one hour ago
            return LocaleController.getString(R.string.HourAgo);
        }
        return LocaleController.formatPluralString("HoursAgo", (int) hours);
    }
}
