/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Chats > "Double tap actions": what a double tap on a
 * message does, set apart for your own messages and for other people's:
 * Telegram's quick reaction (as it is), reply, copy the text, or nothing.
 * Nothing translates or sends anything.
 *
 * Idea from Nekogram (GPL-3.0) DoubleTap; written for MZGram without the
 * translate and speak actions.
 */

package org.telegram.ui.mzgram;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BulletinFactory;

import static org.telegram.messenger.LocaleController.getString;

public final class MZGramDoubleTap {

    // The values stored in MZGramConfig.doubleTapOwn and doubleTapOther
    public static final int REACTION = 0;
    public static final int REPLY = 1;
    public static final int COPY = 2;
    public static final int NONE = 3;
    public static final int[] TITLES = {
            R.string.MZGramDoubleTapReaction,
            R.string.MZGramDoubleTapReply,
            R.string.MZGramDoubleTapCopy,
            R.string.MZGramDoubleTapNothing,
    };

    private MZGramDoubleTap() {
    }

    private static MessageObject messageOf(View view) {
        if (view instanceof ChatMessageCell) {
            return ((ChatMessageCell) view).getPrimaryMessageObject();
        } else if (view instanceof ChatActionCell) {
            return ((ChatActionCell) view).getMessageObject();
        }
        return null;
    }

    // What a double tap on this message does: REACTION leaves it to Telegram.
    public static int actionFor(View view) {
        if (!MZGramConfig.doubleTapActions) {
            return REACTION;
        }
        MessageObject message = messageOf(view);
        if (message == null || message.isDateObject) {
            return REACTION;
        }
        int action = message.isOutOwner() ? MZGramConfig.doubleTapOwn : MZGramConfig.doubleTapOther;
        return action < REACTION || action > NONE ? REACTION : action;
    }

    // Whether the double tap has something to do on this message; Telegram
    // asks it to know whether a single tap may wait for a second one.
    public static boolean available(ChatActivity chat, View view, int action) {
        if (action == NONE || chat.isSecretChat() || chat.isInScheduleMode()) {
            return false;
        }
        MessageObject message = messageOf(view);
        if (message == null || message.isSponsored() || message.isSending()) {
            return false;
        }
        if (action == COPY) {
            return !TextUtils.isEmpty(textOf(message));
        }
        // REPLY: the same chats where the swipe replies in the field
        return message.getId() > 0 && chat.canSendMessage() && canWrite(chat);
    }

    private static boolean canWrite(ChatActivity chat) {
        org.telegram.tgnet.TLRPC.Chat current = chat.getCurrentChat();
        if (current == null) {
            return true;
        }
        if (ChatObject.isNotInChat(current) && !chat.isThreadChat()) {
            return false;
        }
        if (ChatObject.isChannel(current) && !ChatObject.canPost(current) && !current.megagroup) {
            return false;
        }
        return ChatObject.canSendMessages(current);
    }

    private static CharSequence textOf(MessageObject message) {
        if (message.caption != null) {
            return message.caption;
        }
        if (message.type == MessageObject.TYPE_TEXT && message.messageText != null) {
            return message.messageText;
        }
        return message.messageOwner != null ? message.messageOwner.message : null;
    }

    // Does the action; false when there was nothing to do.
    public static boolean perform(ChatActivity chat, View view, int action) {
        if (!available(chat, view, action)) {
            return false;
        }
        MessageObject message = messageOf(view);
        if (action == COPY) {
            AndroidUtilities.addToClipboard(textOf(message));
            BulletinFactory.of(chat).createCopyBulletin(getString(R.string.MessageCopied)).show();
        } else {
            chat.showFieldPanelForReply(message);
        }
        return true;
    }
}
