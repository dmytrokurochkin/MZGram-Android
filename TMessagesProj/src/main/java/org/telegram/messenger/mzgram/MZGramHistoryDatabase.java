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
    private static final int DB_VERSION = 1;

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

    private MZGramHistoryDatabase(Context context) {
        super(context, dbPath(context), null, DB_VERSION);
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
                "mimeType TEXT" +
                ")");
        db.execSQL("CREATE INDEX idx_history_message_lookup ON " + TABLE + " (accountUserId, dialogId, messageId, kind)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

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
        return getWritableDatabase().insert(TABLE, null, values);
    }

    public boolean existsDeleted(long accountUserId, long dialogId, int messageId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + TABLE + " WHERE kind = ? AND accountUserId = ? AND dialogId = ? AND messageId = ? LIMIT 1",
                new String[]{String.valueOf(MZGramHistoryMessage.KIND_DELETED), String.valueOf(accountUserId), String.valueOf(dialogId), String.valueOf(messageId)})) {
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
        return m;
    }
}
