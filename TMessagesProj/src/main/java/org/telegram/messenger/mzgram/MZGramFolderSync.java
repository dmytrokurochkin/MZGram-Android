/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Syncs local folders across devices via a JSON file in Saved Messages.
 *
 * Based on Mercurygram MgFolderSync (GPL-2.0+); written anew for MZGram.
 */

package org.telegram.messenger.mzgram;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesController.DialogFilter;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MZGramFolderSync implements NotificationCenter.NotificationCenterDelegate {

    public static final String FILE_NAME = "mzgram-folders.json";
    public static final String LEGACY_FILE_NAME = "mercurygram-folders.json";
    public static final String CAPTION_TAG = "#mzgram_folders";
    public static final String LEGACY_CAPTION_TAG = "#mercurygram_folders";
    private static final int FORMAT_VERSION = 1;
    private static final int PUSH_DELAY_MS = 3000;
    private static final long MAX_BLOB_BYTES = 4L * 1024 * 1024;
    private static final int MAX_EMOTICON_LENGTH = 32;
    private static final int MAX_TITLE_ENTITIES = 32;
    private static final long CHANNEL_MARKED_BASE = -1000000000000L;

    private static final MZGramFolderSync[] instances = new MZGramFolderSync[UserConfig.MAX_ACCOUNT_COUNT];

    public static void startAll() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (instances[a] == null) {
                    instances[a] = new MZGramFolderSync(a);
                }
            }
        });
    }

    private final int account;
    private boolean started;
    private boolean searching;
    private boolean pushing;
    private TLRPC.Message message;
    private String lastPushed;
    private String pushedBefore;
    private int updatedBefore;
    private String pendingAttachName;
    private final Runnable pushRunnable = this::push;
    private final Runnable pushTimeout = () -> pushing = false;

    private MZGramFolderSync(int account) {
        this.account = account;
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.dialogFiltersUpdated);
        center.addObserver(this, NotificationCenter.didReceiveNewMessages);
        center.addObserver(this, NotificationCenter.replaceMessagesObjects);
        center.addObserver(this, NotificationCenter.messageReceivedByServer);
        center.addObserver(this, NotificationCenter.messageSendError);
        center.addObserver(this, NotificationCenter.messagesDeleted);
        center.addObserver(this, NotificationCenter.fileLoaded);
        center.addObserver(this, NotificationCenter.fileLoadFailed);
        center.addObserver(this, NotificationCenter.appDidLogout);
        if (ai().getUserConfig().isClientActivated() && ai().getMessagesController().dialogFiltersLoaded) {
            find();
        }
    }

    private AccountInstance ai() {
        return AccountInstance.getInstance(account);
    }

    private long selfId() {
        return ai().getUserConfig().getClientUserId();
    }

    private MZGramAccountPrefs prefs() {
        return MZGramAccountPrefs.of("folders", account);
    }

    private int getFolderSyncUpdated() {
        return prefs().getInt("sync_updated", 0);
    }

    private void setFolderSyncUpdated(int updated) {
        prefs().putInt("sync_updated", updated);
    }

    private String getFolderEmoticons() {
        return prefs().getString("emoticons", "{}");
    }

    private void setFolderEmoticons(String emoticons) {
        prefs().putString("emoticons", emoticons);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, int acc, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            started = searching = pushing = false;
            message = null;
            lastPushed = null;
            pendingAttachName = null;
            AndroidUtilities.cancelRunOnUIThread(pushRunnable);
            AndroidUtilities.cancelRunOnUIThread(pushTimeout);
            return;
        }
        if (!ai().getUserConfig().isClientActivated()) {
            return;
        }
        if (id == NotificationCenter.dialogFiltersUpdated) {
            if (!started) {
                find();
            } else {
                schedulePush();
            }
        } else if (id == NotificationCenter.didReceiveNewMessages || id == NotificationCenter.replaceMessagesObjects) {
            if ((long) args[0] != selfId()) {
                return;
            }
            for (MessageObject obj : (List<MessageObject>) args[1]) {
                if (isCloudDocument(obj.messageOwner) && !obj.isSending() && !obj.isEditing() && !obj.isSendError()) {
                    onCloudMessage(obj.messageOwner);
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            TLRPC.Message msg = (TLRPC.Message) args[2];
            if ((long) args[3] == selfId() && isCloudDocument(msg)) {
                onCloudMessage(msg);
            }
        } else if (id == NotificationCenter.messageSendError) {
            if (pushing && (message == null || (Integer) args[0] == message.id)) {
                rollback();
                message = null;
                started = false;
            }
            pushing = false;
        } else if (id == NotificationCenter.messagesDeleted) {
            if (message != null && (Long) args[1] == 0L && !(Boolean) args[2]
                    && ((List<Integer>) args[0]).contains(message.id)) {
                message = null;
                started = false;
                find();
            }
        } else if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
            if (args.length > 0 && args[0].equals(pendingAttachName)) {
                pendingAttachName = null;
                if (id == NotificationCenter.fileLoaded) {
                    pull();
                }
            }
        }
    }

    private static boolean isCloudDocument(TLRPC.Message msg) {
        if (msg == null) {
            return false;
        }
        String fileName = FileLoader.getDocumentFileName(MessageObject.getDocument(msg));
        return FILE_NAME.equals(fileName) || LEGACY_FILE_NAME.equals(fileName);
    }

    private void onCloudMessage(TLRPC.Message msg) {
        pushing = false;
        AndroidUtilities.cancelRunOnUIThread(pushTimeout);
        if (message == null || msg.id >= message.id) {
            message = msg;
            pull();
        }
    }

    private void find() {
        if (searching) {
            return;
        }
        started = searching = true;
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.q = CAPTION_TAG;
        req.filter = new TLRPC.TL_inputMessagesFilterDocument();
        req.limit = 5;
        ai().getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            searching = false;
            TLRPC.Message found = null;
            if (response instanceof TLRPC.messages_Messages) {
                for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                    if (isCloudDocument(m) && (found == null || m.id > found.id)) {
                        found = m;
                    }
                }
            }
            if (found != null) {
                onCloudMessage(found);
            } else if (error == null) {
                lastPushed = null;
                schedulePush();
            } else {
                started = false;
            }
        }));
    }

    private void pull() {
        final TLRPC.Message msg = message;
        final TLRPC.Document document = MessageObject.getDocument(msg);
        if (document == null || document.size > MAX_BLOB_BYTES) {
            return;
        }
        final File file = ai().getFileLoader().getPathToAttach(document, true);
        if (!file.exists()) {
            final String attachName = FileLoader.getAttachFileName(document);
            if (attachName.equals(pendingAttachName)) {
                return;
            }
            pendingAttachName = attachName;
            ai().getFileLoader().loadFile(document, msg, FileLoader.PRIORITY_NORMAL_UP, 1);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            final String json = AndroidUtilities.readRes(file);
            final Blob blob = json == null ? null : Blob.parse(json);
            if (blob != null) {
                AndroidUtilities.runOnUIThread(() -> apply(blob));
            }
        });
    }

    private static final class Blob {
        final ArrayList<DialogFilter> folders = new ArrayList<>();
        final JSONObject emoticons = new JSONObject();
        final ArrayList<Long> chatIds = new ArrayList<>();
        int updated;

        static Blob parse(String json) {
            final Blob blob = new Blob();
            try {
                final JSONObject root = new JSONObject(json);
                if (root.optInt("version") != FORMAT_VERSION) {
                    return null;
                }
                blob.updated = root.optInt("updated");
                final JSONArray folders = root.getJSONArray("folders");
                for (int i = 0; i < folders.length(); i++) {
                    final JSONObject object = folders.getJSONObject(i);
                    final DialogFilter folder = fromJson(object, blob.chatIds);
                    final String emoticon = object.optString("emoticon", "");
                    if (!emoticon.isEmpty() && emoticon.length() <= MAX_EMOTICON_LENGTH) {
                        blob.emoticons.put(String.valueOf(folder.id), emoticon);
                    }
                    blob.folders.add(folder);
                }
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
            return blob;
        }
    }

    private void apply(Blob blob) {
        final ArrayList<DialogFilter> cloud = blob.folders;
        final int updated = blob.updated;
        final int currentUpdated = getFolderSyncUpdated();
        if (updated > currentUpdated) {
            final MessagesController mc = ai().getMessagesController();
            final boolean firstSync = currentUpdated == 0;
            for (DialogFilter old : MZGramLocalFolders.local(mc.getDialogFilters())) {
                boolean keep = firstSync;
                for (DialogFilter f : cloud) {
                    keep |= f.id == old.id;
                }
                if (!keep) {
                    mc.removeFilter(old);
                    ai().getMessagesStorage().deleteDialogFilter(old);
                }
            }
            if (!cloud.isEmpty()) {
                MZGramLocalFolders.ensureDefaultFilter(ai());
            }
            for (DialogFilter f : cloud) {
                final DialogFilter existing = mc.dialogFiltersById.get(f.id);
                if (existing == null) {
                    mc.addFilter(f, false);
                    ai().getMessagesStorage().saveDialogFilter(f, false, true);
                } else {
                    existing.name = f.name;
                    existing.entities = f.entities;
                    existing.title_noanimate = f.title_noanimate;
                    existing.flags = f.flags;
                    existing.color = f.color;
                    carryEncrypted(existing.alwaysShow, f.alwaysShow);
                    carryEncrypted(existing.neverShow, f.neverShow);
                    for (int i = 0; i < existing.pinnedDialogs.size(); i++) {
                        final long did = existing.pinnedDialogs.keyAt(i);
                        if (DialogObject.isEncryptedDialog(did)) {
                            f.pinnedDialogs.put(did, f.pinnedDialogs.size());
                        }
                    }
                    existing.alwaysShow = f.alwaysShow;
                    existing.neverShow = f.neverShow;
                    existing.pinnedDialogs = f.pinnedDialogs;
                    existing.pendingUnreadCount = -1;
                    mc.onFilterUpdate(existing);
                    ai().getMessagesStorage().saveDialogFilter(existing, false, true);
                }
            }
            setFolderSyncUpdated(updated);
            setFolderEmoticons(blob.emoticons.toString());
            lastPushed = syncKey(mc, cloud, blob.emoticons);
            mc.sortDialogs(null);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload, true);
        } else if (updated == currentUpdated) {
            lastPushed = syncKey(ai().getMessagesController(), cloud, blob.emoticons);
        }

        for (long id : blob.chatIds) {
            if (ChatObject.isChannel(ai().getMessagesController().getChat(-id))) {
                lastPushed = null;
                break;
            }
        }
        schedulePush();
    }

    private void schedulePush() {
        AndroidUtilities.cancelRunOnUIThread(pushRunnable);
        AndroidUtilities.runOnUIThread(pushRunnable, PUSH_DELAY_MS);
    }

    private void push() {
        if (!started || !ai().getUserConfig().isClientActivated()) {
            return;
        }
        if (searching) {
            return;
        }
        if (pushing) {
            schedulePush();
            return;
        }
        final int currentUpdated = getFolderSyncUpdated();
        final ArrayList<DialogFilter> folders = MZGramLocalFolders.local(ai().getMessagesController().getDialogFilters());
        if (folders.isEmpty() && currentUpdated == 0) {
            return;
        }
        final JSONObject emoticons = emoticons(folders);
        final MessagesController mc = ai().getMessagesController();
        final String canonical = syncKey(mc, folders, emoticons);
        if (canonical.equals(lastPushed)) {
            return;
        }
        final int updated = Math.max(ai().getConnectionsManager().getCurrentTime(), currentUpdated + 1);
        final String json = toJson(mc, folders, updated, emoticons).toString();
        final File dir = new File(new File(AndroidUtilities.getCacheDir(), "mzg_folders_" + account), String.valueOf(updated));
        final File file = new File(dir, FILE_NAME);
        final String caption = caption(folders);
        MessageObject editing = null;
        if (message != null) {
            editing = new MessageObject(account, message, false, false);
            editing.editingMessage = caption;
            editing.editingMessageEntities = new ArrayList<>();
        }
        final MessageObject editingFinal = editing;
        pushing = true;
        pushedBefore = lastPushed;
        updatedBefore = currentUpdated;
        lastPushed = canonical;
        setFolderSyncUpdated(updated);
        setFolderEmoticons(emoticons.toString());
        Utilities.globalQueue.postRunnable(() -> {
            try {
                dir.mkdirs();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    rollback();
                    pushing = false;
                });
                return;
            }
            prune(dir);
            AndroidUtilities.runOnUIThread(() -> {
                AndroidUtilities.runOnUIThread(pushTimeout, 60_000);
                SendMessagesHelper.prepareSendingDocument(ai(), file.getAbsolutePath(), file.getAbsolutePath(), null, caption, "application/json", selfId(), null, null, null, null, editingFinal, false, 0, null, null, false);
            });
        });
    }

    private void rollback() {
        lastPushed = pushedBefore;
        setFolderSyncUpdated(updatedBefore);
    }

    private static void prune(File keep) {
        if (keep == null || keep.getParentFile() == null) {
            return;
        }
        final File[] dirs = keep.getParentFile().listFiles();
        if (dirs == null) {
            return;
        }
        for (File d : dirs) {
            if (!d.equals(keep)) {
                new File(d, FILE_NAME).delete();
                d.delete();
            }
        }
    }

    private static void carryEncrypted(List<Long> from, List<Long> to) {
        for (Long did : from) {
            if (DialogObject.isEncryptedDialog(did) && !to.contains(did)) {
                to.add(did);
            }
        }
    }

    private static ArrayList<Long> syncable(MessagesController mc, List<Long> dids) {
        final ArrayList<Long> out = new ArrayList<>(dids.size());
        for (Long did : dids) {
            if (!DialogObject.isEncryptedDialog(did)) {
                out.add(toMarked(mc, did));
            }
        }
        return out;
    }

    private static long toMarked(MessagesController mc, long did) {
        return did < 0 && ChatObject.isChannel(mc.getChat(-did)) ? CHANNEL_MARKED_BASE + did : did;
    }

    private static long fromMarked(long marked, List<Long> chatIds) {
        if (marked <= CHANNEL_MARKED_BASE) {
            return marked - CHANNEL_MARKED_BASE;
        }
        if (marked < 0) {
            chatIds.add(marked);
        }
        return marked;
    }

    private static String caption(List<DialogFilter> folders) {
        final StringBuilder sb = new StringBuilder(CAPTION_TAG);
        for (DialogFilter f : folders) {
            final String line = "\n📁 " + f.name + " (" + f.alwaysShow.size() + " chats)";
            if (sb.length() + line.length() > 1024) {
                break;
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private static final String[] FLAG_NAMES = {"contacts", "non_contacts", "groups", "broadcasts", "bots", "exclude_muted", "exclude_read", "exclude_archived"};
    private static final int[] FLAG_BITS = {
        MessagesController.DIALOG_FILTER_FLAG_CONTACTS, MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS,
        MessagesController.DIALOG_FILTER_FLAG_GROUPS, MessagesController.DIALOG_FILTER_FLAG_CHANNELS,
        MessagesController.DIALOG_FILTER_FLAG_BOTS, MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED,
        MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ, MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
    };

    private static String syncKey(MessagesController mc, List<DialogFilter> folders, JSONObject emoticons) {
        return root(foldersJson(mc, sortedById(folders), emoticons), 0).toString();
    }

    private JSONObject emoticons(List<DialogFilter> folders) {
        final JSONObject result = new JSONObject();
        try {
            final JSONObject saved = new JSONObject(getFolderEmoticons());
            for (DialogFilter f : folders) {
                final String emoticon = saved.optString(String.valueOf(f.id), "");
                if (!emoticon.isEmpty()) {
                    result.put(String.valueOf(f.id), emoticon);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static ArrayList<DialogFilter> sortedById(List<DialogFilter> folders) {
        final ArrayList<DialogFilter> sorted = new ArrayList<>(folders);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.id, a.id));
        return sorted;
    }

    public static JSONObject toJson(MessagesController mc, List<DialogFilter> folders, int updated, JSONObject emoticons) {
        return root(foldersJson(mc, folders, emoticons), updated);
    }

    private static JSONObject root(JSONArray folders, int updated) {
        try {
            final JSONObject root = new JSONObject();
            root.put("format", "mzgram-folders");
            root.put("version", FORMAT_VERSION);
            root.put("updated", updated);
            root.put("folders", folders);
            return root;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONArray foldersJson(MessagesController mc, List<DialogFilter> filters, JSONObject emoticons) {
        try {
            final JSONArray folders = new JSONArray();
            for (DialogFilter f : filters) {
                final JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("title", f.name == null ? "" : f.name);
                final JSONArray entities = titleEntitiesJson(f);
                if (entities.length() > 0) {
                    o.put("title_entities", entities);
                }
                if (f.title_noanimate) {
                    o.put("title_noanimate", true);
                }
                final String emoticon = emoticons.optString(String.valueOf(f.id), "");
                if (!emoticon.isEmpty()) {
                    o.put("emoticon", emoticon);
                }
                o.put("color", f.color);
                final JSONObject flags = new JSONObject();
                for (int i = 0; i < FLAG_NAMES.length; i++) {
                    flags.put(FLAG_NAMES[i], (f.flags & FLAG_BITS[i]) != 0);
                }
                o.put("flags", flags);
                o.put("include", new JSONArray(syncable(mc, f.alwaysShow)));
                o.put("exclude", new JSONArray(syncable(mc, f.neverShow)));
                final ArrayList<Long> pinned = new ArrayList<>();
                for (int i = 0; i < f.pinnedDialogs.size(); i++) {
                    if (!DialogObject.isEncryptedDialog(f.pinnedDialogs.keyAt(i))) {
                        pinned.add(f.pinnedDialogs.keyAt(i));
                    }
                }
                Collections.sort(pinned, (a, b) -> Integer.compare(f.pinnedDialogs.get(a), f.pinnedDialogs.get(b)));
                o.put("pinned", new JSONArray(syncable(mc, pinned)));
                folders.put(o);
            }
            return folders;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONArray titleEntitiesJson(DialogFilter f) throws Exception {
        final JSONArray result = new JSONArray();
        for (TLRPC.MessageEntity entity : f.entities) {
            if (!(entity instanceof TLRPC.TL_messageEntityCustomEmoji)) {
                continue;
            }
            final JSONObject o = new JSONObject();
            o.put("offset", entity.offset);
            o.put("length", entity.length);
            o.put("document_id", String.valueOf(((TLRPC.TL_messageEntityCustomEmoji) entity).document_id));
            result.put(o);
        }
        return result;
    }

    private static void readTitleEntities(DialogFilter f, JSONArray entities) {
        for (int i = 0; entities != null && i < entities.length() && f.entities.size() < MAX_TITLE_ENTITIES; i++) {
            final JSONObject o = entities.optJSONObject(i);
            if (o == null) {
                continue;
            }
            final int offset = o.optInt("offset");
            final int length = o.optInt("length");
            final long documentId = Long.parseLong(o.optString("document_id", "0"));
            if (offset >= 0 && length > 0 && offset + length <= f.name.length() && documentId != 0) {
                final TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                entity.offset = offset;
                entity.length = length;
                entity.document_id = documentId;
                f.entities.add(entity);
            }
        }
    }

    private static DialogFilter fromJson(JSONObject o, List<Long> chatIds) throws Exception {
        final DialogFilter f = new DialogFilter();
        f.id = o.getInt("id");
        f.name = o.optString("title", "");
        f.title_noanimate = o.optBoolean("title_noanimate", false);
        readTitleEntities(f, o.optJSONArray("title_entities"));
        f.color = o.optInt("color", -1);
        final JSONObject flags = o.optJSONObject("flags");
        if (flags != null) {
            for (int i = 0; i < FLAG_NAMES.length; i++) {
                if (flags.optBoolean(FLAG_NAMES[i], false)) {
                    f.flags |= FLAG_BITS[i];
                }
            }
        }
        final JSONArray include = o.optJSONArray("include");
        for (int i = 0; include != null && i < include.length(); i++) {
            f.alwaysShow.add(fromMarked(include.getLong(i), chatIds));
        }
        final JSONArray exclude = o.optJSONArray("exclude");
        for (int i = 0; exclude != null && i < exclude.length(); i++) {
            f.neverShow.add(fromMarked(exclude.getLong(i), chatIds));
        }
        final JSONArray pinned = o.optJSONArray("pinned");
        for (int i = 0; pinned != null && i < pinned.length(); i++) {
            f.pinnedDialogs.put(fromMarked(pinned.getLong(i), chatIds), i);
        }
        return f;
    }
}
