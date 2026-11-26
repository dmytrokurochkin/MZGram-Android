/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.mzgram.ext;

import org.mzgram.arc.messages.ArcForwardLoader;
import org.mzgram.core.helpers.MessageHelper;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Collections;

/**
 * MZGram archive settings > "Save protected content": in chats and channels whose owner
 * turned on "Restrict saving content", messages and media can be saved, copied and
 * screenshotted like anywhere else.
 *
 * The app's own checks (MessagesController.isChatNoForwards, isUserNoForwards,
 * isPeerNoForwards, and the message flag through {@link #noforwards}) say "not restricted"
 * while the switch is on. The server still refuses to forward such messages, so they are
 * sent again as copies: ChatActivity does it for its own forward, {@link #sendCopies} for
 * every other forward (share sheet, media viewer, shared media).
 */
public final class ProtectedContent {

    private ProtectedContent() {
    }

    public static boolean isOn() {
        return ExtConfig.INSTANCE.getSaveProtectedContent().Bool();
    }

    /** The message's own "no forwards" flag as the app's save, copy and screenshot checks see it. */
    public static boolean noforwards(TLRPC.Message message) {
        return message != null && message.noforwards && !isOn();
    }

    /** What the server enforces, whatever the switch says. */
    public static boolean isProtected(int account, MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return false;
        }
        return message.messageOwner.noforwards || MessagesController.getInstance(account).isPeerNoForwardsOnServer(message.getDialogId());
    }

    /**
     * SendMessagesHelper.sendMessage, forwarding: with the switch on, sends the protected
     * messages that can be copied as copies and returns the others, to be forwarded as usual.
     * Returns null when there is nothing to copy, so the forward goes on unchanged.
     */
    public static ArrayList<MessageObject> sendCopies(int account, ArrayList<MessageObject> messages, long peer, boolean notify, int scheduleDate,
                                                      MessageObject replyToTopMsg, long payStars, long monoForumPeerId, MessageSuggestionParams suggestionParams) {
        if (!isOn() || messages == null || messages.isEmpty()) {
            return null;
        }
        final MessageHelper helper = MessageHelper.getInstance(account);
        final ArrayList<MessageObject> copies = new ArrayList<>();
        final ArrayList<MessageObject> rest = new ArrayList<>();
        for (MessageObject message : messages) {
            if (isProtected(account, message) && helper.canSendMessagesAsCopy(new ArrayList<>(Collections.singletonList(message)))) {
                copies.add(message);
            } else {
                rest.add(message);
            }
        }
        if (copies.isEmpty()) {
            return null;
        }
        final Runnable send = () -> {
            if (!helper.sendMessagesAsCopy(copies, peer, null, replyToTopMsg, null, notify, scheduleDate, 0, null, 0, payStars, monoForumPeerId, suggestionParams)) {
                FileLog.e("ProtectedContent: could not send " + copies.size() + " copies to " + peer);
            }
        };
        final ArrayList<MessageObject> missing = ArcForwardLoader.getMissingMedia(account, copies);
        if (missing.isEmpty()) {
            send.run();
            return rest;
        }
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getCurrentAccount() != account) {
            // Nowhere to show the download: forward them all as usual, the server refuses as before.
            return null;
        }
        ArcForwardLoader.load(fragment, missing, send);
        return rest;
    }
}
