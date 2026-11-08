package org.telegram.messenger.mzgram;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;

/**
 * MZGram: pauses a round video message that is playing in a chat when that chat
 * is covered by another screen (for example when switching to another dialog).
 * Off by default.
 */
public class MZGramRoundPause {

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
