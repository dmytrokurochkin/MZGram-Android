/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Interface > Show ID: the ID of a user, bot, group or
 * channel as a row of its profile. Hidden by default; Telegram API shows
 * the bare number, Bot API the number the Bot API uses: -100 and the
 * number for a channel or supergroup, minus the number for a group, the
 * bare number for a user or bot. A tap on it copies it.
 */

package org.telegram.messenger.mzgram;

public final class MZGramPeerId {

    public static final int HIDDEN = 0;
    public static final int TELEGRAM_API = 1;
    public static final int BOT_API = 2;

    public static final int USER = 0;
    public static final int GROUP = 1;
    public static final int CHANNEL = 2;

    // The Bot API's channels: -(10^12 + id)
    private static final long BOT_API_CHANNEL_SHIFT = 1000000000000L;

    private MZGramPeerId() {
    }

    // Empty when hidden.
    public static String format(long id, int kind, int mode) {
        if (mode != TELEGRAM_API && mode != BOT_API || id <= 0) {
            return "";
        } else if (mode == TELEGRAM_API || kind == USER) {
            return Long.toString(id);
        } else if (kind == GROUP) {
            return "-" + id;
        }
        return "-" + (BOT_API_CHANNEL_SHIFT + id);
    }
}
