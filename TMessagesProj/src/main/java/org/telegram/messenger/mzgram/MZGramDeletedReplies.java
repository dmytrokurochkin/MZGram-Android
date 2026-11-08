/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Replies to messages another user deleted (Settings > MZGram > Archive):
 *
 * - "Show replies to deleted messages" (keptReplyPreviews): a reply whose
 *   original is gone shows the copy kept in the archive (author, text,
 *   media preview) instead of "Deleted message". Also saves the request
 *   Telegram would send to fetch an original the server no longer has.
 * - "Reply to deleted as a quote" (replyToDeletedAsQuote): the server has no
 *   message to attach a reply to, so a reply to a kept deleted message is
 *   sent as a quote of it instead: a collapsed blockquote with the author's
 *   name (a mention, or a link to the chat) and the quoted text, then the
 *   reply. An album carries the quote once, on its first part.
 *
 * Both off by default. Reading the archive: never on the UI thread.
 *
 * Ported from exteraless (GPL-3.0), app/exteraless/chats/DeletedReplyQuote.java
 * (rewrite, resolveAuthor, authorEntity, chatUrl); adapted to
 * MZGramHistoryDatabase and MZGramConfig.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashSet;

public final class MZGramDeletedReplies {

    // Album parts whose quote is already given (the first part only).
    private static final HashSet<String> quotedGroups = new HashSet<>();

    // Not more lookups than this per loaded part of a chat.
    private static final int MAX_LOOKUPS = 100;

    private MZGramDeletedReplies() {
    }

    // ---- previews ----

    private static boolean showsPreviews(long dialogId) {
        return MZGramConfig.keptReplyPreviews && MZGramHistoryController.savesChat(dialogId);
    }

    // The original a reply points to in this chat, or 0: a reply to another
    // chat or to a story has no original here.
    private static int replyId(TLRPC.Message message) {
        if (message == null || !(message.reply_to instanceof TLRPC.TL_messageReplyHeader)) {
            return 0;
        }
        TLRPC.MessageReplyHeader header = message.reply_to;
        if (header.reply_to_peer_id != null && MessageObject.getPeerId(header.reply_to_peer_id) != message.dialog_id) {
            return 0;
        }
        return header.reply_to_msg_id;
    }

