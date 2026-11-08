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
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.browser.Browser;

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
}
