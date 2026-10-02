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
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramHistoryMessage
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The archive's data layer, path by path: which messages end up in the
// MZGram archive and which do not. Every test stores real rows in
// Telegram's own cache (messages_v2) and then drives the same code the app
// runs -- an update through MessagesController, the self-destruct timer,
// MessagesStorage.emptyMessagesMedia, MessagesStorage.overwriteChannel --
// and reads the archive database afterwards.
//
// Rules checked here:
//   - only OTHER people's messages are archived, never the account owner's;
//   - a message removed by the auto-delete timer is archived;
//   - media removed from a message (self-destructing media) keeps its file;
//   - after channelDifferenceTooLong only cached messages the server no
//     longer has are archived, never ones it still returned;
//   - every edit of another person's message keeps the previous revision.
class MZGramArchiveDataLayerTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_200_000_000L + (Math.random() * 1_000_000).toLong()
    // A fresh channel per test: MessagesController caches each channel's
    // pts in memory, so reusing one id would carry pts across tests.
    private val channelId = 7_300_000_000L + (Math.random() * 1_000_000).toLong()
    private val channelDialogId = -channelId

    private var savedSaveMessageHistory = false

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)
    private val db get() = MZGramHistoryDatabase.getInstance()

    private fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.putUser(user(otherUserId), false)
            controller.putChat(channel(), false)
        }
        savedSaveMessageHistory = MZGramConfig.saveMessageHistory
        MZGramConfig.saveMessageHistory = true
        MZGramConfig.setDialogTracked(otherUserId, true)
        MZGramConfig.setDialogTracked(channelDialogId, true)
    }

    @After
    fun tearDown() {
        MZGramConfig.setDialogTracked(otherUserId, false)
        MZGramConfig.setDialogTracked(channelDialogId, false)
        MZGramConfig.saveMessageHistory = savedSaveMessageHistory
    }

    // ---- fixtures ----

    private fun now() = ConnectionsManager.getInstance(account).currentTime

    private fun newMessageId(): Int = 100_000 + (Math.random() * 1_000_000).toInt()

    private fun userPeer(id: Long) = TLRPC.TL_peerUser().also { it.user_id = id }

    private fun user(id: Long) = TLRPC.TL_user().also {
        it.id = id
        it.access_hash = 1
        it.first_name = "MZGram test other"
    }

    private fun channel() = TLRPC.TL_channel().also {
        it.id = channelId
        it.access_hash = 1
        it.title = "MZGram test channel"
        it.megagroup = true
    }

    private fun incomingPrivate(mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.from_id = userPeer(otherUserId)
        it.peer_id = userPeer(otherUserId)
        it.dialog_id = otherUserId
        it.flags = it.flags or 256
    }

    private fun outgoingPrivate(mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.out = true
        it.from_id = userPeer(selfId)
        it.peer_id = userPeer(otherUserId)
        it.dialog_id = otherUserId
        it.flags = it.flags or 256 or 2
    }

    private fun incomingChannel(mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.from_id = userPeer(otherUserId)
        it.peer_id = TLRPC.TL_peerChannel().also { p -> p.channel_id = channelId }
        it.dialog_id = channelDialogId
        it.flags = it.flags or 256
    }

    private fun edited(message: TLRPC.Message, text: String): TLRPC.Message {
        val copy = if (message.peer_id is TLRPC.TL_peerChannel) incomingChannel(message.id, text)
        else if (message.out) outgoingPrivate(message.id, text) else incomingPrivate(message.id, text)
        copy.date = message.date
        copy.edit_date = now()
        copy.flags = copy.flags or 32768
        return copy
    }

    // ---- queues ----

    private fun drain(queue: DispatchQueue) {
        val latch = CountDownLatch(1)
        queue.postRunnable { latch.countDown() }
        assertEquals("queue drained", true, latch.await(30, TimeUnit.SECONDS))
    }

    private fun drainAll() {
        repeat(4) {
            drain(Utilities.stageQueue)
            drain(storage.storageQueue)
        }
    }

    private fun onStage(block: () -> Unit) {
        Utilities.stageQueue.postRunnable { block() }
        drainAll()
    }

    private fun waitFor(timeoutSec: Int, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            if (check()) {
                return true
            }
            Thread.sleep(250)
        }
        return check()
    }

    private fun putInCache(message: TLRPC.Message) {
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        assertNotNull("message is in messages_v2",
            storage.getMessage(message.dialog_id, message.id.toLong()))
    }

    private fun cached(dialogId: Long, mid: Int) = storage.getMessage(dialogId, mid.toLong())

    private fun deliver(vararg updates: TLRPC.Update, fromGetDifference: Boolean = false) = onStage {
        controller.processUpdateArray(arrayListOf(*updates), ArrayList(), ArrayList(), fromGetDifference, 0)
    }

    private fun deleteUpdate(vararg mids: Int) = TL_update.TL_updateDeleteMessages().also {
        mids.forEach { mid -> it.messages.add(mid) }
    }

    // ---- archive reads ----

    private fun archivedDeleted(dialogId: Long, mid: Int) = db.getDeleted(selfId, dialogId, mid)

    private fun rows(dialogId: Long, mid: Int) =
        db.getAllForDialog(selfId, dialogId, 10_000).filter { it.messageId == mid }

    private fun describe(dialogId: Long, mid: Int): String {
        val all = rows(dialogId, mid)
        return "inMessagesV2=${cached(dialogId, mid) != null} archive=" +
            (if (all.isEmpty()) "NONE" else all.joinToString { "kind=${it.kind} rowId=${it.rowId} text='${it.text}' mediaPath=${it.mediaPath}" })
    }

    // ---- own messages are never archived ----

    // The echo of a deletion of the owner's own message -- deleted from
    // another device, or by a group admin. Only other people's messages
    // are archived.
    @Test
    fun own_messageDeletedByUpdate_isNotArchived() {
        val mid = newMessageId()
        putInCache(outgoingPrivate(mid, "own message $mid"))
        deliver(deleteUpdate(mid))
        log("own deleted by update: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertNull("deleted from messages_v2", cached(otherUserId, mid))
        assertNull("own message is not archived", archivedDeleted(otherUserId, mid))
    }

    // The owner's own edit, arriving as an update (edited on another device).
    @Test
    fun own_messageEditedByUpdate_keepsNoRevision() {
        val mid = newMessageId()
        val original = outgoingPrivate(mid, "own before $mid")
        putInCache(original)
        deliver(TL_update.TL_updateEditMessage().also { it.message = edited(original, "own after $mid") })
        log("own edited by update: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertEquals("own after $mid", cached(otherUserId, mid)!!.message)
        assertTrue("own edit keeps no revision", db.getRevisions(selfId, otherUserId, mid).isEmpty())
    }

    // ---- edit history of other people's messages ----

    @Test
    fun edit_privateMessageByUpdateEditMessage_keepsPreviousRevision() {
        val mid = newMessageId()
        val original = incomingPrivate(mid, "before $mid")
        putInCache(original)
        deliver(TL_update.TL_updateEditMessage().also { it.message = edited(original, "after $mid") })
        log("edit private: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertEquals("after $mid", cached(otherUserId, mid)!!.message)
        val revisions = db.getRevisions(selfId, otherUserId, mid)
        assertEquals(listOf("before $mid"), revisions.map { it.text })
        assertEquals(otherUserId, revisions[0].fromId)
    }

    @Test
    fun edit_channelMessageByUpdateEditChannelMessage_keepsPreviousRevision() {
        val mid = newMessageId()
        val original = incomingChannel(mid, "channel before $mid")
        putInCache(original)
        deliver(TL_update.TL_updateEditChannelMessage().also { it.message = edited(original, "channel after $mid") })
        log("edit channel: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        assertEquals("channel after $mid", cached(channelDialogId, mid)!!.message)
        assertEquals(listOf("channel before $mid"), db.getRevisions(selfId, channelDialogId, mid).map { it.text })
    }

    // Edits made while the app was offline come back from getDifference in
    // other_updates and go through processUpdateArray with
    // fromGetDifference = true.
    @Test
    fun edit_deliveredThroughGetDifference_keepsPreviousRevision() {
        val mid = newMessageId()
        val original = incomingPrivate(mid, "offline before $mid")
        putInCache(original)
        deliver(TL_update.TL_updateEditMessage().also { it.message = edited(original, "offline after $mid") }, fromGetDifference = true)
        log("edit getDifference: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertEquals(listOf("offline before $mid"), db.getRevisions(selfId, otherUserId, mid).map { it.text })
    }

    @Test
    fun edit_twice_keepsBothPreviousRevisionsInOrder() {
        val mid = newMessageId()
        val original = incomingPrivate(mid, "v1 $mid")
        putInCache(original)
        deliver(TL_update.TL_updateEditMessage().also { it.message = edited(original, "v2 $mid") })
        deliver(TL_update.TL_updateEditMessage().also { it.message = edited(original, "v3 $mid") })
        log("edit twice: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertEquals(listOf("v1 $mid", "v2 $mid"), db.getRevisions(selfId, otherUserId, mid).map { it.text })
    }

    // ---- auto-delete timer ----

    // A message with ttl_period (the chat's auto-delete timer) is removed by
    // the client's own delete task, without any delete update from the
    // server. The server has deleted it too, so it is archived.
    @Test
    fun ttl_othersMessageRemovedByAutoDeleteTimer_isArchived() {
        val mid = newMessageId()
        val message = incomingPrivate(mid, "auto-delete $mid")
        message.date = now() - 60
        message.ttl_period = 5
        message.flags = message.flags or 33554432
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        controller.didAddedNewTask(now() - 1, 0, null)
        val removed = waitFor(60) { cached(otherUserId, mid) == null }
        drainAll()
        log("ttl expired: dialog=$otherUserId mid=$mid removedByTimer=$removed ${describe(otherUserId, mid)}")
        assertTrue("delete task removed the expired message", removed)
        val row = archivedDeleted(otherUserId, mid)
        assertNotNull("expired message of another user is archived", row)
        assertEquals("auto-delete $mid", row!!.text)
    }

    @Test
    fun ttl_ownMessageRemovedByAutoDeleteTimer_isNotArchived() {
        val mid = newMessageId()
        val message = outgoingPrivate(mid, "own auto-delete $mid")
        message.date = now() - 60
        message.ttl_period = 5
        message.flags = message.flags or 33554432
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        controller.didAddedNewTask(now() - 1, 0, null)
        val removed = waitFor(60) { cached(otherUserId, mid) == null }
        drainAll()
        log("ttl own expired: dialog=$otherUserId mid=$mid removedByTimer=$removed ${describe(otherUserId, mid)}")
        assertTrue("delete task removed the expired message", removed)
        assertNull("own message is not archived", archivedDeleted(otherUserId, mid))
    }

    // ---- media removed from a message ----

    // Self-destructing media: when its timer runs out (or a view-once file
    // was viewed), MessagesStorage.emptyMessagesMedia replaces the media
    // with an empty one and deletes the file. The archive keeps a copy of
    // the file itself.
    @Test
    fun media_removedFromOthersMessage_fileIsKeptInArchive() {
        val mid = newMessageId()
        val message = incomingPrivate(mid, "")
        val bytes = ByteArray(4096) { (it * 31 + mid).toByte() }
        val document = TLRPC.TL_document().also {
            it.id = 900_000_000_000L + mid
            it.access_hash = 1
            it.dc_id = 2
            it.date = message.date
            it.mime_type = "application/octet-stream"
            it.size = bytes.size.toLong()
            it.file_reference = ByteArray(0)
            it.attributes.add(TLRPC.TL_documentAttributeFilename().also { a -> a.file_name = "mzgram_ttl_$mid.bin" })
        }
        message.media = TLRPC.TL_messageMediaDocument().also {
            it.document = document
            it.ttl_seconds = 10
            it.flags = it.flags or 1 or 4
        }
        message.flags = message.flags or 512
        putInCache(message)

        // The downloaded file, where Telegram keeps it for this message.
        val file = FileLoader.getInstance(account).getPathToMessage(message)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        assertTrue("downloaded file exists at $file", file.exists())

        storage.emptyMessagesMedia(otherUserId, arrayListOf(mid))
        drainAll()

        val after = cached(otherUserId, mid)
        log("media removed: dialog=$otherUserId mid=$mid mediaAfter=${after?.media?.document?.javaClass?.simpleName} ${describe(otherUserId, mid)}")
        assertTrue("media emptied in messages_v2", after!!.media.document is TLRPC.TL_documentEmpty)
        val withFile = rows(otherUserId, mid).firstOrNull { it.mediaPath != null }
        assertNotNull("archive row with the media file", withFile)
        val kept = File(withFile!!.mediaPath)
        assertTrue("archived file exists at $kept", kept.exists())
        assertTrue("archived file has the original bytes", kept.readBytes().contentEquals(bytes))
        assertEquals(MZGramHistoryMessage.KIND_VIEW_ONCE, withFile.kind)
    }

    // ---- channelDifferenceTooLong ----

    // After a long gap the server answers getChannelDifference with
    // channelDifferenceTooLong: the latest messages of the channel, and no
    // list of what was deleted meanwhile. overwriteChannel then drops the
    // whole channel from messages_v2. A cached message whose id lies inside
    // the returned range but which the server did not return is gone from
    // the server and is archived; returned messages and messages older than
    // the returned range (unknown, may still exist) are not.
    @Test
    fun tooLong_archivesOnlyCachedMessagesMissingFromReturnedRange() {
        val base = newMessageId()
        val older = base
        val kept1 = base + 10
        val deletedMeanwhile = base + 20
        val kept2 = base + 30
        for (mid in listOf(older, kept1, deletedMeanwhile, kept2)) {
            putInCache(incomingChannel(mid, "channel $mid"))
        }

        val diff = TLRPC.TL_updates_channelDifferenceTooLong()
        diff.dialog = TLRPC.TL_dialog().also {
            it.peer = TLRPC.TL_peerChannel().also { p -> p.channel_id = channelId }
            it.top_message = kept2
        }
        diff.messages.add(incomingChannel(kept1, "channel $kept1"))
        diff.messages.add(incomingChannel(kept2, "channel $kept2"))
        diff.chats.add(channel())
        val latch = CountDownLatch(1)
        storage.overwriteChannel(channelId, diff, 0) { latch.countDown() }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        drainAll()

        for (mid in listOf(older, kept1, deletedMeanwhile, kept2)) {
            log("tooLong: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        }
        assertNotNull("message missing from the returned range is archived", archivedDeleted(channelDialogId, deletedMeanwhile))
        assertEquals("channel $deletedMeanwhile", archivedDeleted(channelDialogId, deletedMeanwhile)!!.text)
        assertNull("returned message still exists on the server", archivedDeleted(channelDialogId, kept1))
        assertNull("returned message still exists on the server", archivedDeleted(channelDialogId, kept2))
        assertNull("older than the returned range: unknown, not archived", archivedDeleted(channelDialogId, older))
    }

    // No messages returned: no range is known, nothing is archived.
    @Test
    fun tooLong_withoutReturnedMessages_archivesNothing() {
        val mid = newMessageId()
        putInCache(incomingChannel(mid, "channel $mid"))
        val diff = TLRPC.TL_updates_channelDifferenceTooLong()
        diff.dialog = TLRPC.TL_dialog().also { it.peer = TLRPC.TL_peerChannel().also { p -> p.channel_id = channelId } }
        val latch = CountDownLatch(1)
        storage.overwriteChannel(channelId, diff, 0) { latch.countDown() }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        drainAll()
        log("tooLong empty: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        assertNull(archivedDeleted(channelDialogId, mid))
    }
}
