/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Separate group permissions: the combined "Stickers and GIFs" row of the
 * group permissions and of a member's restrictions is split into Stickers,
 * GIFs, Games and Inline bots. These are four independent flags of
 * chatBannedRights in the Telegram protocol; the official screens set them
 * together. Off by default.
 *
 * Idea from Nagram X (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;

public final class MZGramGroupPermissions {

    /** Media rows of the official screens: photos, videos, files, music, voice, round, stickers, polls, links, reactions. */
    private static final int MEDIA_ROWS = 10;

    private MZGramGroupPermissions() {
    }

    public static boolean isOn() {
        return MZGramConfig.granularGroupPermissions;
    }

    /** Number of rows under "Send media": three more with the option on. */
    public static int mediaTotal() {
        return MEDIA_ROWS + extraTotal();
    }

    public static int extraTotal() {
        return isOn() ? 3 : 0;
    }

    /** Allowed GIFs, games and inline bots (the rows added by the option); defaults may be null. */
    public static int extraCount(TLRPC.TL_chatBannedRights rights, TLRPC.TL_chatBannedRights defaults) {
        if (!isOn() || rights == null) {
            return 0;
        }
        int count = 0;
        if (!rights.send_gifs && (defaults == null || !defaults.send_gifs)) {
            count++;
        }
        if (!rights.send_games && (defaults == null || !defaults.send_games)) {
            count++;
        }
        if (!rights.send_inline && (defaults == null || !defaults.send_inline)) {
            count++;
        }
        return count;
    }

    /** Title of the stickers row: "Stickers" alone with the option on. */
    public static int stickersTitle() {
        return isOn() ? R.string.MZGramPermissionStickers : R.string.SendMediaPermissionStickersGifs;
    }
}
