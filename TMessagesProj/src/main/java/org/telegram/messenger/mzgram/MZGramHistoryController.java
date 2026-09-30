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
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    // ---- deleted messages ----

    public void onMessageDeleted(int accountId, long dialogId, TLRPC.Message message) {
        if (message == null || !isTracked(dialogId)) {
            return;
        }
        try {
            onMessageDeletedInner(accountId, dialogId, message);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.onMessageDeleted", e);
        }
    }

    private void onMessageDeletedInner(int accountId, long dialogId, TLRPC.Message message) {
        long accountUserId = UserConfig.getInstance(accountId).getClientUserId();

        MZGramHistoryDatabase db = MZGramHistoryDatabase.getInstance();
        if (db.existsDeleted(accountUserId, dialogId, message.id)) {
            return;
        }

        long rowId = db.insert(buildRow(accountId, accountUserId, dialogId, message, MZGramHistoryMessage.KIND_DELETED));
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("MZGramHistoryController: archived deleted message " + message.id + " in dialog " + dialogId + ", rowId=" + rowId);
        }
    }

    // ---- edited messages ----

    public void onMessageEdited(int accountId, long dialogId, TLRPC.Message oldMessage, TLRPC.Message newMessage) {
        if (oldMessage == null || newMessage == null || !isTracked(dialogId)) {
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

    // Editing your OWN message never reaches onMessageEdited above: before
    // the edit request is even sent, SendMessagesHelper.editMessage()
    // overwrites the same TLRPC.Message object's text/media in place and
    // writes that already-new copy into messages_v2 for an instant UI
    // update (SendMessagesHelper.java, the "if (!retry)" block). By the
    // time the server's TL_updateEditMessage echoes back and reaches
    // onMessageEdited, the "old" row read from messages_v2 already holds
    // the new text, so the sameText/sameMedia check above silently treats
    // it as a no-op edit and nothing is archived.
    //
    // Called instead from that exact spot in SendMessagesHelper, with a
    // snapshot of the message taken before any field is overwritten, so
    // there is nothing to compare against here -- a real edit is already
    // certain (the user just confirmed one).
    public void onMessageEditedLocally(int accountId, long dialogId, TLRPC.Message oldMessage) {
        if (oldMessage == null || !isTracked(dialogId)) {
            return;
        }
        MessagesStorage.getInstance(accountId).getStorageQueue().postRunnable(() -> {
            try {
                long accountUserId = UserConfig.getInstance(accountId).getClientUserId();
                long rowId = MZGramHistoryDatabase.getInstance().insert(buildRow(accountId, accountUserId, dialogId, oldMessage, MZGramHistoryMessage.KIND_EDITED));
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("MZGramHistoryController: archived locally-edited message " + oldMessage.id + " in dialog " + dialogId + ", rowId=" + rowId);
                }
            } catch (Exception e) {
                FileLog.e("MZGramHistoryController.onMessageEditedLocally", e);
            }
        });
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
        if (message == null || !isTracked(dialogId)) {
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
        if (db.hasHistory(accountUserId, dialogId, message.id)) {
            return; // already archived
        }
        db.insert(buildRow(accountId, accountUserId, dialogId, message, MZGramHistoryMessage.KIND_VIEW_ONCE));
    }

    // ---- shared row building / media capture ----

    private MZGramHistoryMessage buildRow(int accountId, long accountUserId, long dialogId, TLRPC.Message message, int kind) {
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
        copyMediaIfNeeded(accountId, accountUserId, dialogId, message, row);
        return row;
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

    private void copyMediaIfNeeded(int accountId, long accountUserId, long dialogId, TLRPC.Message message, MZGramHistoryMessage row) {
        if (message.media == null) {
            return;
        }

        File source;
        try {
            source = FileLoader.getInstance(accountId).getPathToMessage(message);
        } catch (Exception e) {
            FileLog.e("MZGramHistoryController.copyMediaIfNeeded", e);
            return;
        }
        if (source == null || !source.exists()) {
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

        if (!alwaysSave) {
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
