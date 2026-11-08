/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Privacy: small checks that the hooks in Telegram's
 * classes call. Each one is off by default and costs nothing while off.
 *
 * Confirm internal links: ported from Mercurygram (GPL-2.0),
 * TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java
 * (shouldShowUrlInAlert) and ui/Components/AlertsCreator.java
 * (showOpenUrlAlert).
 *
 * No link preview while typing: ported from Mercurygram (GPL-2.0),
 * it/belloworld/mercurygram/MgLinkPreview.java and its call sites in
 * ChatActivity.searchLinks, ChatActivityEnterView.isMessageWebPageSearchEnabled,
 * poll/WebPageLoader, ShareTopView, StoryLinkSheet and BotShareSheet.
 *
 * Drafts on this device only: ported from Mercurygram (GPL-2.0),
 * it/belloworld/mercurygram/MgAccountConfig.java (allowsCloudSync) and its
 * call in MediaDataController.saveDraft.
 *
 * Voice messages on the lock screen: ported from Mercurygram (GPL-2.0),
 * org/telegram/messenger/MediaController.java (playMessage),
 * MusicPlayerService.java (setVisibility) and AndroidUtilities.java
 * (isKeyguardLocked).
 *
 * CDN refusal: ported from Mercurygram (GPL-2.0),
 * org/telegram/messenger/FileLoadOperation.java (mgRefusedCdnRedirect).
 */

package org.telegram.messenger.mzgram;

import android.app.KeyguardManager;
import android.app.Notification;
import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;

public final class MZGramPrivacy {

    private MZGramPrivacy() {
    }

    // A t.me or tg:// link asks before it opens, the way an outside link
    // with a look-alike address does, so a hidden link cannot join a channel,
    // start a bot or add a proxy in one tap.
    public static boolean confirmInternalLink(String url) {
        if (!MZGramConfig.confirmInternalLinks || url == null) {
            return false;
        }
        try {
            return Browser.isInternalUrl(url, null);
        } catch (Exception e) {
            return false;
        }
    }

    // Trending sticker packs are marked viewed only here: the server is not
    // told which ones were looked at.
    public static boolean keepStickerReadLocal() {
        return MZGramConfig.disableReadStickers;
    }

    // A link typed or pasted in a message, share, poll, story or bot field
    // is not sent to the server for a preview (account.getWebPagePreview),
    // so the server does not open it before the message is sent; the
    // message then goes without a preview. Received previews still show.
    public static boolean noLinkPreviewWhileTyping() {
        return MZGramConfig.disableLinkPreviews;
    }

    // Whether a draft goes to the server (messages.saveDraft). With drafts
    // on this device only, just an empty draft does: it clears one left in
    // the cloud from before, so the old text does not come back.
    // A voice message or round video does not start or resume while the
    // phone is locked: the lock screen player and headset buttons cannot
    // play it to someone holding the phone. Music still can.
    public static boolean blockPlayWhileLocked(MessageObject messageObject) {
        if (!MZGramConfig.lockScreenVoiceProtection || messageObject == null) {
            return false;
        }
        return (messageObject.isVoice() || messageObject.isRoundVideo()) && isKeyguardLocked();
    }

    // The player notification of a voice message or round video shows no
    // name or picture on a secure lock screen; music stays public.
    public static int playerNotificationVisibility(MessageObject messageObject) {
        if (MZGramConfig.lockScreenVoiceProtection && messageObject != null
                && (messageObject.isVoice() || messageObject.isRoundVideo())) {
            return Notification.VISIBILITY_PRIVATE;
        }
        return Notification.VISIBILITY_PUBLIC;
    }

    // Telegram's CDN servers only know the account's permanent key, so every
    // CDN frame carries the same auth_key_id and a network observer can link
    // downloads across networks. A CDN redirect is refused and the file is
    // asked again from the main datacenter, without CDN (cdn_supported off).
    // The few requests already on the way may be redirected too; if the
    // server still insists, the download goes through the CDN after all.
    public static final int MAX_CDN_REFUSALS = 16;

    public static boolean refuseCdnRedirect(int refusedSoFar) {
        return MZGramConfig.reduceNetworkTracking && refusedSoFar < MAX_CDN_REFUSALS;
    }

    public static boolean isKeyguardLocked() {
        try {
            KeyguardManager km = (KeyguardManager) ApplicationLoader.applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean allowsCloudDraft(TLRPC.DraftMessage draft) {
        if (!MZGramConfig.disableCloudDrafts) {
            return true;
        }
        if (draft == null) {
            return true;
        }
        return TextUtils.isEmpty(draft.message)
                && (draft.reply_to == null || draft.reply_to.reply_to_msg_id == 0)
                && draft.effect == 0
                && draft.rich_message == null
                && draft.suggested_post == null
                && draft.media == null;
    }
}
