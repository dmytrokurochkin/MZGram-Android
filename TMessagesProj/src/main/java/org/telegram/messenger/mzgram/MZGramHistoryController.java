/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Decides whether a deleted or edited message is worth keeping, and writes
 * the snapshot to MZGramHistoryDatabase. Unlike AyuGram4A, which saves for
 * every chat unless excluded, MZGram only saves for chats the user added to
 * the allowlist (MZGramConfig.isDialogTracked), matching the Desktop
 * anti-recall feature.
 *
 * Ported from AyuGram4A 7013145676d36d82ee13c02a89f72097b7490dcd
 * (messages/AyuMessagesController.java: onMessageDeleted, onMessageEdited,
 * the same-media comparison in onMessageEditedInner). AyuGram4A builds the
 * saved TLRPC.Message copy through its proprietary AyuMessageUtils (a
 * private submodule, not part of the public source); MZGram maps the
 * message fields itself instead.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MZGramHistoryController {

    private static volatile MZGramHistoryController instance;

    public static MZGramHistoryController getInstance() {
        if (instance == null) {
            synchronized (MZGramHistoryController.class) {
                if (instance == null) {
                    instance = new MZGramHistoryController();
                }
            }
        }
        return instance;
    }

    private MZGramHistoryController() {
    }

    public static boolean isTracked(long dialogId) {
        return MZGramConfig.saveMessageHistory && MZGramConfig.isDialogTracked(dialogId);
    }

    // The archive keeps what OTHER people delete or edit. The account owner's
    // own messages (sent from this or another device, or posted to a channel
    // the owner runs) are never archived.
    public static boolean isOwnMessage(int accountId, TLRPC.Message message) {
        if (message.out) {
            return true;
        }
        long selfId = UserConfig.getInstance(accountId).getClientUserId();
        return message.from_id != null && MessageObject.getPeerId(message.from_id) == selfId;
    }

    // ---- deleted messages ----

    public void onMessageDeleted(int accountId, long dialogId, TLRPC.Message message) {
        // MZGram: unconditional entry log -- isTracked()/message==null below used
        // to fail completely silently, so a misconfigured allowlist and "this
        // hook was never even reached" were indistinguishable from logcat.
        // Diagnostic-only, no behavior change.
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController.onMessageDeleted: called for dialog " + dialogId
                    + ", messageId=" + (message != null ? message.id : "null")
                    + ", saveMessageHistory=" + MZGramConfig.saveMessageHistory
                    + ", isDialogTracked=" + MZGramConfig.isDialogTracked(dialogId));
        }
        if (message == null || !isTracked(dialogId)) {
            return;
        }
        if (isOwnMessage(accountId, message)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("MZGramHistoryController: message " + message.id + " in dialog " + dialogId + " is the owner's own, not archived");
            }
            return;
        }
        try {
            onMessageDeletedInner(accountId, dialogId, message);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.onMessageDeleted", e);
        }
    }

    // MZGram: own fix. Used to pre-check MZGramHistoryDatabase.existsDeleted()
    // in Java and return early before ever calling insert() -- a separate
    // read that could only ever be as reliable as that one extra query, with
    // no way to tell from outside this class whether a "not archived" report
    // was ever wrong. insert() itself now uses INSERT OR IGNORE against a
    // partial UNIQUE index (MZGramHistoryDatabase.onCreate), so a genuine
    // duplicate is a no-op enforced atomically by SQLite (rowId == -1)
    // instead of trusted to a prior Java-side check. See
    // docs/07-nekogram-features-plan.md for the investigation this replaced.
    private void onMessageDeletedInner(int accountId, long dialogId, TLRPC.Message message) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();

        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        long rowId = db.insert(buildRow(accountId, accountUserId, dialogId, message, MZGramHistoryMessage.KIND_DELETED));
        if (BuildVars.LOGS_ENABLED) {
            if (rowId == -1) {
                FileLog.d("MZGramHistoryController: message " + message.id + " in dialog " + dialogId + " already archived (INSERT OR IGNORE no-op)");
            } else {
                FileLog.d("MZGramHistoryController: archived deleted message " + message.id + " in dialog " + dialogId + ", rowId=" + rowId);
            }
        }
    }

    // ---- edited messages ----

    public void onMessageEdited(int accountId, long dialogId, TLRPC.Message oldMessage, TLRPC.Message newMessage) {
        if (oldMessage == null || newMessage == null || !isTracked(dialogId) || isOwnMessage(accountId, oldMessage)) {
            return;
        }
        try {
            onMessageEditedInner(accountId, dialogId, oldMessage, newMessage);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.onMessageEdited", e);
        }
    }

    private void onMessageEditedInner(int accountId, long dialogId, TLRPC.Message oldMessage, TLRPC.Message newMessage) {
        boolean sameText = TextUtils.equals(oldMessage.message, newMessage.message);
        if (sameText && sameMedia(oldMessage, newMessage)) {
            return; // nothing MZGram shows changed (e.g. only views/reactions were updated)
        }

        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        long rowId = MZGramHistoryDatabase.getInstance().insert(buildRow(accountId, accountUserId, dialogId, oldMessage, MZGramHistoryMessage.KIND_EDITED));
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController: archived edited message " + oldMessage.id + " in dialog " + dialogId + ", rowId=" + rowId);
        }
    }

    private boolean sameMedia(TLRPC.Message a, TLRPC.Message b) {
        if (a.media == null && b.media == null) {
            return true;
        }
        if (a.media == null || b.media == null) {
            return false;
        }
        if (a.media instanceof TLRPC.TL_messageMediaPhoto && b.media instanceof TLRPC.TL_messageMediaPhoto
                && a.media.photo != null && b.media.photo != null) {
            return a.media.photo.id == b.media.photo.id;
        }
        if (a.media instanceof TLRPC.TL_messageMediaDocument && b.media instanceof TLRPC.TL_messageMediaDocument
                && a.media.document != null && b.media.document != null) {
            return a.media.document.id == b.media.document.id;
        }
        return a.media.getClass() == b.media.getClass();
    }

    // ---- one-time-view media ----

    // MZGram: own hook, no AyuGram4A equivalent -- Desktop MZGram already
    // copies one-time media into its archive the same way (mzgram_archive).
    // Called right before the media is emptied locally (see ChatActivity's
    // sendSecretMediaDelete / doDeleteShowOnceTask), so the file is still on
    // disk.
    public void onOneTimeMediaViewed(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || !isTracked(dialogId) || isOwnMessage(accountId, message)) {
            return;
        }
        try {
            onOneTimeMediaViewedInner(accountId, dialogId, message);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.onOneTimeMediaViewed", e);
        }
    }

    private void onOneTimeMediaViewedInner(int accountId, long dialogId, TLRPC.Message message) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        if (db.hasKind(accountUserId, dialogId, message.id, MZGramHistoryMessage.KIND_VIEW_ONCE)) {
            return; // already archived
        }
        long rowId = db.insert(buildRow(accountId, accountUserId, dialogId, message, MZGramHistoryMessage.KIND_VIEW_ONCE, true));
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController: archived removed media of message " + message.id + " in dialog " + dialogId + ", rowId=" + rowId);
        }
    }

    // Called by MessagesStorage.emptyMessagesMedia right before it replaces a
    // message's media with an empty one and deletes the file: self-destructing
    // media whose timer ran out, and view-once media after viewing. The file
    // is still on disk here, so a copy goes into the archive. Kept even over
    // the size limit, like any one-time media.
    public void onMessageMediaRemoved(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || message.media == null || !isTracked(dialogId) || isOwnMessage(accountId, message)) {
            return;
        }
        try {
            onOneTimeMediaViewedInner(accountId, dialogId, message);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.onMessageMediaRemoved", e);
        }
    }

    // ---- shared row building / media capture ----

    private MZGramHistoryMessage buildRow(int accountId, long accountUserId, long dialogId, TLRPC.Message message, int kind) {
        return buildRow(accountId, accountUserId, dialogId, message, kind, false);
    }

    private MZGramHistoryMessage buildRow(int accountId, long accountUserId, long dialogId, TLRPC.Message message, int kind, boolean oneTimeMedia) {
        MZGramHistoryMessage row = new MZGramHistoryMessage();
        row.kind = kind;
        row.accountUserId = accountUserId;
        row.dialogId = dialogId;
        row.topicId = MessageObject.getTopicId(accountId, message, false);
        row.messageId = message.id;
        row.groupedId = (message.flags & 131072) != 0 ? message.grouped_id : 0;
        row.fromId = message.from_id != null ? MessageObject.getPeerId(message.from_id) : 0;
        row.date = message.date;
        row.editDate = message.edit_date;
        row.entityCreateDate = (int) (System.currentTimeMillis() / 1000);
        row.text = message.message;
        row.entities = serializeEntities(message);
        row.messageData = serializeMessage(message);
        copyMediaIfNeeded(accountId, accountUserId, dialogId, message, row, oneTimeMedia);
        return row;
    }

    private static byte[] serializeMessage(TLRPC.Message message) {
        try {
            SerializedData data = new SerializedData(message.getObjectSize());
            message.serializeToStream(data);
            byte[] bytes = data.toByteArray();
            data.cleanup();
            return bytes;
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.serializeMessage", e);
            return null;
        }
    }

    private static TLRPC.Message deserializeMessage(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            SerializedData data = new SerializedData(bytes);
            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
            data.cleanup();
            return message;
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.deserializeMessage", e);
            return null;
        }
    }

    private byte[] serializeEntities(TLRPC.Message message) {
        if (message.entities == null || message.entities.isEmpty()) {
            return null;
        }
        NativeByteBuffer buffer = null;
        try {
            int size = 4;
            for (int a = 0, count = message.entities.size(); a < count; a++) {
                size += message.entities.get(a).getObjectSize();
            }
            buffer = new NativeByteBuffer(size);
            buffer.writeInt32(message.entities.size());
            for (int a = 0, count = message.entities.size(); a < count; a++) {
                message.entities.get(a).serializeToStream(buffer);
            }
            byte[] bytes = new byte[size];
            buffer.buffer.position(0);
            buffer.buffer.get(bytes);
            return bytes;
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.serializeEntities", e);
            return null;
        } finally {
            if (buffer != null) {
                buffer.reuse();
            }
        }
    }

    private void copyMediaIfNeeded(int accountId, long accountUserId, long dialogId, TLRPC.Message message, MZGramHistoryMessage row, boolean oneTimeMedia) {
        if (message.media == null) {
            return;
        }

        File source = findLocalFile(accountId, message);
        if (source == null) {
            return; // not downloaded on this device -- nothing local to archive
        }

        int mediaType;
        boolean alwaysSave;
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            mediaType = MZGramHistoryMessage.MEDIA_PHOTO;
            alwaysSave = true;
        } else if (MessageObject.isStickerMessage(message) || MessageObject.isAnimatedStickerMessage(message)) {
            mediaType = MZGramHistoryMessage.MEDIA_STICKER;
            alwaysSave = true;
        } else if (MessageObject.isVoiceMessage(message) || MessageObject.isRoundVideoMessage(message)) {
            mediaType = MZGramHistoryMessage.MEDIA_FILE;
            alwaysSave = true;
        } else {
            mediaType = MZGramHistoryMessage.MEDIA_FILE;
            alwaysSave = false;
        }

        if (!alwaysSave && !oneTimeMedia) {
            int limitMb = MZGramConfig.historyMediaSizeLimitMb;
            if (limitMb > 0 && source.length() > (long) limitMb * 1024 * 1024) {
                return; // over the user's size limit -- keep the text row without media
            }
        }

        try {
            File destDir = MZGramHistoryDatabase.mediaDir(accountUserId, dialogId);
            File dest = new File(destDir, message.id + "_" + source.getName());
            if (!dest.exists()) {
                AndroidUtilities.copyFile(source, dest);
            }
            row.mediaPath = dest.getAbsolutePath();
            row.mediaType = mediaType;
            if (message.media.document != null) {
                row.mimeType = message.media.document.mime_type;
            }
            enforceMediaCap();
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.copyMediaIfNeeded", e);
        }
    }

    // Where the downloaded file of this message is on disk: the path the
    // message was sent from (attachPath), the regular download location, or
    // the cache directory (self-destructing media always lives there).
    private File findLocalFile(int accountId, TLRPC.Message message) {
        try {
            if (!TextUtils.isEmpty(message.attachPath)) {
                File f = new File(message.attachPath);
                if (f.exists()) {
                    return f;
                }
            }
            FileLoader loader = FileLoader.getInstance(accountId);
            File f = loader.getPathToMessage(message);
            if (f != null && f.exists()) {
                return f;
            }
            f = loader.getPathToMessage(message, true, true);
            if (f != null && f.exists()) {
                return f;
            }
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.findLocalFile", e);
        }
        return null;
    }

    // ---- total media size cap ----

    // Oldest-first eviction across the whole media folder (every account,
    // every tracked chat combined), so the archive's disk usage stays under
    // MZGramConfig.historyTotalMediaCapMb regardless of which chat is
    // currently growing. Text/entity rows are left alone -- only the media
    // file on disk is removed, the same way the per-file size limit above
    // already results in a text-only row.
    private void enforceMediaCap() {
        int capMb = MZGramConfig.historyTotalMediaCapMb;
        if (capMb <= 0) {
            return;
        }
        long capBytes = (long) capMb * 1024 * 1024;
        List<File> files = new ArrayList<>();
        long total = collectFiles(MZGramHistoryDatabase.mediaRoot(), files);
        if (total <= capBytes) {
            return;
        }
        Collections.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= capBytes) {
                break;
            }
            long len = f.length();
            if (f.delete()) {
                total -= len;
            }
        }
    }

    private long collectFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        long total = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                total += collectFiles(child, out);
            } else {
                out.add(child);
                total += child.length();
            }
        }
        return total;
    }

    // ---- keeping deleted messages in the chat ----

    // Messages the user removed themself in the last minute, by dialog and
    // id ("dialogId:messageId" -> when). The messagesDeleted notification
    // that follows the user's own Delete reaches an open chat right away;
    // keepsDeletedInChat must not keep those.
    private static final long LOCAL_DELETION_TTL_MS = 60_000;
    private final ConcurrentHashMap<String, Long> localDeletions = new ConcurrentHashMap<>();

    // MessagesController.deleteMessages: the user (or the client itself)
    // removes these messages locally. When the user asked the server to
    // delete them (not cacheOnly), any archived copy goes too, so a kept
    // deleted message the user deletes does not come back.
    public void onLocalDeletion(int accountId, long dialogId, ArrayList<Integer> mids, boolean removeArchived) {
        if (mids == null || mids.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Long>> it = localDeletions.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue() > LOCAL_DELETION_TTL_MS) {
                it.remove();
            }
        }
        for (int a = 0, N = mids.size(); a < N; a++) {
            localDeletions.put(dialogId + ":" + mids.get(a), now);
        }
        if (removeArchived && isTracked(dialogId)) {
            ArrayList<Integer> ids = new ArrayList<>(mids);
            long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
            MessagesStorage.getInstance(accountId).getStorageQueue().postRunnable(() -> {
                try {
                    MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
                    for (int a = 0, N = ids.size(); a < N; a++) {
                        db.delete(accountUserId, dialogId, ids.get(a));
                    }
                } catch (Exception e) {
                    FileLog.e("MZGramHistoryController.onLocalDeletion", e);
                }
            });
        }
    }

    // ChatActivity, when a message shown in an open chat is deleted: true
    // means keep it in the chat, marked as deleted, instead of removing it.
    // Kept: another person's message in a tracked chat that the user did
    // not remove themself.
    public boolean keepsDeletedInChat(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || message instanceof TLRPC.TL_messageService || !isTracked(dialogId) || isOwnMessage(accountId, message)) {
            return false;
        }
        Long when = localDeletions.get(dialogId + ":" + message.id);
        return when == null || System.currentTimeMillis() - when > LOCAL_DELETION_TTL_MS;
    }

    // MessagesController.processLoadedMessages, for a tracked chat: the list
    // of messages the chat gets back, with
    //   - other people's archived deleted messages that belong in the loaded
    //     range put back in their place, marked as deleted;
    //   - media removed from a message (self-destructing, view once) put
    //     back from the archived file, as ordinary media.
    // Returns the loaded list itself when there is nothing to add, otherwise
    // a new list; the loaded one and its messages are never changed (they
    // may still be on their way into messages_v2).
    //
    // An archived message is added only where it certainly belongs: between
    // the oldest and the newest loaded message, below them when the server
    // says the history starts there, above them when this is the newest
    // part of the chat. Anything else would make the chat think it has
    // loaded a range it has not.
    public ArrayList<TLRPC.Message> messagesForChat(int accountId, long dialogId, ArrayList<TLRPC.Message> loaded, int count, int maxId, int loadType, boolean isCache) {
        if (loaded == null || DialogObject.isEncryptedDialog(dialogId) || !isTracked(dialogId)) {
            return loaded;
        }
        try {
            return messagesForChatInner(accountId, dialogId, loaded, count, maxId, loadType, isCache);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.messagesForChat", e);
            return loaded;
        }
    }

    private static final int CHAT_HISTORY_LIMIT = 200;

    private ArrayList<TLRPC.Message> messagesForChatInner(int accountId, long dialogId, ArrayList<TLRPC.Message> loaded, int count, int maxId, int loadType, boolean isCache) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        ArrayList<TLRPC.Message> result = null;

        HashSet<Integer> ids = new HashSet<>();
        int minLoaded = Integer.MAX_VALUE;
        int maxLoaded = 0;
        for (int a = 0, N = loaded.size(); a < N; a++) {
            TLRPC.Message message = loaded.get(a);
            if (message == null || message.id <= 0) {
                continue;
            }
            ids.add(message.id);
            minLoaded = Math.min(minLoaded, message.id);
            maxLoaded = Math.max(maxLoaded, message.id);
            if (hasRemovedMedia(message)) {
                TLRPC.Message restored = withRestoredMedia(accountId, accountUserId, dialogId, message);
                if (restored != null) {
                    if (result == null) {
                        result = new ArrayList<>(loaded);
                    }
                    result.set(a, restored);
                }
            }
        }

        boolean fromNewest = loadType == 2 && maxId == 0;
        int lower;
        int upper;
        if (ids.isEmpty()) {
            if (isCache || !fromNewest && loadType != 0) {
                return result != null ? result : loaded;
            }
            // The server has nothing here: the chat is empty (from the newest
            // message), or nothing is older than max_id.
            lower = 1;
            upper = loadType == 0 && maxId > 0 ? maxId - 1 : Integer.MAX_VALUE;
        } else {
            boolean reachedOldest = !isCache && loaded.size() < count && (loadType == 0 || loadType == 2);
            lower = reachedOldest ? 1 : minLoaded;
            upper = fromNewest ? Integer.MAX_VALUE : maxLoaded;
        }

        List<MZGramHistoryMessage> rows = db.getDeletedInRange(accountUserId, dialogId, lower, upper, CHAT_HISTORY_LIMIT);
        for (int r = 0, N = rows.size(); r < N; r++) {
            MZGramHistoryMessage row = rows.get(r);
            if (ids.contains(row.messageId)) {
                continue;
            }
            TLRPC.Message message = deserializeMessage(row.messageData);
            if (message == null || message instanceof TLRPC.TL_messageService || message.id != row.messageId) {
                continue;
            }
            message.dialog_id = dialogId;
            message.unread = false;
            message.media_unread = false;
            message.mzgramDeleted = true;
            putFileBack(accountId, message, row.mediaPath);
            if (result == null) {
                result = new ArrayList<>(loaded);
            }
            insertByIdDescending(result, message);
            ids.add(message.id);
        }
        if (BuildVars.LOGS_ENABLED && result != null) {
            FileLog.d("MZGramHistoryController.messagesForChat dialog=" + dialogId + " range=[" + lower + ", " + upper + "] loaded=" + loaded.size() + " shown=" + result.size());
        }
        return result != null ? result : loaded;
    }

    // Loaded lists are newest first; ids <= 0 (local, ephemeral) are left
    // where they are.
    private static void insertByIdDescending(ArrayList<TLRPC.Message> list, TLRPC.Message message) {
        for (int a = 0, N = list.size(); a < N; a++) {
            TLRPC.Message m = list.get(a);
            if (m != null && m.id > 0 && m.id < message.id) {
                list.add(a, message);
                return;
            }
        }
        list.add(message);
    }

    // emptyMessagesMedia leaves the media without its photo/document (the
    // field is not even serialized any more).
    private static boolean hasRemovedMedia(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            return message.media.document == null || message.media.document instanceof TLRPC.TL_documentEmpty;
        }
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            return message.media.photo == null || message.media.photo instanceof TLRPC.TL_photoEmpty;
        }
        return false;
    }

    private TLRPC.Message withRestoredMedia(int accountId, long accountUserId, long dialogId, TLRPC.Message message) {
        MZGramHistoryMessage row = MZGramHistoryDatabase.getInstance().getLatest(accountUserId, dialogId, message.id, MZGramHistoryMessage.KIND_VIEW_ONCE);
        if (row == null) {
            return null;
        }
        TLRPC.Message old = deserializeMessage(row.messageData);
        TLRPC.Message copy = deserializeMessage(serializeMessage(message));
        if (old == null || old.media == null || copy == null) {
            return null;
        }
        copy.media = old.media;
        copy.media.ttl_seconds = 0;
        copy.media.flags &= ~4;
        copy.ttl = 0;
        copy.dialog_id = dialogId;
        copy.media_unread = false;
        copy.mzgramRestoredMedia = true;
        putFileBack(accountId, copy, row.mediaPath);
        return copy;
    }

    // ---- one-time media in an open chat ----

    // MessagesStorage.emptyMessagesMedia, right after it removed the media of
    // a message (and archived it): what an open chat is told the message now
    // looks like. For another person's message in a tracked chat that is the
    // media back from the archive, as ordinary media; otherwise null (the
    // chat shows it expired, as usual).
    public TLRPC.Message mediaForOpenChat(int accountId, long dialogId, TLRPC.Message emptied) {
        if (emptied == null || !isTracked(dialogId) || isOwnMessage(accountId, emptied)) {
            return null;
        }
        try {
            long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
            return withRestoredMedia(accountId, accountUserId, dialogId, emptied);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.mediaForOpenChat", e);
            return null;
        }
    }

    // ChatActivity, on updateMessageMedia with media put back
    // (mzgramRestoredMedia): turns the shown one-time message into an
    // ordinary one with that media, no longer expired.
    public static void applyRestoredMedia(MessageObject shown, TLRPC.Message restored) {
        shown.messageOwner.media = restored.media;
        shown.messageOwner.attachPath = restored.attachPath;
        shown.messageOwner.ttl = 0;
        shown.messageOwner.media_unread = false;
        shown.messageOwner.destroyTime = 0;
        shown.messageOwner.destroyTimeMillis = 0;
        shown.forceExpired = false;
        shown.generateThumbs(false);
        shown.setType();
        shown.forceUpdate = true;
    }

    // ChatActivity, when the user has viewed one-time media: false means
    // do not show it as expired. Kept: another person's message in a tracked
    // chat -- the media comes back from the archive (see mediaForOpenChat).
    public boolean keepsOneTimeMediaInChat(int accountId, long dialogId, TLRPC.Message message) {
        return message != null && isTracked(dialogId) && !isOwnMessage(accountId, message);
    }

    // ChatActivity, for a deleted message it keeps in the open chat: marked
    // deleted, and one-time media in it shown as ordinary media, since the
    // server will never let it be opened again.
    public static void markKeptDeleted(MessageObject shown) {
        shown.messageOwner.mzgramDeleted = true;
        TLRPC.MessageMedia media = MessageObject.getMedia(shown.messageOwner);
        if (shown.messageOwner.ttl != 0 || media != null && media.ttl_seconds != 0) {
            shown.messageOwner.ttl = 0;
            if (media != null) {
                media.ttl_seconds = 0;
                media.flags &= ~4;
            }
            shown.messageOwner.destroyTime = 0;
            shown.messageOwner.destroyTimeMillis = 0;
            shown.forceExpired = false;
            shown.setType();
        }
        shown.forceUpdate = true;
    }

    // Puts the archived copy of the file where the chat looks for this
    // message's media, if nothing is there (Telegram's cache may have been
    // cleared, and self-destructing media deletes its file).
    private static void putFileBack(int accountId, TLRPC.Message message, String mediaPath) {
        if (TextUtils.isEmpty(mediaPath) || message.media == null) {
            return;
        }
        try {
            File archived = new File(mediaPath);
            if (!archived.exists()) {
                return;
            }
            File target = FileLoader.getInstance(accountId).getPathToMessage(message);
            if (target == null || target.getPath().isEmpty() || target.exists()) {
                return;
            }
            File dir = target.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            AndroidUtilities.copyFile(archived, target);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.putFileBack", e);
        }
    }

    // ---- retrieval for the UI ----

    public boolean hasHistory(int accountId, long dialogId, int messageId) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        return MZGramHistoryDatabase.getInstance().hasHistory(accountUserId, dialogId, messageId);
    }

    public MZGramHistoryMessage getDeletedMessage(int accountId, long dialogId, int messageId) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        return MZGramHistoryDatabase.getInstance().getDeleted(accountUserId, dialogId, messageId);
    }

    public List<MZGramHistoryMessage> getRevisions(int accountId, long dialogId, int messageId) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        return MZGramHistoryDatabase.getInstance().getRevisions(accountUserId, dialogId, messageId);
    }

    public List<MZGramHistoryMessage> getArchive(int accountId, long dialogId, int limit) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        return MZGramHistoryDatabase.getInstance().getAllForDialog(accountUserId, dialogId, limit);
    }

    // ---- full wipe ----

    public void wipeArchive() {
        MZGramHistoryDatabase.getInstance().wipeAll();
    }

    // ---- export / import ----

    // Copies the raw SQLite file to a fresh location the caller can hand to
    // AndroidUtilities.openForView (native "open with"/share chooser). The
    // export is the database file itself, not a separate format -- import
    // is the exact reverse (see importArchive below).
    public File exportArchive() throws IOException {
        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        db.close(); // flush every pending write before copying the file
        File exportDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "mzgram_export");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File dest = new File(exportDir, "mzgram_archive_export.db");
        AndroidUtilities.copyFile(db.getDatabaseFile(), dest);
        return dest;
    }

    // Fully replaces the current archive database with the picked file's
    // contents. Does not touch already-copied media files on disk (those
    // are referenced by path from rows in the imported database, and export
    // does not currently bundle them -- only the row/text/entity data).
    public void importArchive(InputStream input) throws IOException {
        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        db.close();
        try (OutputStream out = new FileOutputStream(db.getDatabaseFile())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
