/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Credits to other open-source projects are in the class comment below and
 * in NOTICE.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * MZGram: hides reactions under messages, either all of them in the chosen
 * kinds of chats (private, groups, channels) or only the chosen emoji.
 * Splitting by kind of chat follows exteraGram-less ChatsConfig.hideReactionsIn*
 * (GPL-3.0); the emoji list is MZGram's own. Only what is shown changes:
 * nothing is sent to the server and tags in Saved Messages always stay.
 */
public final class MZGramReactionFilter {

    private static String cachedSource;
    private static HashSet<String> cachedSet = new HashSet<>();

    private MZGramReactionFilter() {
    }

    private static boolean isTags(MessageObject message) {
        return message.messageOwner != null && message.messageOwner.reactions != null && message.messageOwner.reactions.reactions_as_tags;
    }

    /** True when all reactions of this message are hidden (also hides the picker and the double tap reaction). */
    public static boolean isHidden(MessageObject message) {
        if (message == null || !MZGramConfig.hideReactionsPrivate && !MZGramConfig.hideReactionsGroups && !MZGramConfig.hideReactionsChannels) {
            return false;
        }
        if (isTags(message)) {
            return false;
        }
        long dialogId = message.getDialogId();
        if (dialogId >= 0) {
            return MZGramConfig.hideReactionsPrivate && dialogId != UserConfig.getInstance(message.currentAccount).getClientUserId();
        }
        TLRPC.Chat chat = MessagesController.getInstance(message.currentAccount).getChat(-dialogId);
        return ChatObject.isChannelAndNotMegaGroup(chat) ? MZGramConfig.hideReactionsChannels : MZGramConfig.hideReactionsGroups;
    }

    /** True when this reaction is in the hidden list; the user's own reactions always stay visible. */
    public static boolean isEmojiHidden(MessageObject message, TLRPC.ReactionCount reactionCount) {
        if (reactionCount == null || reactionCount.chosen || TextUtils.isEmpty(MZGramConfig.hiddenReactions)
                || !(reactionCount.reaction instanceof TLRPC.TL_reactionEmoji) || message != null && isTags(message)) {
            return false;
        }
        return hiddenSet().contains(normalize(((TLRPC.TL_reactionEmoji) reactionCount.reaction).emoticon));
    }

    /** Emoji differ from server ones at most by the variation selector. */
    static String normalize(String emoji) {
        return emoji == null ? "" : emoji.replace("️", "");
    }

    private static synchronized HashSet<String> hiddenSet() {
        String source = MZGramConfig.hiddenReactions;
        if (!TextUtils.equals(source, cachedSource)) {
            HashSet<String> set = new HashSet<>();
            for (String emoji : parse(source)) {
                set.add(normalize(emoji));
            }
            cachedSet = set;
            cachedSource = source;
        }
        return cachedSet;
    }

    /** Every emoji of the text once, in the order typed; everything else is dropped. */
    public static ArrayList<String> parse(CharSequence text) {
        ArrayList<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return result;
        }
        HashSet<String> seen = new HashSet<>();
        for (Emoji.EmojiSpanRange range : Emoji.parseEmojis(text)) {
            if (range.start < 0 || range.end > text.length() || range.start >= range.end) {
                continue;
            }
            String emoji = text.subSequence(range.start, range.end).toString();
            if (seen.add(normalize(emoji))) {
                result.add(emoji);
            }
        }
        return result;
    }

    /** Saves the emoji of the typed text, separated by spaces. */
    public static void setHiddenList(String text) {
        MZGramConfig.setHiddenReactions(TextUtils.join(" ", parse(text)));
    }
}