    // MZGramHistoryController.messagesForChat, a loaded part of the chat: a
    // reply whose original is not in this part and is known only as
    // deleted (or not at all) gets the kept copy as its reply, so its
    // MessageObject draws it and no request goes for it.
    static void fill(int accountId, long dialogId, ArrayList<TLRPC.Message> messages) {
        if (messages == null || messages.isEmpty() || !showsPreviews(dialogId) || DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        try {
            HashSet<Integer> present = new HashSet<>();
            for (int a = 0, N = messages.size(); a < N; a++) {
                TLRPC.Message message = messages.get(a);
                if (message != null) {
                    present.add(message.id);
                }
            }
            int lookups = 0;
            for (int a = 0, N = messages.size(); a < N && lookups < MAX_LOOKUPS; a++) {
                TLRPC.Message message = messages.get(a);
                if (message == null) {
                    continue;
                }
                if (message.dialog_id == 0) {
                    message.dialog_id = dialogId;
                }
                int id = replyId(message);
                if (id <= 0 || present.contains(id)) {
                    continue;
                }
                if (message.replyMessage != null && !(message.replyMessage instanceof TLRPC.TL_messageEmpty)) {
                    continue;
                }
                lookups++;
                TLRPC.Message kept = MZGramHistoryController.getInstance().keptDeletedMessage(accountId, dialogId, id);
                if (kept != null) {
                    message.replyMessage = kept;
                }
            }
        } catch (Exception e) {
            FileLog.e("MZGramDeletedReplies.fill", e);
        }
    }

    // MediaDataController.broadcastReplyMessages: an original the server or
    // the cache gave as empty (deleted) becomes the kept copy, if there is one.
    public static TLRPC.Message forReply(int accountId, long dialogId, TLRPC.Message reply) {
        if (!(reply instanceof TLRPC.TL_messageEmpty) || !showsPreviews(dialogId) || DialogObject.isEncryptedDialog(dialogId)) {
            return reply;
        }
        TLRPC.Message kept = MZGramHistoryController.getInstance().keptDeletedMessage(accountId, dialogId, reply.id);
        return kept != null ? kept : reply;
    }

    // ---- sending ----

    private static final class Author {
        final String name;
        final TLRPC.User user;
        final TLRPC.Chat chat;

        Author(String name, TLRPC.User user, TLRPC.Chat chat) {
            this.name = name;
            this.user = user;
            this.chat = chat;
        }
    }

    private static boolean isKeptDeleted(MessageObject source) {
        return source != null && source.messageOwner != null && source.messageOwner.mzgramDeleted;
    }

    // SendMessagesHelper.sendMessage: a reply to a kept deleted message is
    // sent as a quote of it, with no reply.
    public static void rewriteSend(int accountId, SendMessagesHelper.SendMessageParams params) {
        if (!MZGramConfig.replyToDeletedAsQuote || params == null || params.retryMessageObject != null) {
            return;
        }
        try {
            ChatActivity.ReplyQuote replyQuote = params.replyQuote;
            MessageObject source = params.replyToMsg != null ? params.replyToMsg : replyQuote != null ? replyQuote.message : null;
            if (!isKeptDeleted(source)) {
                return;
            }
            Author author = resolveAuthor(accountId, source);
            if (author == null || TextUtils.isEmpty(author.name)) {
                return;
            }
            // The reply goes to the thread or topic, if any, not to the
            // deleted message.
            MessageObject top = params.replyToTopMsg;
            String groupId = groupId(params);
            if (!claimGroupQuote(groupId, groupId != null && params.params.containsKey("final"))) {
                params.replyToMsg = top;
                params.replyQuote = null;
                return;
            }
            CharSequence quoted = replyQuote != null && replyQuote.message == source ? replyQuote.getText() : source.messageText;
            String body = quoted == null ? "" : quoted.toString();
            String header = author.name;
            String quote = TextUtils.isEmpty(body) ? header : header + "\n" + body;
            String reply = params.message != null ? params.message : params.caption;
            String prefix = TextUtils.isEmpty(reply) ? quote : quote + "\n";

            if (params.entities == null) {
                params.entities = new ArrayList<>();
            }
            for (int a = 0, N = params.entities.size(); a < N; a++) {
                params.entities.get(a).offset += prefix.length();
            }
            params.entities.add(blockquote(quote.length()));
            params.entities.add(bold(header.length()));
            TLRPC.MessageEntity authorEntity = authorEntity(author, source.getId(), header.length());
            if (authorEntity != null) {
                params.entities.add(authorEntity);
            }
            if (params.message == null) {
                params.caption = prefix + (params.caption == null ? "" : params.caption);
            } else {
                params.message = prefix + params.message;
            }
            params.replyToMsg = top;
            params.replyQuote = null;
        } catch (Exception e) {
            FileLog.e("MZGramDeletedReplies.rewriteSend", e);
        }
    }

    private static String groupId(SendMessagesHelper.SendMessageParams params) {
        if (params.params == null) {
            return null;
        }
        String groupId = params.params.get("groupId");
        return groupId == null || "0".equals(groupId) ? null : groupId;
    }

    private static synchronized boolean claimGroupQuote(String groupId, boolean last) {
        if (groupId == null) {
            return true;
        }
        boolean claimed = quotedGroups.add(groupId);
        if (last) {
            quotedGroups.remove(groupId);
        }
        return claimed;
    }

    private static TLRPC.TL_messageEntityBlockquote blockquote(int length) {
        TLRPC.TL_messageEntityBlockquote quote = new TLRPC.TL_messageEntityBlockquote();
        quote.offset = 0;
        quote.length = length;
        quote.collapsed = true;
        return quote;
    }

    private static TLRPC.TL_messageEntityBold bold(int length) {
        TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
        bold.offset = 0;
        bold.length = length;
        return bold;
    }

    private static TLRPC.MessageEntity authorEntity(Author author, int messageId, int length) {
        if (author.user != null) {
            TLRPC.TL_inputMessageEntityMentionName mention = new TLRPC.TL_inputMessageEntityMentionName();
            TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
            inputUser.user_id = author.user.id;
            inputUser.access_hash = author.user.access_hash;
            mention.user_id = inputUser;
            mention.offset = 0;
            mention.length = length;
            return mention;
        }
        String url = chatUrl(author.chat, messageId);
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
        entity.offset = 0;
        entity.length = length;
        entity.url = url;
        return entity;
    }

    private static String chatUrl(TLRPC.Chat chat, int messageId) {
        if (chat == null) {
            return null;
        }
        String username = ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(username)) {
            return "tg://resolve?domain=" + username;
        }
        if (!ChatObject.isChannel(chat) || chat.id == 0 || messageId <= 0) {
            return null;
        }
        return "tg://privatepost?channel=" + chat.id + "&post=" + messageId;
    }

    private static Author resolveAuthor(int accountId, MessageObject messageObject) {
        TLRPC.Message message = messageObject.messageOwner;
        MessagesController controller = MessagesController.getInstance(accountId);
        Author author = peerAuthor(controller, message.from_id);
        if (author != null) {
            return author;
        }
        if (message.post && message.peer_id instanceof TLRPC.TL_peerChannel) {
            author = chatAuthor(controller.getChat(message.peer_id.channel_id));
            if (author != null) {
                return author;
            }
        }
        long senderId = messageObject.getSenderId();
        if (senderId > 0) {
            author = userAuthor(controller.getUser(senderId));
        } else if (senderId < 0) {
            author = chatAuthor(controller.getChat(-senderId));
        }
        if (author != null) {
            return author;
        }
        if (message.fwd_from != null) {
            author = peerAuthor(controller, message.fwd_from.from_id);
            if (author != null) {
                return author;
            }
            if (!TextUtils.isEmpty(message.fwd_from.from_name)) {
                return new Author(message.fwd_from.from_name, null, null);
            }
        }
        return null;
    }

    private static Author peerAuthor(MessagesController controller, TLRPC.Peer peer) {
        if (peer instanceof TLRPC.TL_peerUser) {
            return userAuthor(controller.getUser(peer.user_id));
        }
        if (peer instanceof TLRPC.TL_peerChannel) {
            return chatAuthor(controller.getChat(peer.channel_id));
        }
        if (peer instanceof TLRPC.TL_peerChat) {
            return chatAuthor(controller.getChat(peer.chat_id));
        }
        return null;
    }

    private static Author userAuthor(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        String name = UserObject.getUserName(user);
        return TextUtils.isEmpty(name) ? null : new Author(name, user, null);
    }

    private static Author chatAuthor(TLRPC.Chat chat) {
        if (chat == null || TextUtils.isEmpty(chat.title)) {
            return null;
        }
        return new Author(chat.title, null, chat);
    }
}
