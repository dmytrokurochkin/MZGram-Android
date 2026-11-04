package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramArchiveLimits
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramHistoryController
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramKeepFrom
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The archive's three levels: the message as it comes (also when Telegram's
// cache never had it), its file in advance up to the limit of its kind of
// chat, the file when deleted up to 100 MB more; the counters under the
// archive size.
class MZGramArchiveLevelsTest {

    private val account = 0
    private val selfId = 7_000_000_101L
    private val otherUserId = 7_000_000_102L

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)
    private var saved = false
    private var media = false

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram levels test"
        UserConfig.getInstance(account).setCurrentUser(self)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller }
        saved = MZGramConfig.saveDeletedMessages
        media = MZGramConfig.saveArchiveMedia
        MZGramConfig.saveDeletedMessages = true
        MZGramConfig.saveArchiveMedia = true
    }

    @After
    fun tearDown() {
        MZGramConfig.saveDeletedMessages = saved
        MZGramConfig.saveArchiveMedia = media
    }

    private fun newMessageId(): Int = 2_000_000 + (Math.random() * 1_000_000).toInt()

    private fun incoming(mid: Int, text: String): TLRPC.Message {
        val m = TLRPC.TL_message()
        m.id = mid
        m.date = (System.currentTimeMillis() / 1000).toInt()
        m.message = text
        m.out = false
        m.from_id = TLRPC.TL_peerUser().also { it.user_id = otherUserId }
        m.peer_id = TLRPC.TL_peerUser().also { it.user_id = otherUserId }
        m.dialog_id = otherUserId
        m.flags = m.flags or 256
        return m
    }

    private fun drainAll() {
        repeat(3) {
            for (queue in listOf(Utilities.stageQueue, storage.storageQueue)) {
                val latch = CountDownLatch(1)
                queue.postRunnable { latch.countDown() }
                assertTrue("queue drained", latch.await(30, TimeUnit.SECONDS))
            }
        }
    }

    // The message comes: what putMessages tells MZGram, without Telegram's
    // cache keeping it.
    private fun comes(message: TLRPC.Message) {
        storage.storageQueue.postRunnable {
            MZGramHistoryController.getInstance().onMessagesStored(account, arrayListOf(message))
        }
        drainAll()
    }

    private fun deleted(mid: Int) {
        val update = TL_update.TL_updateDeleteMessages()
        update.messages.add(mid)
        Utilities.stageQueue.postRunnable {
            controller.processUpdateArray(arrayListOf(update), ArrayList(), ArrayList(), false, 0)
        }
        drainAll()
    }

    @Test
    fun limits_byTheKindOfChat_and100MbMoreWhenDeleted() {
        val mb = 1024L * 1024L
        assertEquals(300, MZGramArchiveLimits.defaultMb(MZGramKeepFrom.USER))
        assertEquals(5, MZGramArchiveLimits.defaultMb(MZGramKeepFrom.BOT))
        assertEquals(20, MZGramArchiveLimits.defaultMb(MZGramKeepFrom.GROUP))
        assertEquals(5, MZGramArchiveLimits.defaultMb(MZGramKeepFrom.CHANNEL))
        assertTrue(MZGramArchiveLimits.fetches(300 * mb, 300, false))
        assertFalse(MZGramArchiveLimits.fetches(300 * mb + 1, 300, false))
        assertTrue(MZGramArchiveLimits.fetches(400 * mb, 300, true))
        assertFalse(MZGramArchiveLimits.fetches(400 * mb + 1, 300, true))
        assertTrue(MZGramArchiveLimits.fetches(105 * mb, 5, true))
        assertFalse(MZGramArchiveLimits.fetches(6 * mb, 5, false))
        assertTrue("no limit", MZGramArchiveLimits.fetches(5000 * mb, MZGramArchiveLimits.NO_LIMIT, false))
        assertTrue("an unknown size counts as small", MZGramArchiveLimits.fetches(0, 0, false))
        val before = MZGramArchiveLimits.limitMb(MZGramKeepFrom.BOT)
        MZGramArchiveLimits.setLimitMb(MZGramKeepFrom.BOT, MZGramArchiveLimits.NO_LIMIT)
        assertEquals(MZGramArchiveLimits.NO_LIMIT, MZGramArchiveLimits.limitMb(MZGramKeepFrom.BOT))
        MZGramArchiveLimits.setLimitMb(MZGramKeepFrom.BOT, before)
        assertEquals(MZGramArchiveLimits.NO_LIMIT, MZGramArchiveLimits.CHOICES.last())
    }

    @Test
    fun counters_countAndReset() {
        MZGramArchiveLimits.resetCounters()
        MZGramArchiveLimits.count(MZGramArchiveLimits.SAVED_AHEAD)
        MZGramArchiveLimits.count(MZGramArchiveLimits.EXPIRED)
        MZGramArchiveLimits.count(MZGramArchiveLimits.EXPIRED)
        MZGramArchiveLimits.noteExpired("a.jpg")
        assertEquals(MZGramArchiveLimits.EXPIRED, MZGramArchiveLimits.failure("a.jpg"))
        assertEquals(MZGramArchiveLimits.FAILED, MZGramArchiveLimits.failure("a.jpg"))
        assertEquals(1, MZGramArchiveLimits.counter(MZGramArchiveLimits.SAVED_AHEAD))
        assertEquals(2, MZGramArchiveLimits.counter(MZGramArchiveLimits.EXPIRED))
        MZGramArchiveLimits.resetCounters()
        for (i in 0 until MZGramArchiveLimits.COUNTERS) {
            assertEquals(0, MZGramArchiveLimits.counter(i))
        }
    }

    // Level 1: the message kept as it came is archived when it is deleted,
    // although Telegram's cache never had it.
    @Test
    fun messageKeptAsItCame_isArchivedWithoutTheCache() {
        val mid = newMessageId()
        comes(incoming(mid, "kept as it came $mid"))
        assertEquals(null, storage.getMessage(otherUserId, mid.toLong()))
        deleted(mid)
        val row = MZGramHistoryDatabase.getInstance().getDeleted(selfId, otherUserId, mid)
        assertNotNull("archived from the message kept as it came", row)
        assertEquals("kept as it came $mid", row!!.text)
        assertEquals("the kept copy goes once archived", null, MZGramHistoryDatabase.getInstance().getReceived(selfId, otherUserId, mid))
    }

    // A deletion of a message this device never had counts as deleted
    // without content.
    @Test
    fun deletedWithoutContent_isCounted() {
        MZGramArchiveLimits.resetCounters()
        deleted(newMessageId())
        assertEquals(1, MZGramArchiveLimits.counter(MZGramArchiveLimits.DELETED_WITHOUT_CONTENT))
        MZGramArchiveLimits.resetCounters()
    }

    // Level 2: a file already on the device when the message comes is
    // copied into the archive at once, and goes with the message when it
    // is deleted, after Telegram's cache lost it.
    @Test
    fun fileOnTheDevice_isKeptInAdvance() {
        MZGramArchiveLimits.resetCounters()
        val mid = newMessageId()
        val message = incoming(mid, "with a file $mid")
        message.media = TLRPC.TL_messageMediaDocument().also { m ->
            m.document = TLRPC.TL_document().also { d ->
                d.id = 500_000_000_000L + mid
                d.access_hash = 1
                d.dc_id = 2
                d.date = message.date
                d.size = 2048
                d.mime_type = "application/pdf"
                d.file_reference = ByteArray(0)
                d.attributes.add(TLRPC.TL_documentAttributeFilename().also { a -> a.file_name = "levels-$mid.pdf" })
            }
            m.flags = m.flags or 1
        }
        message.flags = message.flags or 512
        val local = FileLoader.getInstance(account).getPathToMessage(message)
        local.parentFile?.mkdirs()
        local.writeBytes(ByteArray(2048) { 5 })
        var kept: File? = null
        try {
            comes(message)
            val received = MZGramHistoryDatabase.getInstance().getReceived(selfId, otherUserId, mid)
            assertNotNull(received)
            assertNotNull("the file kept in advance", received!!.mediaPath)
            kept = File(received.mediaPath)
            assertEquals(2048L, kept.length())
            assertEquals(1, MZGramArchiveLimits.counter(MZGramArchiveLimits.SAVED_AHEAD))
            local.delete()
            deleted(mid)
            val row = MZGramHistoryDatabase.getInstance().getDeleted(selfId, otherUserId, mid)
            assertNotNull(row)
            assertEquals("the deleted message has the file kept in advance", kept.absolutePath, row!!.mediaPath)
        } finally {
            local.delete()
            kept?.delete()
            MZGramArchiveLimits.resetCounters()
        }
    }
}
