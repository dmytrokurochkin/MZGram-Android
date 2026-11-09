/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The owner's own "last seen" as other people see it: Telegram never shows
 * it to the owner. This device notes when it last told the server the
 * account is online or offline (account.updateStatus), per account, and
 * Settings > MZGram > Privacy shows it. Nothing leaves the device.
 *
 * Idea from exteraless (GPL-3.0), app/exteraless/ghost/OwnLastSeen.java.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class MZGramOwnLastSeen {

    private static final String FEATURE = "own_last_seen";
    // The server keeps "online" for about five minutes after the last update.
    private static final int ONLINE_WINDOW = 5 * 60;

    private MZGramOwnLastSeen() {
    }

    // An account.updateStatus request the server accepted.
    public static void onStatusSent(int account, boolean offline, int now) {
        if (!MZGramConfig.ownLastSeen || now <= 0) {
            return;
        }
        MZGramAccountPrefs prefs = MZGramAccountPrefs.of(FEATURE, account);
        prefs.putInt("at", now);
        prefs.putBoolean("offline", offline);
    }

    // "online", a usual "last seen" line, or "not known yet".
    public static String describe(int account, int now) {
        MZGramAccountPrefs prefs = MZGramAccountPrefs.of(FEATURE, account);
        int at = prefs.getInt("at", 0);
        if (at <= 0) {
            return LocaleController.getString(R.string.MZGramOwnLastSeenUnknown);
        }
        boolean offline = prefs.getBoolean("offline", true);
        if (!offline && now - at < ONLINE_WINDOW) {
            return LocaleController.getString(R.string.MZGramOnline);
        }
        return LocaleController.formatString(R.string.MZGramLastSeenAt, MZGramLanguage.formatDateTime(Math.min(at, now) * 1000L, false));
    }
}
