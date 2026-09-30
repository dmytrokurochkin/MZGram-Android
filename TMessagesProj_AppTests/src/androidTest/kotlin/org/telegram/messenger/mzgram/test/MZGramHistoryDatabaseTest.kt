package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramHistoryMessage

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
}
