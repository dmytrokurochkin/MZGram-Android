/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Decides whether a deleted or edited message is worth keeping, and writes
 * the snapshot to MZGramHistoryDatabase. Other people's messages are kept in
 * every private chat, group, channel and secret chat while "Save deleted and
 * edited messages" is on; the owner's own messages never are.
 */

package org.telegram.messenger.mzgram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
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

    // Deleted messages are saved in every chat while the switch is on; chats
    // with bots only while "Save for bots" is on too.
    public static boolean savesChat(long dialogId) {
        return MZGramConfig.saveDeletedMessages && (MZGramConfig.saveForBots || !isBotChat(dialogId));
    }

    // Earlier versions of other people's edited messages, the same way.
    public static boolean savesEdits(long dialogId) {
        return MZGramConfig.saveEditHistory && (MZGramConfig.saveForBots || !isBotChat(dialogId));
    }

    public static boolean isBotChat(long dialogId) {
        if (dialogId <= 0) {
            return false;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            TLRPC.User user = MessagesController.getInstance(a).getUser(dialogId);
            if (user != null) {
                return user.bot;
            }
        }
        return false;
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
        // MZGram: unconditional entry log -- savesChat()/message==null below
        // used to fail completely silently, so a switched-off archive and
        // "this hook was never even reached" were indistinguishable from
        // logcat. Diagnostic-only, no behavior change.
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController.onMessageDeleted: called for dialog " + dialogId
                    + ", messageId=" + (message != null ? message.id : "null")
                    + ", saveDeletedMessages=" + MZGramConfig.saveDeletedMessages);
        }
        if (message == null || !savesChat(dialogId)) {
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
    // instead of trusted to a prior Java-side check.
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
        if (oldMessage == null || newMessage == null || !savesEdits(dialogId) || isOwnMessage(accountId, oldMessage)) {
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

    // MZGram: Desktop MZGram already copies one-time media into its archive the same way (mzgram_archive).
    // Called right before the media is emptied locally (see ChatActivity's
    // sendSecretMediaDelete / doDeleteShowOnceTask), so the file is still on
    // disk.
    public void onOneTimeMediaViewed(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || !savesChat(dialogId) || isOwnMessage(accountId, message)) {
            return;
        }
        try {
            onOneTimeMediaViewedInner(accountId, dialogId, message);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.onOneTimeMediaViewed", e);
        }
    }

    // Saves one-time media with its file. Called several times for the same
    // message (on arrival once downloaded, when the viewer opens, when the
    // media is removed); the file may only be on disk at one of those
    // points, so a row saved earlier without its file gets the file later.
    private void onOneTimeMediaViewedInner(int accountId, long dialogId, TLRPC.Message message) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        MZGramHistoryMessage existing = db.getLatest(accountUserId, dialogId, message.id, MZGramHistoryMessage.KIND_VIEW_ONCE);
        if (existing != null && existing.mediaPath != null && new File(existing.mediaPath).exists()) {
            return; // already archived with its file
        }
        MZGramHistoryMessage row = buildRow(accountId, accountUserId, dialogId, message, MZGramHistoryMessage.KIND_VIEW_ONCE, true);
        long rowId;
        if (existing == null) {
            rowId = db.insert(row);
        } else if (row.mediaPath != null) {
            db.updateMedia(existing.rowId, row.mediaPath, row.mediaType, row.mimeType);
            rowId = existing.rowId;
        } else {
            return; // still no file on disk
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController: archived one-time media of message " + message.id + " in dialog " + dialogId
                    + ", rowId=" + rowId + ", file=" + row.mediaPath);
        }
    }

    // ---- one-time media on arrival ----

    // MessagesStorage.putMessages: messages arriving (or loaded) into the
    // cache. Another person's one-time media in a chat is fetched
    // right away and archived once downloaded, as MZGram Desktop does, so it
    // is kept even if it is never opened. Downloading the file does not tell
    // the sender it was viewed; that only happens on opening it.
    public void onMessagesStored(int accountId, ArrayList<TLRPC.Message> messages) {
        if (messages == null) {
            return;
        }
        for (int a = 0, N = messages.size(); a < N; a++) {
            TLRPC.Message message = messages.get(a);
            if (message == null || message.out) {
                continue;
            }
            if (message.from_id instanceof TLRPC.TL_peerUser) {
                MZGramLastSeen.record(message.from_id.user_id, message.date);
            } else if (message.from_id == null && message.peer_id != null && message.peer_id.user_id != 0) {
                MZGramLastSeen.record(message.peer_id.user_id, message.date);
            }
        }
        if (!MZGramConfig.saveDeletedMessages) {
            return;
        }
        for (int a = 0, N = messages.size(); a < N; a++) {
            TLRPC.Message message = messages.get(a);
            if (message == null || message.media == null || message.media.ttl_seconds == 0 || message.id <= 0) {
                continue;
            }
            long dialogId = MessageObject.getDialogId(message);
            if (!savesChat(dialogId) || isOwnMessage(accountId, message) || oneTimeFileName(message) == null) {
                continue;
            }
            try {
                onOneTimeMediaArrived(accountId, dialogId, message);
            } catch (Exception e) {
                FileLog.e("MZGramHistoryController.onMessagesStored", e);
            }
        }
    }

    // "account:fileName" -> {dialogId, message}
    private final ConcurrentHashMap<String, Object[]> pendingOneTime = new ConcurrentHashMap<>();
    private final boolean[] fileObserverAdded = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private static String oneTimeFileName(TLRPC.Message message) {
        TLRPC.MessageMedia media = message.media;
        if (media instanceof TLRPC.TL_messageMediaPhoto && media.photo instanceof TLRPC.TL_photo) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
            return size != null ? FileLoader.getAttachFileName(size) : null;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document instanceof TLRPC.TL_document) {
            return FileLoader.getAttachFileName(media.document);
        }
        return null;
    }

    private void onOneTimeMediaArrived(int accountId, long dialogId, TLRPC.Message message) {
        TLRPC.Message copy = deserializeMessage(serializeMessage(message));
        String fileName = oneTimeFileName(message);
        if (copy == null || fileName == null) {
            return;
        }
        copy.dialog_id = dialogId;
        MessagesStorage.getInstance(accountId).getStorageQueue().postRunnable(() -> {
            try {
                if (findLocalFile(accountId, copy) != null) {
                    onOneTimeMediaViewedInner(accountId, dialogId, copy);
                    return;
                }
                long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
                MZGramHistoryMessage existing = MZGramHistoryDatabase.getInstance().getLatest(accountUserId, dialogId, copy.id, MZGramHistoryMessage.KIND_VIEW_ONCE);
                if (existing != null && existing.mediaPath != null && new File(existing.mediaPath).exists()) {
                    return;
                }
                pendingOneTime.put(accountId + ":" + fileName, new Object[]{dialogId, copy});
                AndroidUtilities.runOnUIThread(() -> {
                    addFileObserver(accountId);
                    startDownload(accountId, copy);
                });
            } catch (Exception e) {
                FileLog.e("MZGramHistoryController.onOneTimeMediaArrived", e);
            }
        });
    }

    // Plain file in the cache (cache type 1), where findLocalFile looks.
    private static void startDownload(int accountId, TLRPC.Message message) {
        FileLoader loader = FileLoader.getInstance(accountId);
        MessageObject parent = new MessageObject(accountId, message, false, false);
        TLRPC.MessageMedia media = message.media;
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize());
            if (size != null) {
                loader.loadFile(ImageLocation.getForPhoto(size, media.photo), parent, "jpg", FileLoader.PRIORITY_NORMAL, 1);
            }
        } else if (media.document != null) {
            loader.loadFile(media.document, parent, FileLoader.PRIORITY_NORMAL, 1);
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController: fetching one-time media of message " + message.id + " in dialog " + message.dialog_id);
        }
    }

    private void addFileObserver(int accountId) {
        if (fileObserverAdded[accountId]) {
            return;
        }
        fileObserverAdded[accountId] = true;
        NotificationCenter.getInstance(accountId).addObserver((id, account, args) -> {
            if (id != NotificationCenter.fileLoaded || args.length == 0 || !(args[0] instanceof String)) {
                return;
            }
            Object[] pending = pendingOneTime.remove(account + ":" + args[0]);
            if (pending == null) {
                return;
            }
            long dialogId = (Long) pending[0];
            TLRPC.Message message = (TLRPC.Message) pending[1];
            MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                try {
                    onOneTimeMediaViewedInner(account, dialogId, message);
                } catch (Exception e) {
                    FileLog.e("MZGramHistoryController.fileLoaded", e);
                }
            });
        }, NotificationCenter.fileLoaded);
    }

    // Called by MessagesStorage.emptyMessagesMedia right before it replaces a
    // message's media with an empty one and deletes the file: self-destructing
    // media whose timer ran out, and view-once media after viewing. The file
    // is still on disk here, so a copy goes into the archive.
    public void onMessageMediaRemoved(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || message.media == null || !savesChat(dialogId) || isOwnMessage(accountId, message)) {
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
        TLRPC.Message saved = withoutSwitchedOffParts(message);
        row.entities = serializeEntities(saved);
        row.messageData = serializeMessage(saved);
        copyMediaIfNeeded(accountId, accountUserId, dialogId, message, row, oneTimeMedia);
        return row;
    }

    // A copy of the message without the parts whose switches are off
    // (formatting, reactions); the message itself is left as it is, since
    // the chat may still be showing it.
    private static TLRPC.Message withoutSwitchedOffParts(TLRPC.Message message) {
        if (MZGramConfig.saveFormatting && MZGramConfig.saveReactions) {
            return message;
        }
        TLRPC.Message copy = deserializeMessage(serializeMessage(message));
        if (copy == null) {
            return message;
        }
        copy.dialog_id = message.dialog_id;
        if (!MZGramConfig.saveFormatting && copy.entities != null) {
            copy.entities.clear();
        }
        if (!MZGramConfig.saveReactions) {
            copy.reactions = null;
            copy.flags &= ~org.telegram.tgnet.TLObject.FLAG_20;
        }
        return copy;
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

        if (!MZGramConfig.saveArchiveMedia) {
            return;
        }
        LocalFile source = findLocalFile(accountId, message);
        if (source == null) {
            return; // not downloaded on this device -- nothing local to archive
        }

        // Any size is kept: there is no per-file limit and no total quota,
        // and saved files are never deleted on their own.
        int mediaType;
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            mediaType = MZGramHistoryMessage.MEDIA_PHOTO;
        } else if (MessageObject.isStickerMessage(message) || MessageObject.isAnimatedStickerMessage(message)) {
            mediaType = MZGramHistoryMessage.MEDIA_STICKER;
        } else {
            mediaType = MZGramHistoryMessage.MEDIA_FILE;
        }

        try {
            File destDir = MZGramHistoryDatabase.mediaDir(accountUserId, dialogId);
            File dest = new File(destDir, dialogId + "_" + message.id + "_" + source.name());
            if (!dest.exists() || dest.length() == 0) {
                copyOut(source, dest);
            }
            row.mediaPath = dest.getAbsolutePath();
            row.mediaType = mediaType;
            if (message.media.document != null) {
                row.mimeType = message.media.document.mime_type;
            }
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.copyMediaIfNeeded", e);
        }
    }

    // A downloaded file of a message: plain, or encrypted in the cache with
    // its key file (how the one-time viewer stores photos and videos).
    private static final class LocalFile {
        final File file;
        final File keyFile;

        LocalFile(File file, File keyFile) {
            this.file = file;
            this.keyFile = keyFile;
        }

        // The name of the plain file.
        String name() {
            String name = file.getName();
            return keyFile != null && name.endsWith(".enc") ? name.substring(0, name.length() - 4) : name;
        }
    }

    // Where the downloaded file of this message is on disk. Looked for in
    // the path the message was sent from (attachPath), the regular download
    // folder and the cache, for every photo size (the chat, the one-time
    // viewer and the archive each pick their own size), both as a plain
    // file and as "<name>.enc" with "<internal cache>/<name>.enc.key" --
    // the one-time viewer downloads photos and videos encrypted like that.
    private static LocalFile findLocalFile(int accountId, TLRPC.Message message) {
        try {
            if (!TextUtils.isEmpty(message.attachPath)) {
                File f = new File(message.attachPath);
                if (f.exists() && f.length() > 0) {
                    return new LocalFile(f, null);
                }
            }
            FileLoader loader = FileLoader.getInstance(accountId);
            ArrayList<File> candidates = new ArrayList<>();
            TLRPC.MessageMedia media = MessageObject.getMedia(message);
            if (media != null && media.photo != null && media.photo.sizes != null) {
                ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<>(media.photo.sizes);
                Collections.sort(sizes, (x, y) -> Integer.compare(y.w * y.h, x.w * x.h));
                for (int a = 0, N = sizes.size(); a < N; a++) {
                    TLRPC.PhotoSize size = sizes.get(a);
                    if (size instanceof TLRPC.TL_photoStrippedSize || size instanceof TLRPC.TL_photoPathSize) {
                        continue;
                    }
                    candidates.add(loader.getPathToAttach(size, true));
                    candidates.add(loader.getPathToAttach(size, false));
                }
            } else if (media != null && media.document != null) {
                candidates.add(loader.getPathToAttach(media.document, true));
                candidates.add(loader.getPathToAttach(media.document, false));
            }
            candidates.add(loader.getPathToMessage(message));
            candidates.add(loader.getPathToMessage(message, true, true));
            for (int a = 0, N = candidates.size(); a < N; a++) {
                File f = candidates.get(a);
                if (f == null || f.getPath().isEmpty()) {
                    continue;
                }
                if (f.exists() && f.length() > 0) {
                    return new LocalFile(f, null);
                }
                File enc = new File(f.getAbsolutePath() + ".enc");
                File key = new File(FileLoader.getInternalCacheDir(), f.getName() + ".enc.key");
                if (enc.exists() && enc.length() > 0 && key.exists()) {
                    return new LocalFile(enc, key);
                }
            }
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.findLocalFile", e);
        }
        return null;
    }

    private static void copyOut(LocalFile source, File dest) throws Exception {
        if (source.keyFile == null) {
            AndroidUtilities.copyFile(source.file, dest);
            return;
        }
        try (java.io.InputStream in = new org.telegram.messenger.secretmedia.EncryptedFileInputStream(source.file, source.keyFile);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[64 * 1024];
            long left = source.file.length();
            while (left > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (read <= 0) {
                    break;
                }
                out.write(buffer, 0, read);
                left -= read;
            }
        }
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
        if (removeArchived && savesChat(dialogId)) {
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
    // Kept: another person's message in a chat that the user did
    // not remove themself.
    public boolean keepsDeletedInChat(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || message instanceof TLRPC.TL_messageService || !savesChat(dialogId) || isOwnMessage(accountId, message)) {
            return false;
        }
        Long when = localDeletions.get(dialogId + ":" + message.id);
        return when == null || System.currentTimeMillis() - when > LOCAL_DELETION_TTL_MS;
    }

    // MessagesController.processLoadedMessages, for a chat: the list
    // of messages the chat gets back, with
    //   - other people's archived deleted messages that belong in the loaded
    //     range put back in their place, marked as deleted;
    //   - media removed from a message (self-destructing, view once) put
    //     back from the archived file, as ordinary media.
    // Returns the loaded list itself when there is nothing to add, otherwise
    // a new list; the loaded one and its messages are never changed (they
    // may still be on their way into messages_v2).
    //
    // Works for private chats, groups, supergroups and channels, a forum
    // topic (threadMessageId = topic, isTopic: only that topic's messages),
    // a reply thread (threadMessageId = the thread's root message: only
    // replies in it) and secret chats.
    //
    // An archived message is added only where it certainly belongs: between
    // the oldest and the newest loaded message, below them when the history
    // starts there, above them when this is the newest part of the chat.
    // Anything else would make the chat think it has loaded a range it has
    // not.
    public ArrayList<TLRPC.Message> messagesForChat(int accountId, long dialogId, ArrayList<TLRPC.Message> loaded, int count, int maxId, int loadType, boolean isCache, long threadMessageId, boolean isTopic) {
        if (loaded == null || !savesChat(dialogId)) {
            return loaded;
        }
        try {
            return messagesForChatInner(accountId, dialogId, loaded, count, maxId, loadType, isCache, threadMessageId, isTopic);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.messagesForChat", e);
            return loaded;
        }
    }

    private static final int CHAT_HISTORY_LIMIT = 200;

    private ArrayList<TLRPC.Message> messagesForChatInner(int accountId, long dialogId, ArrayList<TLRPC.Message> loaded, int count, int maxId, int loadType, boolean isCache, long threadMessageId, boolean isTopic) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        ArrayList<TLRPC.Message> result = null;
        // In a secret chat message ids are negative and go down: the newest
        // message has the lowest id. Its whole history is on the device.
        final boolean secret = DialogObject.isEncryptedDialog(dialogId);

        HashSet<Integer> ids = new HashSet<>();
        int minLoaded = Integer.MAX_VALUE;
        int maxLoaded = Integer.MIN_VALUE;
        for (int a = 0, N = loaded.size(); a < N; a++) {
            TLRPC.Message message = loaded.get(a);
            if (message == null || (secret ? message.id >= 0 : message.id <= 0)) {
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
        boolean reachedOldest = (secret || !isCache) && loaded.size() < count && (loadType == 0 || loadType == 2);
        int lower;
        int upper;
        if (ids.isEmpty()) {
            if (!secret && isCache || !fromNewest && loadType != 0) {
                return result != null ? result : loaded;
            }
            // Nothing here: the chat is empty from its newest message, or
            // nothing is older than max_id.
            if (secret) {
                lower = Integer.MIN_VALUE;
                upper = -1;
            } else {
                lower = 1;
                upper = loadType == 0 && maxId > 0 ? maxId - 1 : Integer.MAX_VALUE;
            }
        } else if (secret) {
            lower = fromNewest ? Integer.MIN_VALUE : minLoaded;
            upper = reachedOldest ? -1 : maxLoaded;
        } else {
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
            if (threadMessageId != 0 && !belongsToThread(accountId, message, threadMessageId, isTopic)) {
                continue;
            }
            message.dialog_id = dialogId;
            message.unread = false;
            message.media_unread = false;
            message.mzgramDeleted = true;
            // One-time media of a deleted message: the server will never let
            // it be opened again, so it is shown as ordinary media.
            if (message.media != null && message.media.ttl_seconds != 0) {
                message.media.ttl_seconds = 0;
                message.media.flags &= ~4;
            }
            message.ttl = 0;
            putFileBack(accountId, message, row.mediaPath);
            if (result == null) {
                result = new ArrayList<>(loaded);
            }
            insertNewestFirst(result, message, secret);
            ids.add(message.id);
        }
        if (BuildVars.LOGS_ENABLED && result != null) {
            FileLog.d("MZGramHistoryController.messagesForChat dialog=" + dialogId + " thread=" + threadMessageId + " topic=" + isTopic
                    + " range=[" + lower + ", " + upper + "] loaded=" + loaded.size() + " shown=" + result.size());
        }
        return result != null ? result : loaded;
    }

    // A forum topic holds the messages whose topic id is that topic; a reply
    // thread holds the replies to its root message (and the root itself).
    private static boolean belongsToThread(int accountId, TLRPC.Message message, long threadMessageId, boolean isTopic) {
        if (isTopic) {
            return MessageObject.getTopicId(accountId, message, true) == threadMessageId;
        }
        if (message.id == threadMessageId) {
            return true;
        }
        if (message.reply_to == null) {
            return false;
        }
        long top = message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id;
        return top == threadMessageId;
    }

    // Loaded lists are newest first. Normal ids grow with time, secret chat
    // ids go down; ids of the other sign (local, ephemeral) are left where
    // they are.
    private static void insertNewestFirst(ArrayList<TLRPC.Message> list, TLRPC.Message message, boolean secret) {
        for (int a = 0, N = list.size(); a < N; a++) {
            TLRPC.Message m = list.get(a);
            if (m == null) {
                continue;
            }
            boolean older = secret ? (m.id < 0 && m.id > message.id) : (m.id > 0 && m.id < message.id);
            if (older) {
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
    // looks like. For another person's message in a chat that is the
    // media back from the archive, as ordinary media; otherwise null (the
    // chat shows it expired, as usual).
    public TLRPC.Message mediaForOpenChat(int accountId, long dialogId, TLRPC.Message emptied) {
        if (emptied == null || !savesChat(dialogId) || isOwnMessage(accountId, emptied)) {
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
        shown.checkMediaExistance();
        shown.forceUpdate = true;
    }

    // ChatActivity, when the user has viewed one-time media: false means
    // do not show it as expired. Kept: another person's message -- the media
    // comes back from the archive (see mediaForOpenChat).
    public boolean keepsOneTimeMediaInChat(int accountId, long dialogId, TLRPC.Message message) {
        return message != null && savesChat(dialogId) && !isOwnMessage(accountId, message);
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
            FileLoader loader = FileLoader.getInstance(accountId);
            ArrayList<File> targets = new ArrayList<>();
            targets.add(loader.getPathToMessage(message));
            // A chat bubble shows a photo at its own size, from the image folder.
            if (message.media.photo != null && message.media.photo.sizes != null) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(message.media.photo.sizes, AndroidUtilities.getPhotoSize());
                if (size != null) {
                    targets.add(loader.getPathToAttach(size, false));
                }
            }
            for (int a = 0, N = targets.size(); a < N; a++) {
                File target = targets.get(a);
                if (target == null || target.getPath().isEmpty() || target.exists()) {
                    continue;
                }
                File dir = target.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                AndroidUtilities.copyFile(archived, target);
            }
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
