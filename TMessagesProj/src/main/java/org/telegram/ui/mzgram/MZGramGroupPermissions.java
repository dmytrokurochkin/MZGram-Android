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
 * A second option makes the member side follow these flags: the GIF tab and
 * inline-bot results check send_gifs and send_inline instead of the sticker
 * permission. Off by default.
 *
 * Idea from Nagram X (GPL-3.0); written anew for MZGram.
 */

package org.telegram.ui.mzgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

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

    /** Whether the GIF tab and inline results follow their own permissions in a member's chat. */
    public static boolean isMemberSplitOn() {
        return MZGramConfig.splitGifInlineRights;
    }

    /** Same order of checks as ChatObject.canUserDoAction, for a flag it does not know. */
    private static boolean canDo(TLRPC.Chat chat, boolean bannedSelf, boolean bannedDefault) {
        if (chat == null || ChatObject.isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        if (chat.creator || chat.admin_rights != null) {
            return true;
        }
        if (bannedSelf) {
            return false;
        }
        if (chat.default_banned_rights == null) {
            return ChatObject.canSendStickers(chat);
        }
        return !bannedDefault;
    }

    public static boolean canSendGifs(TLRPC.Chat chat) {
        return chat == null || canDo(chat,
                chat.banned_rights != null && chat.banned_rights.send_gifs,
                chat.default_banned_rights != null && chat.default_banned_rights.send_gifs);
    }

    /** Inline-bot results in the chat: the sticker permission as in Telegram, or send_inline with the option on. */
    public static boolean canSendInline(TLRPC.Chat chat) {
        if (!isMemberSplitOn()) {
            return ChatObject.canSendStickers(chat);
        }
        return chat == null || canDo(chat,
                chat.banned_rights != null && chat.banned_rights.send_inline,
                chat.default_banned_rights != null && chat.default_banned_rights.send_inline);
    }

    /**
     * Whether the GIF tab of the emoji panel may open, or null to keep Telegram's
     * decision (option off or no chat).
     */
    public static Boolean gifTabAllowed(int account, long chatId, boolean stickersBanned, BaseFragment fragment) {
        if (!isMemberSplitOn() || chatId == 0) {
            return null;
        }
        TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(account).getChat(chatId);
        if (chat == null) {
            return null;
        }
        if (canSendGifs(chat)) {
            return true;
        }
        if (!stickersBanned && fragment != null) {
            String text;
            if (chat.default_banned_rights != null && chat.default_banned_rights.send_gifs) {
                text = LocaleController.getString(R.string.GlobalAttachGifRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                text = LocaleController.formatString(R.string.AttachGifRestrictedForever);
            } else {
                text = LocaleController.formatString(R.string.AttachGifRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        }
        return false;
    }
}
