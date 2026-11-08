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
}
