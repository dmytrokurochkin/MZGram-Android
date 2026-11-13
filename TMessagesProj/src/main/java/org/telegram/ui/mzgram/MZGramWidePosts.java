/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Wide channel posts: posts of broadcast channels are laid out almost across
 * the whole screen, with the channel avatar next to the name instead of the
 * side avatar. Off by default; with the option off nothing changes.
 *
 * Width math ported from exteraless WideChannelPostLayout (GPL-3.0),
 * https://github.com/exteraless; adapted for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;

public final class MZGramWidePosts {

    private static final int OUTER_INSET_DP = 9;
    private static final int MEDIA_BACKGROUND_CONTENT_INSET_DP = 8;
    private static final int REGULAR_BACKGROUND_CONTENT_INSET_DP = 17;
    /** Grouped media span count as in Telegram (out of 1000). */
    public static final int REGULAR_SPAN_COUNT = 800;

    private MZGramWidePosts() {
    }

    /** A post of a broadcast channel shown in the channel itself, with the option on. */
    public static boolean hasHeader(MessageObject message) {
        if (message == null
                || !(MZGramConfig.wideChannelPosts || message.mzgramFeed && MZGramConfig.feedWidePosts) // the Feed has its own switch
                || message.messageOwner == null
                || message.messageOwner.action != null
                || message.messageOwner.peer_id == null
                || message.messageOwner.peer_id.channel_id == 0
                || message.eventId != 0
                || message.preview
                || message.sendPreview
                || message.previewForward
                || message.isRepostPreview
                || message.isSponsored()) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(message.currentAccount).getChat(message.messageOwner.peer_id.channel_id);
        return chat != null ? ChatObject.isChannelAndNotMegaGroup(chat) : message.messageOwner.post;
    }

    /** Wide layout: a channel post that has a bubble (not stickers, round videos and the like). */
    public static boolean isWide(MessageObject message) {
        return hasHeader(message) && !message.shouldDrawWithoutBackground();
    }

    public static int backgroundWidth(int viewportWidth, int leadingInset, boolean mediaBackground) {
        int width = viewportWidth - leadingInset - dp(OUTER_INSET_DP * 2);
        if (mediaBackground) {
            width -= dp(OUTER_INSET_DP);
        }
        return Math.max(dp(1), width);
    }

    public static int messageTextWidth(int viewportWidth, int leadingInset) {
        return Math.max(dp(1), backgroundWidth(viewportWidth, leadingInset, false) - dp(31));
    }

    public static int mediaContentWidth(int backgroundWidth, boolean mediaBackground) {
        int contentInset = mediaBackground
                ? MEDIA_BACKGROUND_CONTENT_INSET_DP
                : REGULAR_BACKGROUND_CONTENT_INSET_DP;
        return Math.max(dp(1), backgroundWidth - dp(contentInset));
    }

    public static int mediaContentWidth(int viewportWidth, int leadingInset) {
        return mediaContentWidth(backgroundWidth(viewportWidth, leadingInset, false), false);
    }

    public static int groupedMediaViewportWidth(int viewportWidth, int leadingInset) {
        return Math.max(dp(1), viewportWidth - leadingInset);
    }

    /** Span count of grouped media for the default viewport (screen or tablet side). */
    public static int groupedMediaContentSpanCount() {
        int viewportWidth = AndroidUtilities.isTablet()
                ? AndroidUtilities.getMinTabletSide()
                : AndroidUtilities.displaySize.x;
        return groupedMediaContentSpanCount(viewportWidth);
    }

    public static int groupedMediaContentSpanCount(int groupedMediaViewportWidth) {
        int viewportWidth = groupedMediaViewportWidth;
        if (viewportWidth <= 0) {
            return 1000;
        }
        int contentWidth = Math.max(dp(1), viewportWidth - dp(OUTER_INSET_DP * 2));
        return Math.max(1, Math.min(1000, Math.round(contentWidth * 1000f / viewportWidth)));
    }

    /** Span count for a group: wide or Telegram's 800. */
    public static int spanCount(boolean wide, int groupedMediaViewportWidth) {
        if (!wide) {
            return REGULAR_SPAN_COUNT;
        }
        return groupedMediaViewportWidth > 0
                ? groupedMediaContentSpanCount(groupedMediaViewportWidth)
                : groupedMediaContentSpanCount();
    }
}
