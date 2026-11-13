/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Feed: the posts of the channels you follow, merged by time into one list
 * (MZGramFeedActivity). The channels are the broadcast channels of the chat
 * list (with the archive's when "Channels from the archive" is on), less the
 * ones unchecked in the feed's channel list, or only those of one chat
 * folder (the feed's category). The posts are the last ones of every channel
 * (Settings > MZGram > Feed > Posts per channel), asked from Telegram's
 * server with messages.getHistory, a few channels at a time; nothing is
 * stored. Reading a post in the feed does not mark it read; "Mark all as
 * read" reads every channel of the feed, as opening it would (ghost mode
 * applies). Idea from exteraless (GPL-3.0); written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.collection.LongSparseArray;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class MZGramFeed {

    public static final int[] POSTS_PER_CHANNEL = {10, 20, 50};
    // At most this many channels are asked, and this many posts are shown
    public static final int MAX_CHANNELS = 200;
    public static final int MAX_POSTS = 1000;
    private static final int PARALLEL = 3;

    private static final String PREFS = "mzgram_feed";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_CATEGORY = "category";

    public interface Callback {
        void onLoaded(ArrayList<MessageObject> posts, int channels);
    }

    private MZGramFeed() {
    }

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS + account, Context.MODE_PRIVATE);
    }

    /** Channels unchecked in the feed's channel list. */
    public static Set<Long> hidden(int account) {
        HashSet<Long> result = new HashSet<>();
        for (String id : prefs(account).getStringSet(KEY_HIDDEN, Collections.emptySet())) {
            try {
                result.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static void setHidden(int account, long dialogId, boolean hide) {
        HashSet<String> ids = new HashSet<>(prefs(account).getStringSet(KEY_HIDDEN, Collections.emptySet()));
        if (hide) {
            ids.add(Long.toString(dialogId));
        } else {
            ids.remove(Long.toString(dialogId));
        }
        prefs(account).edit().putStringSet(KEY_HIDDEN, ids).apply();
    }

    /** The chat folder the feed shows, 0 for all channels. */
    public static int category(int account) {
        return prefs(account).getInt(KEY_CATEGORY, 0);
    }

    public static void setCategory(int account, int filterId) {
        prefs(account).edit().putInt(KEY_CATEGORY, filterId).apply();
    }

    private static MessagesController.DialogFilter folder(int account) {
        int id = category(account);
        if (id == 0) {
            return null;
        }
        for (MessagesController.DialogFilter filter : MessagesController.getInstance(account).getDialogFilters()) {
            if (filter.id == id && !filter.isDefault()) {
                return filter;
            }
        }
        return null;
    }

    /** Every channel of the chat list that may be in the feed, checked or not, in the order of the list. */
    public static ArrayList<TLRPC.Dialog> allChannels(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        for (TLRPC.Dialog dialog : new ArrayList<>(controller.getAllDialogs())) {
            if (!DialogObject.isChatDialog(dialog.id) || dialog.folder_id == 1 && !MZGramConfig.feedIncludeArchived) {
                continue;
            }
            TLRPC.Chat chat = controller.getChat(-dialog.id);
            if (chat != null && ChatObject.isChannelAndNotMegaGroup(chat) && !ChatObject.isNotInChat(chat)) {
                result.add(dialog);
            }
        }
        return result;
    }

    /** The channels of the feed: checked, and in the feed's category if one is chosen. */
    public static ArrayList<TLRPC.Dialog> channels(int account) {
        Set<Long> hidden = hidden(account);
        MessagesController.DialogFilter folder = folder(account);
        AccountInstance instance = AccountInstance.getInstance(account);
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        for (TLRPC.Dialog dialog : allChannels(account)) {
            if (hidden.contains(dialog.id) || folder != null && !folder.includesDialog(instance, dialog.id, dialog)) {
                continue;
            }
            result.add(dialog);
            if (result.size() >= MAX_CHANNELS) {
                break;
            }
        }
        return result;
    }

    /** Unread posts of the feed's channels. */
    public static int unreadCount(int account) {
        int count = 0;
        for (TLRPC.Dialog dialog : channels(account)) {
            count += Math.max(0, dialog.unread_count);
        }
        return count;
    }

    /** Marks every channel of the feed read, as opening it would. */
    public static void markAllRead(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        for (TLRPC.Dialog dialog : channels(account)) {
            if (dialog.unread_count > 0 || dialog.unread_mark) {
                controller.markDialogAsRead(dialog.id, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0);
            }
        }
    }

    public static int postsPerChannel() {
        for (int count : POSTS_PER_CHANNEL) {
            if (count == MZGramConfig.feedPostsPerChannel) {
                return count;
            }
        }
        return 20;
    }

    /** Asks the last posts of every channel of the feed; called back once, on the UI thread. */
    public static void load(int account, Callback callback) {
        ArrayList<TLRPC.Dialog> channels = channels(account);
        Loader loader = new Loader(account, channels, postsPerChannel(), callback);
        loader.start();
    }

    private static final class Loader {
        final int account;
        final ArrayList<TLRPC.Dialog> channels;
        final int limit;
        final Callback callback;
        final ArrayList<MessageObject> posts = new ArrayList<>();
        int next;
        int running;
        boolean done;

        Loader(int account, ArrayList<TLRPC.Dialog> channels, int limit, Callback callback) {
            this.account = account;
            this.channels = channels;
            this.limit = limit;
            this.callback = callback;
        }

        void start() {
            if (channels.isEmpty()) {
                finish();
                return;
            }
            for (int i = 0; i < PARALLEL; i++) {
                askNext();
            }
        }

        void askNext() {
            if (next >= channels.size()) {
                if (running == 0) {
                    finish();
                }
                return;
            }
            TLRPC.Dialog dialog = channels.get(next++);
            MessagesController controller = MessagesController.getInstance(account);
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = controller.getInputPeer(dialog.id);
            req.limit = limit;
            if (req.peer == null) {
                askNext();
                return;
            }
            running++;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                running--;
                if (response instanceof TLRPC.messages_Messages) {
                    add((TLRPC.messages_Messages) response);
                }
                askNext();
            }));
        }

        void add(TLRPC.messages_Messages res) {
            MessagesController controller = MessagesController.getInstance(account);
            controller.putUsers(res.users, false);
            controller.putChats(res.chats, false);
            LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
            for (TLRPC.User user : res.users) {
                users.put(user.id, user);
            }
            LongSparseArray<TLRPC.Chat> chats = new LongSparseArray<>();
            for (TLRPC.Chat chat : res.chats) {
                chats.put(chat.id, chat);
            }
            for (TLRPC.Message message : res.messages) {
                if (message instanceof TLRPC.TL_messageEmpty || message instanceof TLRPC.TL_messageService) {
                    continue;
                }
                MessageObject post = new MessageObject(account, message, users, chats, true, true);
                post.mzgramFeed = true;
                posts.add(post);
            }
        }

        void finish() {
            if (done) {
                return;
            }
            done = true;
            Collections.sort(posts, (a, b) -> {
                int byDate = Integer.compare(a.messageOwner.date, b.messageOwner.date);
                return byDate != 0 ? byDate : Integer.compare(a.getId(), b.getId());
            });
            while (posts.size() > MAX_POSTS) {
                posts.remove(0);
            }
            callback.onLoaded(posts, channels.size());
        }
    }
}
