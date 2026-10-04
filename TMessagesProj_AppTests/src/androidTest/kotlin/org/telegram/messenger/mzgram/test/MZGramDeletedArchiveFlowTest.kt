package org.telegram.messenger.mzgram.test

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// End-to-end check of the deleted-message archive on the real code path:
// a message is stored in Telegram's own cache (messages_v2), then a
// synthetic TL_updateDeleteMessages / TL_updateDeleteChannelMessages is fed
// into MessagesController.processUpdateArray -- exactly what the network
// layer does when the server reports a deletion -- and the test reads the
// MZGram archive database afterwards. Nothing between the update and the
// archive row is mocked.
class MZGramDeletedArchiveFlowTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_000_000_002L
    private val newChatUserId = 7_000_000_003L
    private val channelId = 7_000_000_004L
    private val channelDialogId = -channelId

    private var savedSaveMessageHistory = false

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        // MessagesController expects to be created on the main thread.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller }

        savedSaveMessageHistory = MZGramConfig.saveMessageHistory
        MZGramConfig.saveMessageHistory = true
    }

    @After
    fun tearDown() {
        MZGramConfig.saveMessageHistory = savedSaveMessageHistory
    }

    private fun newMessageId(): Int = 100_000 + (Math.random() * 1_000_000).toInt()

    private fun userPeer(id: Long) = TLRPC.TL_peerUser().also { it.user_id = id }

    private fun incomingPrivate(mid: Int, fromUser: Long, text: String): TLRPC.Message {
        val m = TLRPC.TL_message()
        m.id = mid
        m.date = (System.currentTimeMillis() / 1000).toInt()
        m.message = text
        m.out = false
        m.unread = true
        m.from_id = userPeer(fromUser)
        m.peer_id = userPeer(fromUser)
        m.dialog_id = fromUser
        m.flags = m.flags or 256
        return m
    }

    private fun outgoingPrivate(mid: Int, toUser: Long, text: String): TLRPC.Message {
        val m = TLRPC.TL_message()
        m.id = mid
        m.date = (System.currentTimeMillis() / 1000).toInt()
        m.message = text
        m.out = true
        m.from_id = userPeer(selfId)
        m.peer_id = userPeer(toUser)
        m.dialog_id = toUser
        m.flags = m.flags or 256 or 2
        return m
    }

    private fun incomingChannel(mid: Int, fromUser: Long, text: String): TLRPC.Message {
        val m = TLRPC.TL_message()
        m.id = mid
        m.date = (System.currentTimeMillis() / 1000).toInt()
        m.message = text
        m.out = false
        m.from_id = userPeer(fromUser)
        m.peer_id = TLRPC.TL_peerChannel().also { it.channel_id = channelId }
        m.dialog_id = channelDialogId
        m.flags = m.flags or 256
        return m
    }

    private fun drain(queue: org.telegram.messenger.DispatchQueue) {
        val latch = CountDownLatch(1)
        queue.postRunnable { latch.countDown() }
        assertEquals("queue drained", true, latch.await(30, TimeUnit.SECONDS))
    }

    // Lets every chained hop (stage -> storage -> storage) finish.
    private fun drainAll() {
        repeat(3) {
            drain(Utilities.stageQueue)
            drain(storage.storageQueue)
        }
    }

    private fun putInCache(message: TLRPC.Message) {
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        assertNotNull("message is in messages_v2 before the delete",
            storage.getMessage(message.dialog_id, message.id.toLong()))
    }

    // Same entry point ConnectionsManager -> processUpdates uses, run on the
    // same queue it normally runs on.
    private fun deliverUpdate(update: TLRPC.Update) {
        Utilities.stageQueue.postRunnable {
            controller.processUpdateArray(arrayListOf(update), ArrayList(), ArrayList(), false, 0)
        }
        drainAll()
    }

    private fun archived(dialogId: Long, mid: Int) =
        MZGramHistoryDatabase.getInstance().getDeleted(selfId, dialogId, mid)

    private fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    @Test
    fun otherUserDeletesInPrivateChat_isArchived() {
        val mid = newMessageId()
        putInCache(incomingPrivate(mid, otherUserId, "secret from other user $mid"))

        val update = TL_update.TL_updateDeleteMessages()
        update.messages.add(mid)
        deliverUpdate(update)

        assertNull("deleted from messages_v2", storage.getMessage(otherUserId, mid.toLong()))
        val row = archived(otherUserId, mid)
        log("private chat: dialog=$otherUserId mid=$mid archivedRow=" +
            (row?.let { "rowId=${it.rowId} fromId=${it.fromId} text='${it.text}'" } ?: "NONE"))
        assertNotNull("other user's deleted message archived", row)
        assertEquals("secret from other user $mid", row!!.text)
        assertEquals(otherUserId, row.fromId)
    }

    @Test
    fun otherUserDeletesInChannel_isArchived() {
        val mid = newMessageId()
        putInCache(incomingChannel(mid, otherUserId, "channel post $mid"))

        val update = TL_update.TL_updateDeleteChannelMessages()
        update.channel_id = channelId
        update.messages.add(mid)
        deliverUpdate(update)

        assertNull("deleted from messages_v2", storage.getMessage(channelDialogId, mid.toLong()))
        val row = archived(channelDialogId, mid)
        log("channel: dialog=$channelDialogId mid=$mid archivedRow=" +
            (row?.let { "rowId=${it.rowId} fromId=${it.fromId} text='${it.text}'" } ?: "NONE"))
        assertNotNull("other user's deleted channel message archived", row)
        assertEquals("channel post $mid", row!!.text)
    }

    @Test
    fun otherUserDeletesInAnyChat_isArchivedWithoutAddingIt() {
        val mid = newMessageId()
        putInCache(incomingPrivate(mid, newChatUserId, "new chat $mid"))

        val update = TL_update.TL_updateDeleteMessages()
        update.messages.add(mid)
        deliverUpdate(update)

        assertNull("deleted from messages_v2", storage.getMessage(newChatUserId, mid.toLong()))
        val row = archived(newChatUserId, mid)
        log("new chat: dialog=$newChatUserId mid=$mid archivedRow=" + (row?.rowId ?: "NONE"))
        assertNotNull("archived in a chat never added anywhere", row)
        assertEquals("new chat $mid", row!!.text)
    }

    @Test
    fun archiveSwitchedOff_isNotArchived() {
        MZGramConfig.saveMessageHistory = false
        val mid = newMessageId()
        putInCache(incomingPrivate(mid, otherUserId, "archive off $mid"))

        val update = TL_update.TL_updateDeleteMessages()
        update.messages.add(mid)
        deliverUpdate(update)

        assertNull("deleted from messages_v2", storage.getMessage(otherUserId, mid.toLong()))
        assertNull("nothing saved with the switch off", archived(otherUserId, mid))
    }

    // The owner's own messages are never archived: neither on the local
    // "Delete for everyone" tap nor when the server's echo arrives.
    @Test
    fun ownDeleteForEveryone_isNotArchived() {
        val mid = newMessageId()
        putInCache(outgoingPrivate(mid, otherUserId, "own message $mid"))

        // The local "Delete for everyone" tap, then the server's echo.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.deleteMessages(arrayListOf(mid), null, null, otherUserId, 0, true, 0)
        }
        drainAll()
        val update = TL_update.TL_updateDeleteMessages()
        update.messages.add(mid)
        deliverUpdate(update)

        val row = archived(otherUserId, mid)
        log("own delete: dialog=$otherUserId mid=$mid archivedRow=" +
            (row?.let { "rowId=${it.rowId} fromId=${it.fromId} text='${it.text}'" } ?: "NONE"))
        assertNull("deleted from messages_v2", storage.getMessage(otherUserId, mid.toLong()))
        assertNull("own deleted message is not archived", row)
    }

    // Media of any size is archived: a file over the old 50 MB limit is
    // kept whole, and a file already in the archive is not deleted to make
    // room for it (there is no total quota).
    @Test
    fun bigFile_isArchivedWithoutASizeLimit_andNothingIsDeleted() {
        val bigFile = 60L * 1024 * 1024
        val earlier = File(MZGramHistoryDatabase.mediaDir(selfId, otherUserId), "earlier-${newMessageId()}.bin")
        earlier.parentFile?.mkdirs()
        earlier.writeBytes(ByteArray(1024) { 7 })
        earlier.setLastModified(1_000_000L)

        val mid = newMessageId()
        val message = incomingPrivate(mid, otherUserId, "big file $mid")
        message.media = TLRPC.TL_messageMediaDocument().also { m ->
            m.document = TLRPC.TL_document().also { d ->
                d.id = 400_000_000_000L + mid
                d.access_hash = 1
                d.dc_id = 2
                d.date = message.date
                d.size = bigFile
                d.mime_type = "application/zip"
                d.file_reference = ByteArray(0)
                d.attributes.add(TLRPC.TL_documentAttributeFilename().also { a -> a.file_name = "big-$mid.zip" })
            }
            m.flags = m.flags or 1
        }
        message.flags = message.flags or 512
        putInCache(message)

        val downloaded = FileLoader.getInstance(account).getPathToMessage(message)
        downloaded.parentFile?.mkdirs()
        RandomAccessFile(downloaded, "rw").use {
            it.setLength(bigFile)
            it.seek(bigFile - 4)
            it.writeInt(mid)
        }
        var saved: File? = null
        try {
            val update = TL_update.TL_updateDeleteMessages()
            update.messages.add(mid)
            deliverUpdate(update)

            val row = archived(otherUserId, mid)
            log("big file: mid=$mid archivedRow=" + (row?.let { "mediaPath=${it.mediaPath}" } ?: "NONE"))
            assertNotNull("deleted message archived", row)
            assertNotNull("its file archived too", row!!.mediaPath)
            saved = File(row.mediaPath)
            assertEquals("the whole file, over 50 MB", bigFile, saved.length())
            RandomAccessFile(saved, "r").use {
                it.seek(bigFile - 4)
                assertEquals("same bytes", mid, it.readInt())
            }
            assertTrue("a file saved earlier is not deleted", earlier.exists())
        } finally {
            downloaded.delete()
            earlier.delete()
            saved?.delete()
        }
    }
}
