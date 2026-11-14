/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > General > Unread chats first: chats with something
 * unread go above the others in the chat list, under the pinned ones. A
 * muted chat counts only with a mention or a reaction. From exteraless
 * (GPL-3.0).
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

public class MZGramUnreadSort {

    public static boolean isPriority(int account, TLRPC.Dialog dialog) {
        if (dialog == null || dialog instanceof TLRPC.TL_dialogFolder) {
            return false;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        int unreadCount;
        int mentionCount;
        int reactionCount;
        boolean counterMuted;
        TLRPC.Chat chat = dialog.id < 0 ? controller.getChat(-dialog.id) : null;
        if (chat != null && (chat.forum || chat.monoforum && ChatObject.canManageMonoForum(account, chat))) {
            // Forums count their topics: [topics unread, with mentions, with reactions, messages unread in unmuted topics]
            int[] counts = controller.getTopicsController().getForumUnreadCount(chat.id);
            unreadCount = counts[0];
            mentionCount = ChatObject.isMonoForum(chat) ? 0 : counts[1];
            reactionCount = counts[2];
            counterMuted = counts[3] == 0;
        } else {
            unreadCount = dialog.unread_count;
            mentionCount = dialog.unread_mentions_count;
            reactionCount = dialog.unread_reactions_count;
            counterMuted = controller.isDialogMuted(dialog.id, 0);
        }
        return mentionCount > 0 || reactionCount > 0 || unreadCount > 0 && !counterMuted;
    }
}
