/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Archive rules for one chat (Settings > MZGram > Archive > "Archive rules
 * per chat", off by default): a chat follows Keep messages (MZGramKeepFrom),
 * always keeps other people's deleted and edited messages, or never keeps
 * them. "Save deleted messages" and "Save edit history" still decide for
 * every chat, and the owner's own messages are never kept. Turning a chat to
 * "never" stops new saves and keeps what was saved.
 *
 * The rule belongs to the chat, not to one account: the archive decides by
 * the dialog id before the account is known (MZGramHistoryController
 * .savesChat), as the reference keeps it per chat id.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public final class MZGramSaveExclusions {

    public static final int RULE_DEFAULT = 0;
    public static final int RULE_ALWAYS = 1;
    public static final int RULE_NEVER = 2;

    private static final String PREFS = "mzgram_save_exclusions";

    private static volatile SharedPreferences preferences;

    private MZGramSaveExclusions() {
    }

    private static SharedPreferences preferences() {
        if (preferences == null) {
            synchronized (MZGramSaveExclusions.class) {
                if (preferences == null) {
                    preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                }
            }
        }
        return preferences;
    }

    // The rule while "Archive rules per chat" is on, else RULE_DEFAULT (the
    // saved rules are kept and come back when the switch is on again).
    public static int ruleFor(long dialogId) {
        if (!MZGramConfig.saveExclusions || dialogId == 0) {
            return RULE_DEFAULT;
        }
        return getRule(dialogId);
    }

    public static int getRule(long dialogId) {
        try {
            return preferences().getInt(String.valueOf(dialogId), RULE_DEFAULT);
        } catch (Exception e) {
            return RULE_DEFAULT;
        }
    }

    public static void setRule(long dialogId, int rule) {
        if (dialogId == 0) {
            return;
        }
        if (rule == RULE_ALWAYS || rule == RULE_NEVER) {
            preferences().edit().putInt(String.valueOf(dialogId), rule).apply();
        } else {
            preferences().edit().remove(String.valueOf(dialogId)).apply();
        }
    }

    // What Keep messages said, with this chat's rule over it.
    public static boolean applies(long dialogId, boolean byKeepFrom) {
        int rule = ruleFor(dialogId);
        if (rule == RULE_ALWAYS) {
            return true;
        }
        if (rule == RULE_NEVER) {
            return false;
        }
        return byKeepFrom;
    }
}
