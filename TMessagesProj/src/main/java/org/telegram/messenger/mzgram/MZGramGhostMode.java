/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Ghost mode: while on, the client no longer tells the server that a
 * message was read or that the user is typing, so the other side never
 * sees a read receipt or a "typing..." status. Local read state (unread
 * counters, badges, the chat's own read line) is untouched -- only the
 * outgoing network requests are suppressed.
 *
 * Ported from AyuGram4A 7013145676d36d82ee13c02a89f72097b7490dcd
 * (utils/AyuGhostUtils.java, AyuConfig.isGhostModeActive/setGhostMode).
 * AyuGram4A keeps four separate switches (read receipts, online status,
 * upload progress, "offline after online"); MZGram folds them into the one
 * ghostMode switch in MZGramConfig, matching the Desktop MZGram ghost mode.
 */

package org.telegram.messenger.mzgram;

public class MZGramGhostMode {

    public static boolean isEnabled() {
        return MZGramConfig.ghostMode;
    }
}
