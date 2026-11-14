/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Local SQLite store for the deleted/edited message archive: a database
 * file of its own, kept in the app's private storage, entirely separate
 * from Telegram's own message cache and never sent anywhere over the
 * network.
 *
 * A plain SQLiteOpenHelper, to avoid adding the Room dependency (and its
 * annotation processor) for a single small table.
 */

package org.telegram.messenger.mzgram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MZGramHistoryDatabase extends SQLiteOpenHelper {

    static final String DB_NAME = "mzgram_history.db";
    // 3: messageData, the whole serialized message, so a deleted message can
    // be shown in the chat again.
    // 5: received, other people's messages as soon as they come (level 1
    // of the archive), so a deletion finds them even when Telegram's own
    // cache has no copy.
    // 5: DB_VERSION = 5 (received table).
    // 6: group_reads. 7: deleted_dialogs, chats that left the chat list
    // while the archive keeps their deleted messages.
    // A new table or column: DB_VERSION + 1, create it in onCreate (a
    // create<Name>Table method with CREATE TABLE IF NOT EXISTS, called from
    // onCreate and from onUpgrade's "if (oldVersion < N)"; a new column is
    // "ALTER TABLE ... ADD COLUMN ... DEFAULT ..." in onUpgrade only, plus the
    // column in onCreate). Archive data also goes into EXPORTED_TABLES and
    // wipeAll(); a table not in EXPORTED_TABLES stays out of export/import.
    private static final int DB_VERSION = 7;
    private static final String TABLE_READS = "outbox_reads";
    private static final String TABLE_RECEIVED = "received";
    private static final String TABLE_LAST_SEEN = "last_seen";
    // Who read the owner's message in a group (MZGramGroupReads), version 6.
    private static final String TABLE_GROUP_READS = "group_reads";
    // Chats that left the chat list while the archive keeps deleted messages
    // of them (MZGramDeletedDialogs), version 7.
    private static final String TABLE_DELETED_DIALOGS = "deleted_dialogs";

    private static final String TABLE = "history_message";
    // What export and import carry; received is only the level 1 cache.
    private static final String[] EXPORTED_TABLES = {TABLE, TABLE_READS, TABLE_LAST_SEEN, TABLE_GROUP_READS, TABLE_DELETED_DIALOGS};

    private static volatile MZGramHistoryDatabase instance;

    public static MZGramHistoryDatabase getInstance() {
        if (instance == null) {
            synchronized (MZGramHistoryDatabase.class) {
                if (instance == null) {
                    instance = new MZGramHistoryDatabase(ApplicationLoader.applicationContext);
                    // What a wipe set aside and did not delete (the app was
                    // closed first)
                    List<File> left = MZGramArchiveWipe.leftovers(archiveFolders());
                    if (!left.isEmpty()) {
                        new Thread(() -> MZGramArchiveWipe.remove(left), "MZGramArchiveWipe").start();
                    }
                }
            }
        }
        return instance;
    }

    private MZGramHistoryDatabase(Context context) {
        super(context, dbPath(context), null, DB_VERSION);
    }

    // Next to Telegram's own private data directory, not in public storage
    // (scoped storage on modern Android makes the public Downloads folder
    // fragile, and MZGram's Desktop history database lives in the private
    // tdata folder, so the Android archive follows the same "private app
    // data" placement).
    private static String dbPath(Context context) {
        File dir = new File(context.getFilesDir(), "mzgram");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, DB_NAME).getAbsolutePath();
    }

    // Export and import copy the rows through the open database and never
    // close() it: the app keeps using this helper from other threads all the
    // time (messages deleted or edited, reads, last seen), and a query that
    // got its connection before a close() fails with "the connection pool has
    // been closed". Inside the transaction those threads simply wait.

    // A new archive file at dest with every row of this one, as one moment
    // of the archive.
    public void exportTo(File dest) throws IOException {
        SQLiteDatabase.deleteDatabase(dest);
        try (SQLiteDatabase out = SQLiteDatabase.openOrCreateDatabase(dest, null)) {
            onCreate(out);
            out.setVersion(DB_VERSION);
            SQLiteDatabase db = getWritableDatabase();
            out.beginTransaction();
            db.beginTransaction();
            try {
                copyRows(db, out);
                out.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                out.endTransaction();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    // Replaces every row of this archive with the rows of the archive file
    // source, all at once; when that fails the archive stays as it was.
    // Columns the two have in common are copied, so an archive of an earlier
    // or later version comes in too.
    public void importFrom(File source) throws IOException {
        try (SQLiteDatabase in = SQLiteDatabase.openDatabase(source.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                copyRows(in, db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private static void copyRows(SQLiteDatabase from, SQLiteDatabase to) {
        for (String table : EXPORTED_TABLES) {
            to.delete(table, null, null);
            List<String> columns = columns(to, table);
            columns.retainAll(columns(from, table));
            if (columns.isEmpty()) {
                continue;
            }
            ContentValues values = new ContentValues();
            try (Cursor cursor = from.rawQuery("SELECT " + TextUtils.join(", ", columns) + " FROM " + table, null)) {
                while (cursor.moveToNext()) {
                    values.clear();
                    for (int i = 0; i < columns.size(); i++) {
                        String column = columns.get(i);
                        switch (cursor.getType(i)) {
                            case Cursor.FIELD_TYPE_NULL:
                                values.putNull(column);
                                break;
                            case Cursor.FIELD_TYPE_INTEGER:
                                values.put(column, cursor.getLong(i));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                values.put(column, cursor.getDouble(i));
                                break;
                            case Cursor.FIELD_TYPE_BLOB:
                                values.put(column, cursor.getBlob(i));
                                break;
                            default:
                                values.put(column, cursor.getString(i));
                                break;
                        }
                    }
                    to.insertOrThrow(table, null, values);
                }
            }
        }
    }

    // The columns of a table, none when the database has no such table.
    private static List<String> columns(SQLiteDatabase db, String table) {
        List<String> columns = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        return columns;
    }

    // Where earlier versions kept archived files, in the app's own folder.
    // Rows saved then still point there.
    public static File mediaRoot() {
        return new File(new File(ApplicationLoader.applicationContext.getFilesDir(), "mzgram"), "media");
    }

    // Archived files go to a visible folder, Downloads/MZGram/Saved
    // Attachments, with a .nomedia file so the gallery does not list them.
    public static File attachmentsRoot() {
        return new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MZGram"), "Saved Attachments");
    }

    // The folder new archived files are copied to: Saved Attachments, or the
    // app's own folder when shared storage cannot be written.
    public static File mediaDir(long accountUserId, long dialogId) {
        File dir = attachmentsRoot();
        if ((dir.isDirectory() || dir.mkdirs()) && dir.canWrite()) {
            File noMedia = new File(dir, ".nomedia");
            if (!noMedia.exists()) {
                try {
                    noMedia.createNewFile();
                } catch (Exception ignore) {
                }
            }
            return dir;
        }
        dir = new File(mediaRoot(), accountUserId + File.separator + dialogId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "rowId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "kind INTEGER NOT NULL, " +
                "accountUserId INTEGER NOT NULL, " +
                "dialogId INTEGER NOT NULL, " +
                "topicId INTEGER NOT NULL, " +
                "messageId INTEGER NOT NULL, " +
                "groupedId INTEGER NOT NULL, " +
                "fromId INTEGER NOT NULL, " +
                "date INTEGER NOT NULL, " +
                "editDate INTEGER NOT NULL, " +
                "entityCreateDate INTEGER NOT NULL, " +
                "text TEXT, " +
                "entities BLOB, " +
                "mediaPath TEXT, " +
                "mediaType INTEGER NOT NULL DEFAULT 0, " +
                "mimeType TEXT, " +
                "messageData BLOB" +
                ")");
        db.execSQL("CREATE INDEX idx_history_message_lookup ON " + TABLE + " (accountUserId, dialogId, messageId, kind)");
        // MZGram: own fix. A deleted message can only ever be archived once, so
        // this is a real UNIQUE constraint -- unlike KIND_EDITED, which needs
        // many rows per (accountUserId, dialogId, messageId), one per revision,
        // so the index is scoped to kind = KIND_DELETED only (a SQLite partial
        // index). insert() below relies on this via INSERT OR IGNORE instead of
        // a separate existsDeleted() pre-check in Java: a Java-side
        // check-then-insert can only ever be as reliable as that one extra
        // query, while a UNIQUE constraint enforced by SQLite itself at insert
        // time cannot silently drift from what is actually in the table.
        db.execSQL("CREATE UNIQUE INDEX idx_history_message_unique_deleted ON " + TABLE
                + " (accountUserId, dialogId, messageId) WHERE kind = " + MZGramHistoryMessage.KIND_DELETED);
        createReadAndLastSeenTables(db);
        createReceivedTable(db);
        createGroupReadsTable(db);
        createDeletedDialogsTable(db);
    }

    private static void createDeletedDialogsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DELETED_DIALOGS + " (" +
                "accountUserId INTEGER NOT NULL, " +
                "dialogId INTEGER NOT NULL, " +
                "title TEXT, " +
                "deletedAt INTEGER NOT NULL, " +
                "PRIMARY KEY (accountUserId, dialogId))");
    }

    // A chat that left the chat list (or lost all its messages): its name
    // then and when; the first time stays (a chat with no message left is
    // seen again on every load of the chat list).
    public void putDeletedDialog(long accountUserId, long dialogId, String title, int deletedAt) {
        ContentValues values = new ContentValues();
        values.put("accountUserId", accountUserId);
        values.put("dialogId", dialogId);
        values.put("title", title);
        values.put("deletedAt", deletedAt);
        getWritableDatabase().insertWithOnConflict(TABLE_DELETED_DIALOGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeDeletedDialog(long accountUserId, long dialogId) {
        getWritableDatabase().delete(TABLE_DELETED_DIALOGS, "accountUserId = ? AND dialogId = ?",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId)});
    }

    public static final class DeletedDialog {
        public long dialogId;
        public String title;
        public int deletedAt;
        public int keptCount; // deleted messages the archive keeps for it
        public int lastDate; // the newest of them

        public DeletedDialog() {
        }

        public DeletedDialog(long dialogId, String title, int deletedAt) {
            this.dialogId = dialogId;
            this.title = title;
            this.deletedAt = deletedAt;
        }
    }

    // The recorded chats that still have kept deleted messages, the most
    // recently gone first.
    public List<DeletedDialog> getDeletedDialogs(long accountUserId) {
        List<DeletedDialog> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT d.dialogId, d.title, d.deletedAt, COUNT(h.rowId), MAX(h.date) FROM " + TABLE_DELETED_DIALOGS + " d " +
                        "JOIN " + TABLE + " h ON h.accountUserId = d.accountUserId AND h.dialogId = d.dialogId AND h.kind = ? " +
                        "WHERE d.accountUserId = ? GROUP BY d.dialogId, d.title, d.deletedAt ORDER BY d.deletedAt DESC",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId)})) {
            while (cursor.moveToNext()) {
                DeletedDialog dialog = new DeletedDialog();
                dialog.dialogId = cursor.getLong(0);
                dialog.title = cursor.isNull(1) ? null : cursor.getString(1);
                dialog.deletedAt = cursor.getInt(2);
                dialog.keptCount = cursor.getInt(3);
                dialog.lastDate = cursor.getInt(4);
                result.add(dialog);
            }
        }
        return result;
    }

    public boolean hasDeleted(long accountUserId, long dialogId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? LIMIT 1",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId)})) {
            return cursor.moveToFirst();
        }
    }

    // The readers of the owner's message in a group, as the server listed
    // them (messages.getMessageReadParticipants); readAt is 0 when the
    // server gave no time.
    private static void createGroupReadsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_GROUP_READS + " (" +
                "accountUserId INTEGER NOT NULL, " +
                "dialogId INTEGER NOT NULL, " +
                "messageId INTEGER NOT NULL, " +
                "userId INTEGER NOT NULL, " +
                "readAt INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (accountUserId, dialogId, messageId, userId))");
    }

    public void putGroupReads(long accountUserId, long dialogId, int messageId, long[] userIds, int[] readAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < userIds.length; i++) {
                // A time the server gave is not replaced by a missing one.
                db.execSQL("INSERT OR IGNORE INTO " + TABLE_GROUP_READS + " (accountUserId, dialogId, messageId, userId, readAt) VALUES (?, ?, ?, ?, ?)",
                        new Object[]{accountUserId, dialogId, messageId, userIds[i], readAt[i]});
                if (readAt[i] > 0) {
                    db.execSQL("UPDATE " + TABLE_GROUP_READS + " SET readAt = ? WHERE accountUserId = ? AND dialogId = ? AND messageId = ? AND userId = ?",
                            new Object[]{readAt[i], accountUserId, dialogId, messageId, userIds[i]});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // {userId, readAt} pairs, earliest read first.
    public List<long[]> getGroupReads(long accountUserId, long dialogId, int messageId) {
        List<long[]> result = new ArrayList<>();
        String[] args = {String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)};
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT userId, readAt FROM " + TABLE_GROUP_READS + " WHERE accountUserId = ? AND dialogId = ? AND messageId = ? ORDER BY readAt", args)) {
            while (cursor.moveToNext()) {
                result.add(new long[]{cursor.getLong(0), cursor.getInt(1)});
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    private static void createReceivedTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_RECEIVED + " (" +
                "accountUserId INTEGER NOT NULL, " +
                "dialogId INTEGER NOT NULL, " +
                "messageId INTEGER NOT NULL, " +
                "channel INTEGER NOT NULL, " +
                "date INTEGER NOT NULL, " +
                "messageData BLOB, " +
                "mediaPath TEXT, " +
                "PRIMARY KEY (accountUserId, dialogId, messageId))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_received_message ON " + TABLE_RECEIVED + " (accountUserId, messageId, channel)");
    }

    // ---- level 1: the message as soon as it comes ----

    public void putReceived(long accountUserId, long dialogId, int messageId, boolean channel, int date, byte[] data) {
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO " + TABLE_RECEIVED
                        + " (accountUserId, dialogId, messageId, channel, date, messageData) VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{accountUserId, dialogId, messageId, channel ? 1 : 0, date, data});
    }

    public static final class Received {
        public long dialogId;
        public int messageId;
        public byte[] data;
        public String mediaPath;
    }

    // The kept message, or null. dialogId 0: a private chat or group, whose
    // deletions name only ids (they share one id sequence per account).
    public Received getReceived(long accountUserId, long dialogId, int messageId) {
        String query = dialogId != 0
                ? "SELECT dialogId, messageId, messageData, mediaPath FROM " + TABLE_RECEIVED + " WHERE accountUserId = ? AND dialogId = ? AND messageId = ?"
                : "SELECT dialogId, messageId, messageData, mediaPath FROM " + TABLE_RECEIVED + " WHERE accountUserId = ? AND messageId = ? AND channel = 0";
        String[] args = dialogId != 0
                ? new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)}
                : new String[]{String.valueOf(accountUserId), String.valueOf(messageId)};
        try (Cursor cursor = getReadableDatabase().rawQuery(query, args)) {
            if (cursor.moveToFirst()) {
                Received result = new Received();
                result.dialogId = cursor.getLong(0);
                result.messageId = cursor.getInt(1);
                result.data = cursor.isNull(2) ? null : cursor.getBlob(2);
                result.mediaPath = cursor.isNull(3) ? null : cursor.getString(3);
                return result;
            }
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e("MZGramHistoryDatabase.getReceived", e);
        }
        return null;
    }

    // Level 2: the file downloaded in advance.
    public void setReceivedMedia(long accountUserId, long dialogId, int messageId, String mediaPath) {
        ContentValues values = new ContentValues();
        values.put("mediaPath", mediaPath);
        getWritableDatabase().update(TABLE_RECEIVED, values, "accountUserId = ? AND dialogId = ? AND messageId = ?",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)});
    }

    // Once archived as deleted, or deleted by the owner.
    public void deleteReceived(long accountUserId, long dialogId, int messageId) {
        getWritableDatabase().delete(TABLE_RECEIVED, "accountUserId = ? AND dialogId = ? AND messageId = ?",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)});
    }

    // When other people read the owner's messages (MZGramReadDates): a row
    // per read event, every own message up to maxId read by readAt; exact
    // rows are the server's time for the one message maxId. And the last
    // time a user was seen online (MZGramLastSeen).
    private static void createReadAndLastSeenTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_READS + " (" +
                "accountUserId INTEGER NOT NULL, " +
                "dialogId INTEGER NOT NULL, " +
                "maxId INTEGER NOT NULL, " +
                "readAt INTEGER NOT NULL, " +
                "exact INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_reads ON " + TABLE_READS + " (accountUserId, dialogId, maxId)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_LAST_SEEN + " (" +
                "userId INTEGER PRIMARY KEY, " +
                "seenAt INTEGER NOT NULL)");
    }

    public void addOutboxRead(long accountUserId, long dialogId, int maxId, int readAt, boolean exact) {
        SQLiteDatabase db = getWritableDatabase();
        String[] key = {String.valueOf(accountUserId), String.valueOf(dialogId)};
        if (exact) {
            db.delete(TABLE_READS, "accountUserId = ? AND dialogId = ? AND maxId = ? AND exact = 1",
                    new String[]{key[0], key[1], String.valueOf(maxId)});
        } else {
            // Only a read further on than the ones kept says anything new.
            try (Cursor cursor = db.rawQuery("SELECT MAX(maxId) FROM " + TABLE_READS + " WHERE accountUserId = ? AND dialogId = ? AND exact = 0", key)) {
                if (cursor.moveToFirst() && !cursor.isNull(0) && cursor.getInt(0) >= maxId) {
                    return;
                }
            }
        }
        ContentValues values = new ContentValues();
        values.put("accountUserId", accountUserId);
        values.put("dialogId", dialogId);
        values.put("maxId", maxId);
        values.put("readAt", readAt);
        values.put("exact", exact ? 1 : 0);
        db.insert(TABLE_READS, null, values);
    }

    // {readAt, 1 if the server's time} for a message; {0, 0} when unknown.
    public int[] getReadAt(long accountUserId, long dialogId, int messageId) {
        SQLiteDatabase db = getReadableDatabase();
        String[] args = {String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)};
        try (Cursor cursor = db.rawQuery("SELECT readAt FROM " + TABLE_READS + " WHERE accountUserId = ? AND dialogId = ? AND maxId = ? AND exact = 1 LIMIT 1", args)) {
            if (cursor.moveToFirst()) {
                return new int[]{cursor.getInt(0), 1};
            }
        }
        try (Cursor cursor = db.rawQuery("SELECT MIN(readAt) FROM " + TABLE_READS + " WHERE accountUserId = ? AND dialogId = ? AND maxId >= ? AND exact = 0", args)) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return new int[]{cursor.getInt(0), 0};
            }
        }
        return new int[]{0, 0};
    }

    public void putLastSeen(long userId, int seenAt) {
        putLastSeen(userId, seenAt, Integer.MAX_VALUE);
    }

    // A kept time later than latestPossible is broken (a time in milliseconds,
    // a wrong clock) and is replaced by any real one.
    public void putLastSeen(long userId, int seenAt, int latestPossible) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO " + TABLE_LAST_SEEN + " (userId, seenAt) VALUES (?, ?)", new Object[]{userId, seenAt});
        db.execSQL("UPDATE " + TABLE_LAST_SEEN + " SET seenAt = ? WHERE userId = ? AND (seenAt < ? OR seenAt > ?)", new Object[]{seenAt, userId, seenAt, latestPossible});
    }

    public java.util.Map<Long, Integer> getAllLastSeen() {
        java.util.HashMap<Long, Integer> result = new java.util.HashMap<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT userId, seenAt FROM " + TABLE_LAST_SEEN, null)) {
            while (cursor.moveToNext()) {
                result.put(cursor.getLong(0), cursor.getInt(1));
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
            return;
        }
        if (oldVersion < 3) {
            // Keeps every row archived so far; old rows simply have no
            // messageData and are shown only in the archive screen.
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN messageData BLOB");
        }
        if (oldVersion < 4) {
            createReadAndLastSeenTables(db);
        }
        if (oldVersion < 5) {
            createReceivedTable(db);
        }
        if (oldVersion < 6) {
            createGroupReadsTable(db);
        }
        if (oldVersion < 7) {
            createDeletedDialogsTable(db);
        }
    }

    // MZGram: own fix. insertWithOnConflict(..., CONFLICT_IGNORE) instead of
    // plain insert(): for KIND_DELETED rows the partial unique index above
    // makes a genuine duplicate simply a no-op (returns -1), enforced
    // atomically by SQLite itself. This replaced a separate Java-side
    // existsDeleted() check-then-insert in MZGramHistoryController, which was
    // the prime suspect for deleted messages silently never being archived
    // (a stale/mismatched existsDeleted() read could short-circuit before
    // insert ever ran, with no way to tell from outside this class). For
    // KIND_EDITED/KIND_VIEW_ONCE rows, which are never covered by that index,
    // this behaves exactly like the plain insert() did before.
    public long insert(MZGramHistoryMessage msg) {
        ContentValues values = new ContentValues();
        values.put("kind", msg.kind);
        values.put("accountUserId", msg.accountUserId);
        values.put("dialogId", msg.dialogId);
        values.put("topicId", msg.topicId);
        values.put("messageId", msg.messageId);
        values.put("groupedId", msg.groupedId);
        values.put("fromId", msg.fromId);
        values.put("date", msg.date);
        values.put("editDate", msg.editDate);
        values.put("entityCreateDate", msg.entityCreateDate);
        values.put("text", msg.text);
        values.put("entities", msg.entities);
        values.put("mediaPath", msg.mediaPath);
        values.put("mediaType", msg.mediaType);
        values.put("mimeType", msg.mimeType);
        values.put("messageData", msg.messageData);
        return getWritableDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    // Several writes as one, all or none (an import that adds rows).
    public void inTransaction(Runnable work) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            work.run();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // A revision of the message captured at that time is already here.
    public boolean hasRevision(long accountUserId, long dialogId, int messageId, int entityCreateDate) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? AND entityCreateDate = ? LIMIT 1",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_EDITED), String.valueOf(accountUserId), String.valueOf(dialogId),
                        String.valueOf(messageId), String.valueOf(entityCreateDate)})) {
            return cursor.moveToFirst();
        }
    }

    public boolean existsDeleted(long accountUserId, long dialogId, int messageId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? LIMIT 1",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
            return cursor.moveToFirst();
        }
    }

    public void updateMedia(long rowId, String mediaPath, int mediaType, String mimeType) {
        ContentValues values = new ContentValues();
        values.put("mediaPath", mediaPath);
        values.put("mediaType", mediaType);
        values.put("mimeType", mimeType);
        getWritableDatabase().update(TABLE, values, "rowId = ?", new String[]{String.valueOf(rowId)});
    }

    public boolean hasKind(long accountUserId, long dialogId, int messageId, int kind) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? LIMIT 1",
                new String[]{String.valueOf(kind), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
            return cursor.moveToFirst();
        }
    }

    public boolean hasHistory(long accountUserId, long dialogId, int messageId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE accountUserId = ? AND dialogId = ? AND messageId = ? LIMIT 1",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
            return cursor.moveToFirst();
        }
    }

    public MZGramHistoryMessage getDeleted(long accountUserId, long dialogId, int messageId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? LIMIT 1",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    public List<MZGramHistoryMessage> getDeletedGrouped(long accountUserId, long dialogId, long groupedId) {
        List<MZGramHistoryMessage> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND groupedId = ? ORDER BY messageId",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(groupedId)})) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    // Deleted messages of a dialog with messageId in [minId, maxId] that can be
    // shown in the chat (messageData kept), newest first.
    public List<MZGramHistoryMessage> getDeletedInRange(long accountUserId, long dialogId, int minId, int maxId, int limit) {
        List<MZGramHistoryMessage> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId >= ? AND messageId <= ? AND messageData IS NOT NULL ORDER BY messageId DESC LIMIT ?",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId),
                        String.valueOf(minId), String.valueOf(maxId), String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    // The latest row of the given kind for one message, or null.
    public MZGramHistoryMessage getLatest(long accountUserId, long dialogId, int messageId, int kind) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? ORDER BY rowId DESC LIMIT 1",
                new String[]{String.valueOf(kind), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    public List<MZGramHistoryMessage> getAllForDialog(long accountUserId, long dialogId, int limit) {
        List<MZGramHistoryMessage> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE accountUserId = ? AND dialogId = ? ORDER BY entityCreateDate DESC LIMIT ?",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    // The deleted messages kept for a dialog, newest first; with a query,
    // those whose text has it (any case). For the list of a chat's deleted
    // messages (MZGramDeletedMessagesActivity).
    public List<MZGramHistoryMessage> getDeletedForDialog(long accountUserId, long dialogId, String query, int limit) {
        List<MZGramHistoryMessage> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(MZGramHistoryMessage.KIND_DELETED));
        args.add(String.valueOf(accountUserId));
        args.add(String.valueOf(dialogId));
        if (!TextUtils.isEmpty(query)) {
            sql.append(" AND text LIKE ? ESCAPE '!'");
            args.add("%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        sql.append(" ORDER BY date DESC, messageId DESC LIMIT ?");
        args.add(String.valueOf(limit));
        try (Cursor cursor = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    public List<MZGramHistoryMessage> getRevisions(long accountUserId, long dialogId, int messageId) {
        List<MZGramHistoryMessage> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? ORDER BY entityCreateDate",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_EDITED), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    // The kept deleted messages of one chat; returns how many rows went
    public int deleteDeletedOfDialog(long accountUserId, long dialogId) {
        return getWritableDatabase().delete(TABLE, "kind = ? AND accountUserId = ? AND dialogId = ?",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId)});
    }

    public void delete(long accountUserId, long dialogId, int messageId) {
        getWritableDatabase().delete(TABLE, "accountUserId = ? AND dialogId = ? AND messageId = ?",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)});
    }

    public void clean() {
        getWritableDatabase().execSQL("DELETE FROM " + TABLE);
    }

    // The media folder and Saved Attachments, what Clear archive deletes.
    public static List<File> archiveFolders() {
        List<File> result = new ArrayList<>();
        result.add(mediaRoot());
        result.add(attachmentsRoot());
        return result;
    }

    // Full wipe for the settings screen's "clear archive" action: every
    // archived row plus every copied media file on disk, for every account
    // and every dialog. The rows go at once; the folders are moved aside and
    // returned, to be deleted with MZGramArchiveWipe.remove (slow for
    // gigabytes). Not on the UI thread.
    public List<File> wipeAll() {
        clean();
        getWritableDatabase().execSQL("DELETE FROM " + TABLE_READS);
        getWritableDatabase().execSQL("DELETE FROM " + TABLE_LAST_SEEN);
        getWritableDatabase().execSQL("DELETE FROM " + TABLE_RECEIVED);
        getWritableDatabase().execSQL("DELETE FROM " + TABLE_GROUP_READS);
        getWritableDatabase().execSQL("DELETE FROM " + TABLE_DELETED_DIALOGS);
        MZGramLastSeen.forget();
        // The emptied database gives its space back
        try {
            getWritableDatabase().execSQL("VACUUM");
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e("MZGramHistoryDatabase.vacuum", e);
        }
        return MZGramArchiveWipe.setAside(archiveFolders());
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static MZGramHistoryMessage fromCursor(Cursor cursor) {
        MZGramHistoryMessage m = new MZGramHistoryMessage();
        m.rowId = cursor.getLong(cursor.getColumnIndexOrThrow("rowId"));
        m.kind = cursor.getInt(cursor.getColumnIndexOrThrow("kind"));
        m.accountUserId = cursor.getLong(cursor.getColumnIndexOrThrow("accountUserId"));
        m.dialogId = cursor.getLong(cursor.getColumnIndexOrThrow("dialogId"));
        m.topicId = cursor.getLong(cursor.getColumnIndexOrThrow("topicId"));
        m.messageId = cursor.getInt(cursor.getColumnIndexOrThrow("messageId"));
        m.groupedId = cursor.getLong(cursor.getColumnIndexOrThrow("groupedId"));
        m.fromId = cursor.getLong(cursor.getColumnIndexOrThrow("fromId"));
        m.date = cursor.getInt(cursor.getColumnIndexOrThrow("date"));
        m.editDate = cursor.getInt(cursor.getColumnIndexOrThrow("editDate"));
        m.entityCreateDate = cursor.getInt(cursor.getColumnIndexOrThrow("entityCreateDate"));
        m.text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
        m.entities = cursor.getBlob(cursor.getColumnIndexOrThrow("entities"));
        m.mediaPath = cursor.getString(cursor.getColumnIndexOrThrow("mediaPath"));
        m.mediaType = cursor.getInt(cursor.getColumnIndexOrThrow("mediaType"));
        m.mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mimeType"));
        m.messageData = cursor.getBlob(cursor.getColumnIndexOrThrow("messageData"));
        return m;
    }
}
