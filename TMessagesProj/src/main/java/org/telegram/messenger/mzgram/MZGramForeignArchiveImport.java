/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Settings > MZGram > Archive > "Import from another client": adds the
 * deleted and edited messages, deleted chats and last seen times of another
 * client's message database (an SQLite file with the tables DeletedMessage
 * and/or EditedMessage, such as database-export.sqlite) to the MZGram
 * archive. Nothing already in the archive is replaced or removed; a message
 * already kept is skipped. The owner's own messages are left out, as the
 * archive never keeps them.
 *
 * A deleted text message becomes a whole message, so it shows in its chat
 * again; one with media keeps its text and, when the file is still
 * readable, the path of its file, and shows in the archive lists.
 *
 * Ported from exteraless (GPL-3.0), app/exteraless/backup/
 * AyuDatabaseImport.java (merge, copyDeleted, copyEdited) and the message
 * mapping of com/radolyn/ayugram/proprietary/AyuMessageUtils.java (map);
 * adapted to MZGramHistoryDatabase.
 */

package org.telegram.messenger.mzgram;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class MZGramForeignArchiveImport {

    private static final String DELETED = "DeletedMessage";
    private static final String EDITED = "EditedMessage";
    private static final String DELETED_DIALOGS = "DeletedDialog";
    private static final String LAST_SEEN = "LastSeenEntity";

    // The other client's kinds of media (DOCUMENT_TYPE_*)
    private static final int TYPE_NONE = 0;
    private static final int TYPE_PHOTO = 1;
    private static final int TYPE_STICKER = 2;
    private static final int TYPE_FILE = 3;

    private MZGramForeignArchiveImport() {
    }

    public static final class Result {
        public int deleted;
        public int edited;
    }

    // Reads the picked file into a temporary copy and adds its rows. Throws
    // when it is not such a database; then the archive stays as it was.
    // Not on the UI thread.
    public static Result importFrom(InputStream input) throws IOException {
        File temp = new File(ApplicationLoader.applicationContext.getCacheDir(), "mzgram_foreign_import.db");
        try {
            try (OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            if (!isSqlite(temp)) {
                throw new IOException("not an SQLite database");
            }
            try (SQLiteDatabase source = SQLiteDatabase.openDatabase(temp.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                if (!hasTable(source, DELETED) && !hasTable(source, EDITED)) {
                    throw new IOException("not a message database");
                }
                Result result = new Result();
                MZGramHistoryDatabase target = MZGramHistoryDatabase.getInstance();
                ArrayList<long[]> lastSeen = new ArrayList<>();
                target.inTransaction(() -> {
                    result.deleted = copyMessages(source, target, DELETED, MZGramHistoryMessage.KIND_DELETED);
                    result.edited = copyMessages(source, target, EDITED, MZGramHistoryMessage.KIND_EDITED);
                    copyDeletedDialogs(source, target);
                    readLastSeen(source, lastSeen);
                });
                for (int a = 0, N = lastSeen.size(); a < N; a++) {
                    MZGramLastSeen.record(lastSeen.get(a)[0], (int) lastSeen.get(a)[1]);
                }
                return result;
            } catch (android.database.SQLException e) {
                throw new IOException(e);
            }
        } finally {
            SQLiteDatabase.deleteDatabase(temp);
        }
    }

    private static boolean isSqlite(File file) {
        byte[] header = new byte[16];
        try (FileInputStream in = new FileInputStream(file)) {
            return in.read(header) == header.length && new String(header, 0, 15, StandardCharsets.US_ASCII).equals("SQLite format 3");
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasTable(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new String[]{table})) {
            return cursor.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    private static long getLong(Cursor cursor, int index) {
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getLong(index);
    }

    private static int getInt(Cursor cursor, int index) {
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static String getString(Cursor cursor, int index) {
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static byte[] getBlob(Cursor cursor, int index) {
        return index < 0 || cursor.isNull(index) || cursor.getType(index) != Cursor.FIELD_TYPE_BLOB ? null : cursor.getBlob(index);
    }

    private static int copyMessages(SQLiteDatabase source, MZGramHistoryDatabase target, String table, int kind) {
        if (!hasTable(source, table)) {
            return 0;
        }
        int added = 0;
        try (Cursor row = source.rawQuery("SELECT * FROM `" + table + "`", null)) {
            int userId = row.getColumnIndex("userId");
            int dialogId = row.getColumnIndex("dialogId");
            int messageId = row.getColumnIndex("messageId");
            if (userId < 0 || dialogId < 0 || messageId < 0) {
                return 0;
            }
            int topicId = row.getColumnIndex("topicId");
            int groupedId = row.getColumnIndex("groupedId");
            int peerId = row.getColumnIndex("peerId");
            int fromId = row.getColumnIndex("fromId");
            int date = row.getColumnIndex("date");
            int editDate = row.getColumnIndex("editDate");
            int entityCreateDate = row.getColumnIndex("entityCreateDate");
            int text = row.getColumnIndex("text");
            int textEntities = row.getColumnIndex("textEntities");
            int mediaPath = row.getColumnIndex("mediaPath");
            int documentType = row.getColumnIndex("documentType");
            int mimeType = row.getColumnIndex("mimeType");
            int replyMessageId = row.getColumnIndex("replyMessageId");
            while (row.moveToNext()) {
                try {
                    MZGramHistoryMessage m = new MZGramHistoryMessage();
                    m.kind = kind;
                    m.accountUserId = getLong(row, userId);
                    m.dialogId = getLong(row, dialogId);
                    m.messageId = getInt(row, messageId);
                    m.fromId = getLong(row, fromId);
                    if (m.accountUserId == 0 || m.dialogId == 0 || m.messageId == 0) {
                        continue;
                    }
                    // The archive never keeps the owner's own messages
                    if (m.fromId == m.accountUserId || m.dialogId == m.accountUserId) {
                        continue;
                    }
                    m.topicId = getLong(row, topicId);
                    m.groupedId = getLong(row, groupedId);
                    m.date = getInt(row, date);
                    m.editDate = getInt(row, editDate);
                    m.entityCreateDate = getInt(row, entityCreateDate);
                    if (m.entityCreateDate == 0) {
                        m.entityCreateDate = m.date;
                    }
                    if (kind == MZGramHistoryMessage.KIND_EDITED
                            ? target.hasRevision(m.accountUserId, m.dialogId, m.messageId, m.entityCreateDate)
                            : target.existsDeleted(m.accountUserId, m.dialogId, m.messageId)) {
                        continue;
                    }
                    m.text = getString(row, text);
                    ArrayList<TLRPC.MessageEntity> entities = readEntities(getBlob(row, textEntities));
                    m.entities = writeEntities(entities);
                    int type = getInt(row, documentType);
                    m.mediaType = type == TYPE_PHOTO ? MZGramHistoryMessage.MEDIA_PHOTO
                            : type == TYPE_STICKER ? MZGramHistoryMessage.MEDIA_STICKER
                            : type == TYPE_FILE ? MZGramHistoryMessage.MEDIA_FILE : MZGramHistoryMessage.MEDIA_NONE;
                    String path = getString(row, mediaPath);
                    if (!TextUtils.isEmpty(path) && new File(path).canRead()) {
                        m.mediaPath = path;
                        m.mimeType = getString(row, mimeType);
                    }
                    if (kind == MZGramHistoryMessage.KIND_DELETED && type == TYPE_NONE && !TextUtils.isEmpty(m.text)) {
                        long peer = getLong(row, peerId);
                        m.messageData = textMessage(m, peer != 0 ? peer : m.dialogId, entities, getInt(row, replyMessageId));
                    }
                    if (target.insert(m) != -1) {
                        added++;
                    }
                } catch (Exception e) {
                    FileLog.e("MZGramForeignArchiveImport.row", e);
                }
            }
        }
        return added;
    }

    // The other client writes entities one after another; MZGram keeps the
    // count first (MZGramHistoryController.serializeEntities).
    private static ArrayList<TLRPC.MessageEntity> readEntities(byte[] bytes) {
        ArrayList<TLRPC.MessageEntity> result = new ArrayList<>();
        if (bytes == null || bytes.length == 0) {
            return result;
        }
        SerializedData data = new SerializedData(bytes);
        try {
            while (data.remaining() >= 4) {
                TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(false), false);
                if (entity == null) {
                    break;
                }
                result.add(entity);
            }
        } catch (Exception e) {
            FileLog.e("MZGramForeignArchiveImport.readEntities", e);
        } finally {
            data.cleanup();
        }
        return result;
    }

    private static byte[] writeEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        int size = 4;
        for (int a = 0, N = entities.size(); a < N; a++) {
            size += entities.get(a).getObjectSize();
        }
        SerializedData data = new SerializedData(size);
        try {
            data.writeInt32(entities.size());
            for (int a = 0, N = entities.size(); a < N; a++) {
                entities.get(a).serializeToStream(data);
            }
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e("MZGramForeignArchiveImport.writeEntities", e);
            return null;
        } finally {
            data.cleanup();
        }
    }

    private static MessagesController controllerFor(long accountUserId) {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && UserConfig.getInstance(a).getClientUserId() == accountUserId) {
                return MessagesController.getInstance(a);
            }
        }
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    // A deleted text message as a whole message, for the chat to show it.
    private static byte[] textMessage(MZGramHistoryMessage m, long peerId, ArrayList<TLRPC.MessageEntity> entities, int replyTo) {
        MessagesController controller = controllerFor(m.accountUserId);
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = m.messageId;
        message.date = m.date;
        message.dialog_id = m.dialogId;
        message.peer_id = controller.getPeer(peerId);
        message.message = m.text;
        if (m.fromId != 0) {
            message.from_id = controller.getPeer(m.fromId);
            message.flags |= 256;
        }
        if (entities != null && !entities.isEmpty()) {
            message.entities = entities;
            message.flags |= 128;
        }
        if (m.editDate != 0) {
            message.edit_date = m.editDate;
            message.flags |= 32768;
        }
        if (m.groupedId != 0) {
            message.grouped_id = m.groupedId;
            message.flags |= 131072;
        }
        if (replyTo != 0) {
            TLRPC.TL_messageReplyHeader header = new TLRPC.TL_messageReplyHeader();
            header.flags |= 16;
            header.reply_to_msg_id = replyTo;
            message.reply_to = header;
            message.flags |= 8;
        }
        return MZGramHistoryController.serializeMessage(message);
    }

    private static void copyDeletedDialogs(SQLiteDatabase source, MZGramHistoryDatabase target) {
        if (!hasTable(source, DELETED_DIALOGS)) {
            return;
        }
        try (Cursor row = source.rawQuery("SELECT * FROM `" + DELETED_DIALOGS + "`", null)) {
            int userId = row.getColumnIndex("userId");
            int dialogId = row.getColumnIndex("dialogId");
            int entityCreateDate = row.getColumnIndex("entityCreateDate");
            if (userId < 0 || dialogId < 0) {
                return;
            }
            while (row.moveToNext()) {
                long account = getLong(row, userId);
                long dialog = getLong(row, dialogId);
                if (account != 0 && dialog != 0 && dialog != account) {
                    target.putDeletedDialog(account, dialog, null, getInt(row, entityCreateDate));
                }
            }
        } catch (Exception e) {
            FileLog.e("MZGramForeignArchiveImport.deletedDialogs", e);
        }
    }

    private static void readLastSeen(SQLiteDatabase source, ArrayList<long[]> out) {
        if (!hasTable(source, LAST_SEEN)) {
            return;
        }
        try (Cursor row = source.rawQuery("SELECT * FROM `" + LAST_SEEN + "`", null)) {
            int userId = row.getColumnIndex("userId");
            int lastSeen = row.getColumnIndex("lastSeen");
            if (userId < 0 || lastSeen < 0) {
                return;
            }
            while (row.moveToNext()) {
                long user = getLong(row, userId);
                long at = getLong(row, lastSeen);
                if (at > 100_000_000_000L) {
                    at /= 1000; // kept in milliseconds
                }
                if (user > 0 && at > 0 && at < Integer.MAX_VALUE) {
                    out.add(new long[]{user, at});
                }
            }
        } catch (Exception e) {
            FileLog.e("MZGramForeignArchiveImport.lastSeen", e);
        }
    }
}
