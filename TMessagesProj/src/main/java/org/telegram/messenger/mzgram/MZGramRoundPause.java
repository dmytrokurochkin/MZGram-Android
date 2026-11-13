/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;

/**
 * MZGram: pauses a round video message that is playing in a chat when that chat
 * is covered by another screen (for example when switching to another dialog).
 * Off by default.
 */
public class MZGramRoundPause {

    /**
     * The app went to the background (LaunchActivity.onPause): a playing voice
     * message pauses with "Pause voice messages when minimized" (off by
     * default; idea from exteraless MediaController.pauseInBackgroundIfNeeded,
     * GPL-3.0). Music keeps playing.
     */
    public static void onAppMinimized() {
        if (!MZGramConfig.pauseVoiceOnMinimize) {
            return;
        }
        MediaController controller = MediaController.getInstance();
        MessageObject playing = controller.getPlayingMessageObject();
        if (playing != null && playing.isVoice() && controller.isPlayingMessage(playing) && !controller.isMessagePaused()) {
            controller.pauseMessage(playing);
        }
    }

    /** Called when a chat screen becomes fully hidden. */
    public static void onChatHidden(long dialogId) {
        if (!MZGramConfig.pauseRoundOnLeave) {
            return;
        }
        MediaController controller = MediaController.getInstance();
        MessageObject playing = controller.getPlayingMessageObject();
        if (playing != null && playing.isRoundVideo() && playing.getDialogId() == dialogId && !controller.isMessagePaused()) {
            controller.pauseMessage(playing);
        }
    }
}
