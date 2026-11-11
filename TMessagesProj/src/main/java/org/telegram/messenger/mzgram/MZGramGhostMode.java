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
 * One ghostMode switch in MZGramConfig covers all of it, matching the
 * Desktop MZGram ghost mode.
 *
 * Exclusions (MZGramConfig.ghostModeExclusions): one chat can follow the
 * switch, always show reads and typing, or never show them even while ghost
 * mode is off. Set from the chat's menu, kept per account.
 */

package org.telegram.messenger.mzgram;

public class MZGramGhostMode {

    public static final int EXCLUSION_DEFAULT = 0;
    public static final int EXCLUSION_ALWAYS_READ = 1;
    public static final int EXCLUSION_NEVER_READ = 2;

    private static final String FEATURE = "ghost_exclusions";

    public static boolean isEnabled() {
        return MZGramConfig.ghostMode;
    }

    // Ghost mode for one chat: its exclusion wins over the switch.
    public static boolean isEnabledFor(int account, long dialogId) {
        if (MZGramConfig.ghostModeExclusions && dialogId != 0) {
            int exclusion = getExclusion(account, dialogId);
            if (exclusion == EXCLUSION_ALWAYS_READ) {
                return false;
            }
            if (exclusion == EXCLUSION_NEVER_READ) {
                return true;
            }
        }
        return MZGramConfig.ghostMode;
    }

    public static int getExclusion(int account, long dialogId) {
        return MZGramAccountPrefs.of(FEATURE, account).getInt(String.valueOf(dialogId), EXCLUSION_DEFAULT);
    }

    public static void setExclusion(int account, long dialogId, int exclusion) {
        MZGramAccountPrefs prefs = MZGramAccountPrefs.of(FEATURE, account);
        if (exclusion == EXCLUSION_ALWAYS_READ || exclusion == EXCLUSION_NEVER_READ) {
            prefs.putInt(String.valueOf(dialogId), exclusion);
        } else {
            prefs.remove(String.valueOf(dialogId));
        }
    }
}
