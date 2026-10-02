/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Local SQLite store for the deleted/edited message archive: a database
 * file of its own, kept in the app's private storage, entirely separate
 * from Telegram's own message cache and never sent anywhere over the
 * network.
 *
 * Ported concept from AyuGram4A 7013145676d36d82ee13c02a89f72097b7490dcd
 * (database/AyuDatabase.java, database/AyuData.java, database/dao/*.java).
 * AyuGram4A uses the Room library over this schema; MZGram uses a plain
 * SQLiteOpenHelper instead, to avoid adding the Room dependency (and its
 * annotation processor) for a single small table.
 */

package org.telegram.messenger.mzgram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MZGramHistoryDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "mzgram_history.db";
    // 3: messageData, the whole serialized message, so a deleted message can
    // be shown in the chat again.
    private static final int DB_VERSION = 3;

    private static final String TABLE = "history_message";

    private static volatile MZGramHistoryDatabase instance;

    public static MZGramHistoryDatabase getInstance() {
        if (instance == null) {
            synchronized (MZGramHistoryDatabase.class) {
                if (instance == null) {
                    instance = new MZGramHistoryDatabase(ApplicationLoader.applicationContext);
                }
            }
        }
        return instance;
    }

    private final String dbFilePath;

    private MZGramHistoryDatabase(Context context) {
        super(context, dbPath(context), null, DB_VERSION);
        dbFilePath = dbPath(context);
    }

    // Next to Telegram's own private data directory, not in public storage
    // (unlike AyuGram4A's attachmentsPath, which uses the public Downloads
    // folder -- scoped storage on modern Android makes that fragile, and
    // MZGram's Desktop history database lives in the private tdata folder,
    // so the Android archive follows the same "private app data" placement).
    private static String dbPath(Context context) {
        File dir = new File(context.getFilesDir(), "mzgram");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, DB_NAME).getAbsolutePath();
    }

    // For export/import: the raw SQLite file backing this database. Callers
    // must close() this helper first so every write is flushed to disk
    // before the file is copied or replaced -- this reads the path stored
    // at construction time rather than opening the database.
    public File getDatabaseFile() {
        return new File(dbFilePath);
    }

    public static File mediaRoot() {
        return new File(new File(ApplicationLoader.applicationContext.getFilesDir(), "mzgram"), "media");
    }

    public static File mediaDir(long accountUserId, long dialogId) {
        File dir = new File(new File(new File(ApplicationLoader.applicationContext.getFilesDir(), "mzgram"), "media"),
                accountUserId + File.separator + dialogId);
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
    }

    // MZGram: own fix. insertWithOnConflict(..., CONFLICT_IGNORE) instead of
    // plain insert(): for KIND_DELETED rows the partial unique index above
    // makes a genuine duplicate simply a no-op (returns -1), enforced
    // atomically by SQLite itself. This replaced a separate Java-side
    // existsDeleted() check-then-insert in MZGramHistoryController, which was
    // the prime suspect for deleted messages silently never being archived
    // (a stale/mismatched existsDeleted() read could short-circuit before
    // insert ever ran, with no way to tell from outside this class) -- see
    // docs/07-nekogram-features-plan.md for the investigation. For
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

    public void delete(long accountUserId, long dialogId, int messageId) {
        getWritableDatabase().delete(TABLE, "accountUserId = ? AND dialogId = ? AND messageId = ?",
                new String[]{String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)});
    }

    public void clean() {
        getWritableDatabase().execSQL("DELETE FROM " + TABLE);
    }

    // Full wipe for the settings screen's "clear archive" action: every
    // archived row plus every copied media file on disk, for every account
    // and every dialog (not just the currently tracked ones -- a chat
    // removed from the allowlist earlier still has old rows/files).
    public void wipeAll() {
        clean();
        deleteRecursively(mediaRoot());
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
