/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Sticker display in chats: size, rounded corners and hiding the time over
 * stickers. All options are off by default and change only how stickers
 * are shown.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.mzgram.MZGramConfig;

public final class MZGramStickers {

    public static final int SIZE_MIN = 50;
    public static final int SIZE_DEFAULT = 100;
    public static final int SIZE_MAX = 150;

    private MZGramStickers() {
    }

    private static boolean isSticker(MessageObject message) {
        return message != null && message.isAnyKindOfSticker() && message.type != MessageObject.TYPE_EMOJIS
                && !message.isAnimatedEmoji() && !message.isDice();
    }

    /** Factor for the largest side of a sticker in the chat; 1 for anything else. */
    public static float sizeFactor(MessageObject message) {
        int size = MZGramConfig.stickerSize;
        if (size == SIZE_DEFAULT || !isSticker(message)) {
            return 1f;
        }
        return Math.max(SIZE_MIN, Math.min(SIZE_MAX, size)) / 100f;
    }

    /** Corner radius of a sticker in the chat: 0 as in Telegram, or a small rounding. */
    public static int roundRadius() {
        return MZGramConfig.stickerRoundCorners ? dp(6) : 0;
    }

    /** Whether the time over a sent sticker is hidden; it stays while sending, on errors and when selected. */
    public static boolean isTimeHidden(MessageObject message) {
        return MZGramConfig.hideStickerTime && isSticker(message) && !message.isSending() && !message.isSendError();
    }
}
