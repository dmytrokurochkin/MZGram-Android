/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Unlimited local folders: ordinary DialogFilters with a negative id that
 * live only in this device's dialog_filter table and are never sent to Telegram
 * servers.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesController.DialogFilter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class MZGramLocalFolders {

    private MZGramLocalFolders() {
    }

    public static boolean isLocal(DialogFilter filter) {
        return filter != null && filter.id < 0;
    }

    /**
     * Pin cap on a folder tab, in the unit DialogsActivity compares against.
     * Local folders have no chat limit, only the pin limit.
     */
    public static int maxPinned(MessagesController mc, boolean premium, DialogFilter filter, int pinnedCount) {
        if (isLocal(filter)) {
            return premium ? mc.maxFolderPinnedDialogsCountPremium : mc.maxFolderPinnedDialogsCountDefault;
        }
        final int chatsLimit = premium ? mc.dialogFiltersChatsLimitPremium : mc.dialogFiltersChatsLimitDefault;
        return chatsLimit - filter.alwaysShow.size() + pinnedCount;
    }

    /**
     * Whether the server reconciliation must leave this folder alone: local
     * folders always, and the "All chats" entry while any of them exists.
     */
    public static boolean keepOnTrim(DialogFilter filter, List<DialogFilter> all) {
        return isLocal(filter) || (filter != null && filter.isDefault() && hasLocal(all));
    }

    public static boolean hasLocal(List<DialogFilter> filters) {
        if (filters == null) {
            return false;
        }
        for (int a = 0, N = filters.size(); a < N; a++) {
            if (isLocal(filters.get(a))) {
                return true;
            }
        }
        return false;
    }

    /** Creates the "All chats" entry when missing, so a first local folder gets a tab strip. */
    public static void ensureDefaultFilter(AccountInstance accountInstance) {
        final MessagesController messagesController = accountInstance.getMessagesController();
        if (messagesController.dialogFiltersById.get(0) != null) {
            return;
        }
        final DialogFilter filter = new DialogFilter();
        filter.id = 0;
        filter.name = "ALL_CHATS";
        filter.color = -1;
        messagesController.dialogFilters.add(0, filter);
        messagesController.dialogFiltersById.put(0, filter);
        accountInstance.getMessagesStorage().saveDialogFilter(filter, false, false);
        accountInstance.getMessagesStorage().saveDialogFiltersOrder();
    }

    /**
     * A free id of the requested kind: first free 2, 3, ... for server folders,
     * or a random negative integer (< -1) for local folders.
     */
    public static int newId(MessagesController messagesController, boolean local) {
        if (!local) {
            int id = 2;
            while (messagesController.dialogFiltersById.get(id) != null) {
                id++;
            }
            return id;
        }
        int id;
        do {
            id = -2 - Utilities.random.nextInt(Integer.MAX_VALUE - 1);
        } while (messagesController.dialogFiltersById.get(id) != null);
        return id;
    }

    /** Moves an existing folder between local and server id ranges. */
    public static void move(AccountInstance accountInstance, DialogFilter filter, boolean local) {
        final MessagesController messagesController = accountInstance.getMessagesController();
        final int oldId = filter.id;
        filter.id = newId(messagesController, local);
        messagesController.dialogFiltersById.remove(oldId);
        messagesController.dialogFiltersById.put(filter.id, filter);
        final DialogFilter stale = new DialogFilter();
        stale.id = oldId;
        accountInstance.getMessagesStorage().deleteDialogFilter(stale);
        if (oldId > 0) {
            final TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
            req.id = oldId;
            accountInstance.getConnectionsManager().sendRequest(req, null);
        }
        if (local) {
            ensureDefaultFilter(accountInstance);
        }
        accountInstance.getMessagesStorage().saveDialogFiltersOrder();
        messagesController.lockFiltersInternal();
    }

    /** Whether the account can take no more server folders. */
    public static boolean serverFull(AccountInstance accountInstance) {
        final MessagesController messagesController = accountInstance.getMessagesController();
        final int count = remoteCount(messagesController.getDialogFilters());
        return (count - 1 >= messagesController.dialogFiltersLimitDefault && !accountInstance.getUserConfig().isPremium())
                || count >= messagesController.dialogFiltersLimitPremium;
    }

    public static int remoteCount(List<DialogFilter> filters) {
        if (filters == null) {
            return 0;
        }
        int count = 0;
        for (int a = 0, N = filters.size(); a < N; a++) {
            if (!isLocal(filters.get(a))) {
                count++;
            }
        }
        return count;
    }

    public static ArrayList<DialogFilter> remote(List<DialogFilter> filters) {
        return partition(filters, false);
    }

    public static ArrayList<DialogFilter> local(List<DialogFilter> filters) {
        return partition(filters, true);
    }

    private static ArrayList<DialogFilter> partition(List<DialogFilter> filters, boolean local) {
        final ArrayList<DialogFilter> out = new ArrayList<>();
        if (filters == null) {
            return out;
        }
        for (int a = 0, N = filters.size(); a < N; a++) {
            if (isLocal(filters.get(a)) == local) {
                out.add(filters.get(a));
            }
        }
        return out;
    }

    /**
     * Drops negative folder IDs before going on the wire. Returns true if request
     * was intercepted and must not be sent.
     */
    public static boolean dropIfLocal(TLObject request, RequestDelegate callback) {
        if (request instanceof TLRPC.TL_messages_updateDialogFiltersOrder) {
            ArrayList<Integer> order = ((TLRPC.TL_messages_updateDialogFiltersOrder) request).order;
            for (int a = order.size() - 1; a >= 0; a--) {
                if (order.get(a) < 0) {
                    order.remove(a);
                }
            }
            return false;
        }
        if (!(request instanceof TLRPC.TL_messages_updateDialogFilter) || ((TLRPC.TL_messages_updateDialogFilter) request).id >= 0) {
            return false;
        }
        if (callback != null) {
            callback.run(null, null);
        }
        return true;
    }

    /**
     * Applies server-side folder order to a list that may also hold local folders.
     */
    public static boolean mergeRemoteOrder(ArrayList<DialogFilter> filters, ArrayList<Integer> serverOrder) {
        if (filters == null || serverOrder == null) {
            return false;
        }
        final HashMap<Integer, Integer> position = new HashMap<>();
        for (int a = 0, N = serverOrder.size(); a < N; a++) {
            position.put(serverOrder.get(a), a);
        }
        final DialogFilter[] ordered = new DialogFilter[serverOrder.size()];
        for (int a = 0, N = filters.size(); a < N; a++) {
            final Integer slot = position.get(filters.get(a).id);
            if (slot != null && slot < ordered.length) {
                ordered[slot] = filters.get(a);
            }
        }
        int next = 0;
        for (int a = 0, N = filters.size(); a < N; a++) {
            if (position.containsKey(filters.get(a).id)) {
                while (next < ordered.length && ordered[next] == null) {
                    next++;
                }
                if (next < ordered.length) {
                    filters.set(a, ordered[next++]);
                }
            }
        }
        boolean changed = false;
        for (int a = 0, N = filters.size(); a < N; a++) {
            if (filters.get(a).order != a) {
                filters.get(a).order = a;
                changed = true;
            }
        }
        return changed;
    }
}
