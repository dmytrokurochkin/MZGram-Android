/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/**
 * MZGram: shadow ban. Messages of the chosen people (and, by a separate
 * switch, of blocked users) are not shown in groups and do not notify; to
 * them and to everyone else nothing changes, nothing is sent to the server.
 * Replies to such messages are hidden too.
 */
public final class MZGramShadowBan {

    /** Row type of the chat list for a hidden message: an empty view of zero height. */
    public static final int VIEW_TYPE = -1913;

    private static String cachedSource;
    private static HashSet<Long> cachedSet = new HashSet<>();

    private MZGramShadowBan() {
    }

    private static synchronized HashSet<Long> ids() {
        String source = MZGramConfig.shadowBanList;
        if (!TextUtils.equals(source, cachedSource)) {
            HashSet<Long> set = new HashSet<>();
            if (!TextUtils.isEmpty(source)) {
                for (String part : source.split(",")) {
                    try {
                        long id = Long.parseLong(part.trim());
                        if (id != 0) {
                            set.add(id);
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            cachedSet = set;
            cachedSource = source;
        }
        return cachedSet;
    }

    /** Peer ids in the list (users positive, channels negative), sorted. */
    public static ArrayList<Long> list() {
        ArrayList<Long> result = new ArrayList<>(ids());
        Collections.sort(result);
        return result;
    }

    public static int count() {
        return ids().size();
    }

    public static boolean contains(long peerId) {
        return peerId != 0 && ids().contains(peerId);
    }

    public static void set(long peerId, boolean banned) {
        if (peerId == 0) {
            return;
        }
        HashSet<Long> set = new HashSet<>(ids());
        if (banned ? !set.add(peerId) : !set.remove(peerId)) {
            return;
        }
        ArrayList<Long> sorted = new ArrayList<>(set);
        Collections.sort(sorted);
        MZGramConfig.setShadowBanList(TextUtils.join(",", sorted));
    }

    /** True while anything can be hidden; checked first so that the chat stays fast. */
    public static boolean isActive() {
        return MZGramConfig.hideBlockedInGroups || MZGramConfig.shadowBan && !ids().isEmpty();
    }

    /** Whether messages of this sender are hidden; never for the own account. */
    public static boolean isHiddenSender(int account, long peerId) {
        if (peerId == 0 || peerId == UserConfig.getInstance(account).getClientUserId()) {
            return false;
        }
        if (MZGramConfig.shadowBan && ids().contains(peerId)) {
            return true;
        }
        return MZGramConfig.hideBlockedInGroups && MessagesController.getInstance(account).blockePeers.indexOfKey(peerId) >= 0;
    }

    /** Whether this message is hidden: groups only, own messages and date rows never. */
    public static boolean isHidden(MessageObject message) {
        return isHidden(message, null);
    }

    /**
     * As isHidden(message), and also hidden by the message filters
     * (MZGramRegexFilters), which share this layer and check the album of
     * the message (group, may be null) as a whole.
     */
    public static boolean isHidden(MessageObject message, MessageObject.GroupedMessages group) {
        if (MZGramRegexFilters.isFiltered(message, group)) {
            return true;
        }
        if (message == null || !isActive() || message.isDateObject || message.messageOwner == null || message.isOutOwner() || message.isSponsored()) {
            return false;
        }
        long dialogId = message.getDialogId();
        if (dialogId >= 0) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(message.currentAccount).getChat(-dialogId);
        if (chat == null || ChatObject.isChannelAndNotMegaGroup(chat)) {
            return false;
        }
        long fromId = message.getFromChatId();
        if (fromId != dialogId && isHiddenSender(message.currentAccount, fromId)) {
            return true;
        }
        if (message.replyMessageObject != null) {
            long replyFromId = message.replyMessageObject.getFromChatId();
            return replyFromId != dialogId && isHiddenSender(message.currentAccount, replyFromId);
        }
        return false;
    }

    /** The message menu offers the ban for senders other than oneself and the group itself. */
    public static boolean canBan(MessageObject message) {
        if (!MZGramConfig.shadowBan || message == null || message.isOutOwner() || message.isSponsored()) {
            return false;
        }
        long dialogId = message.getDialogId();
        long fromId = message.getFromChatId();
        if (dialogId >= 0 || fromId == 0 || fromId == dialogId || fromId == UserConfig.getInstance(message.currentAccount).getClientUserId()) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(message.currentAccount).getChat(-dialogId);
        return chat != null && !ChatObject.isChannelAndNotMegaGroup(chat);
    }
}
