/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;

/**
 * MZGram: "send without sound" as the default. While the switch is on, new
 * messages go out silently unless they are scheduled or addressed to Saved
 * Messages (where the notification is the point of a reminder).
 */
public class MZGramSilentSend {

    private MZGramSilentSend() {
    }

    /** Turns the notification flag of a message off when the default applies. */
    public static void apply(int account, SendMessagesHelper.SendMessageParams params) {
        if (params == null || !MZGramConfig.sendSilentByDefault || !params.notify) {
            return;
        }
        if (isSilentFor(params.peer, params.scheduleDate, UserConfig.getInstance(account).getClientUserId())) {
            params.notify = false;
        }
    }

    /** The rule alone: scheduled messages and Saved Messages keep their notification. */
    static boolean isSilentFor(long peer, int scheduleDate, long selfId) {
        return scheduleDate == 0 && peer != selfId;
    }
}
