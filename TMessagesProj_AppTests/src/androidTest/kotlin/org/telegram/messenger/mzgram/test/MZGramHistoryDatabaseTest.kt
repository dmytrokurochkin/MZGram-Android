package org.telegram.messenger.mzgram.test

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramHistoryMessage
import java.io.File

// Exercises the local deleted/edited message archive (the core of "spy
// mode"/anti-recall) directly against MZGramHistoryDatabase, the same
// SQLite store MZGramHistoryController writes to when a tracked chat's
// message is deleted or edited.
class MZGramHistoryDatabaseTest {

    // Far outside any real account/dialog id range, so a real archive's
    // rows are never matched by these lookups.
    private val testAccountUserId = -1_000_000_000_000L
    private val testDialogId = -1_000_000_000_001L

    private fun row(messageId: Int, kind: Int, text: String): MZGramHistoryMessage {
        val row = MZGramHistoryMessage()
        row.kind = kind
        row.accountUserId = testAccountUserId
        row.dialogId = testDialogId
        row.topicId = 0
        row.messageId = messageId
        row.groupedId = 0
        row.fromId = 12345
        row.date = 1_700_000_000
        row.editDate = 0
        row.entityCreateDate = 1_700_000_100
        row.text = text
        return row
    }

    @Test
    fun insertAndRetrieveDeletedMessage() {
        val db = MZGramHistoryDatabase.getInstance()
        val messageId = 555001
        try {
            assertEquals(false, db.existsDeleted(testAccountUserId, testDialogId, messageId))

            db.insert(row(messageId, MZGramHistoryMessage.KIND_DELETED, "this message was deleted"))

            assertTrue(db.existsDeleted(testAccountUserId, testDialogId, messageId))
            assertTrue(db.hasHistory(testAccountUserId, testDialogId, messageId))

            val deleted = db.getDeleted(testAccountUserId, testDialogId, messageId)
            assertNotNull(deleted)
            assertEquals("this message was deleted", deleted!!.text)
            assertEquals(MZGramHistoryMessage.KIND_DELETED, deleted.kind)
        } finally {
            db.delete(testAccountUserId, testDialogId, messageId)
        }
        assertEquals(false, db.hasHistory(testAccountUserId, testDialogId, messageId))
    }

    @Test
    fun editRevisionsAreOrderedByCaptureTime() {
        val db = MZGramHistoryDatabase.getInstance()
        val messageId = 555002
        try {
            val first = row(messageId, MZGramHistoryMessage.KIND_EDITED, "original text")
            first.entityCreateDate = 1_700_000_000
            db.insert(first)

            val second = row(messageId, MZGramHistoryMessage.KIND_EDITED, "edited once")
            second.entityCreateDate = 1_700_000_050
            db.insert(second)

            val revisions = db.getRevisions(testAccountUserId, testDialogId, messageId)
            assertEquals(2, revisions.size)
            assertEquals("original text", revisions[0].text)
            assertEquals("edited once", revisions[1].text)
        } finally {
            db.delete(testAccountUserId, testDialogId, messageId)
        }
    }

    @Test
    fun wipeAllClearsEveryRow() {
        val db = MZGramHistoryDatabase.getInstance()
        val messageId = 555003
        db.insert(row(messageId, MZGramHistoryMessage.KIND_DELETED, "will be wiped"))
        assertTrue(db.hasHistory(testAccountUserId, testDialogId, messageId))

        db.wipeAll()

        assertEquals(false, db.hasHistory(testAccountUserId, testDialogId, messageId))
        assertNull(db.getDeleted(testAccountUserId, testDialogId, messageId))
    }
    // An app update from database version 2 (no messageData column) must
    // keep every row archived so far.
    @Test
    fun upgradeFromVersion2_keepsArchivedRows() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mzgram_upgrade_test.db")
        file.delete()
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sql.execSQL("CREATE TABLE history_message (rowId INTEGER PRIMARY KEY AUTOINCREMENT, kind INTEGER NOT NULL, " +
                "accountUserId INTEGER NOT NULL, dialogId INTEGER NOT NULL, topicId INTEGER NOT NULL, messageId INTEGER NOT NULL, " +
                "groupedId INTEGER NOT NULL, fromId INTEGER NOT NULL, date INTEGER NOT NULL, editDate INTEGER NOT NULL, " +
                "entityCreateDate INTEGER NOT NULL, text TEXT, entities BLOB, mediaPath TEXT, mediaType INTEGER NOT NULL DEFAULT 0, mimeType TEXT)")
            sql.execSQL("INSERT INTO history_message (kind, accountUserId, dialogId, topicId, messageId, groupedId, fromId, date, editDate, entityCreateDate, text) " +
                "VALUES (0, 1, 2, 0, 3, 0, 4, 5, 0, 6, 'archived before the upgrade')")

            MZGramHistoryDatabase.getInstance().onUpgrade(sql, 2, 3)

            sql.rawQuery("SELECT text, messageData FROM history_message WHERE messageId = 3", null).use { c ->
                assertTrue("row kept", c.moveToFirst())
                assertEquals("archived before the upgrade", c.getString(0))
                assertTrue("old rows have no messageData", c.isNull(1))
            }
        } finally {
            sql.close()
            file.delete()
        }
    }
}
