package org.telegram.messenger.mzgram.test

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The steps BEFORE MessagesController.processUpdateArray, where a deletion
// by another user could get lost on a real device. MZGramDeletedArchiveFlowTest
// already shows processUpdateArray itself archives; these tests go through
// the real network entry point (processUpdates with pts/seq bookkeeping, the
// queues used when updates arrive out of order, the getDifference path) and
// through the storage paths that remove messages without any delete update.
class MZGramDeletedArchiveUpstreamTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_100_000_002L
    // A fresh channel per test: MessagesController caches each channel's
    // pts in memory, so reusing one id would carry pts across tests.
    private val channelId = 7_100_000_000L + (Math.random() * 1_000_000).toLong()
    private val channelDialogId = -channelId

    private var savedSaveMessageHistory = false

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)

    private fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller }
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

    private fun newMessageId(): Int = 100_000 + (Math.random() * 1_000_000).toInt()

    private fun userPeer(id: Long) = TLRPC.TL_peerUser().also { it.user_id = id }

    private fun incomingPrivate(mid: Int, text: String): TLRPC.Message {
        val m = TLRPC.TL_message()
        m.id = mid
        m.date = (System.currentTimeMillis() / 1000).toInt()
        m.message = text
        m.from_id = userPeer(otherUserId)
        m.peer_id = userPeer(otherUserId)
        m.dialog_id = otherUserId
        m.flags = m.flags or 256
        return m
    }

    private fun incomingChannel(mid: Int, text: String): TLRPC.Message {
        val m = TLRPC.TL_message()
        m.id = mid
        m.date = (System.currentTimeMillis() / 1000).toInt()
        m.message = text
        m.from_id = userPeer(otherUserId)
        m.peer_id = TLRPC.TL_peerChannel().also { it.channel_id = channelId }
        m.dialog_id = channelDialogId
        m.flags = m.flags or 256
        return m
    }

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

    private fun putInCache(message: TLRPC.Message) {
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        assertNotNull("message is in messages_v2 before the delete",
            storage.getMessage(message.dialog_id, message.id.toLong()))
    }

    private fun onStage(block: () -> Unit) {
        Utilities.stageQueue.postRunnable { block() }
        drainAll()
    }

    private fun archived(dialogId: Long, mid: Int) =
        MZGramHistoryDatabase.getInstance().getDeleted(selfId, dialogId, mid)

    private fun describe(dialogId: Long, mid: Int): String {
        val row = archived(dialogId, mid)
        val cached = storage.getMessage(dialogId, mid.toLong()) != null
        return "inMessagesV2=$cached archivedRow=" +
            (row?.let { "rowId=${it.rowId} fromId=${it.fromId} text='${it.text}'" } ?: "NONE")
    }

    // A server container exactly as ConnectionsManager hands it to
    // processUpdates: TL_updates with pts bookkeeping, seq 0.
    private fun container(vararg updates: TLRPC.Update): TLRPC.TL_updates {
        val c = TLRPC.TL_updates()
        c.updates.addAll(updates)
        c.date = (System.currentTimeMillis() / 1000).toInt()
        c.seq = 0
        return c
    }

    private fun deleteUpdate(pts: Int, vararg mids: Int) = TL_update.TL_updateDeleteMessages().also {
        mids.forEach { mid -> it.messages.add(mid) }
        it.pts = pts
        it.pts_count = mids.size
    }

    private fun deleteChannelUpdate(pts: Int, vararg mids: Int) = TL_update.TL_updateDeleteChannelMessages().also {
        it.channel_id = channelId
        mids.forEach { mid -> it.messages.add(mid) }
        it.pts = pts
        it.pts_count = mids.size
    }

    private fun setCommonPts(pts: Int) {
        storage.setLastPtsValue(pts)
        storage.setLastSeqValue(0)
    }

    // (a) The delete arrives for a message this device never stored (or no
    // longer has). Nothing to archive: the content was never here.
    @Test
    fun a_deleteForMessageNotInCache_hasNothingToArchive() {
        val mid = newMessageId()
        onStage {
            controller.processUpdateArray(arrayListOf<TLRPC.Update>(deleteUpdate(0, mid)), ArrayList(), ArrayList(), false, 0)
        }
        log("(a) not in cache: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertNull(archived(otherUserId, mid))
    }

    // (a) channelDifferenceTooLong: after a long gap the client drops the
    // whole channel from messages_v2 and reloads it. Messages deleted during
    // the gap vanish here without any delete update ever being processed.
    @Test
    fun a_channelDifferenceTooLong_dropsTrackedMessagesWithoutDeleteUpdate() {
        val mid = newMessageId()
        putInCache(incomingChannel(mid, "will vanish in tooLong $mid"))

        val diff = TLRPC.TL_updates_channelDifferenceTooLong()
        val dialog = TLRPC.TL_dialog()
        dialog.peer = TLRPC.TL_peerChannel().also { it.channel_id = channelId }
        diff.dialog = dialog
        val latch = CountDownLatch(1)
        storage.overwriteChannel(channelId, diff, 0) { latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
        drainAll()

        log("(a) channelDifferenceTooLong: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        assertNull("overwriteChannel removed the message", storage.getMessage(channelDialogId, mid.toLong()))
        assertNull("no delete update ran, so nothing was archived", archived(channelDialogId, mid))
    }

    // (b) getDifference: deletions that happened while the app was offline
    // come back in difference.other_updates and are passed to
    // processUpdateArray with fromGetDifference = true.
    @Test
    fun b_deleteDeliveredThroughGetDifference_isArchived() {
        val mid = newMessageId()
        putInCache(incomingPrivate(mid, "deleted while offline $mid"))
        onStage {
            controller.processUpdateArray(arrayListOf<TLRPC.Update>(deleteUpdate(0, mid)), ArrayList(), ArrayList(), true, 0)
        }
        log("(b) getDifference: dialog=$otherUserId mid=$mid ${describe(otherUserId, mid)}")
        assertNotNull(archived(otherUserId, mid))
    }

    // (d) The real network entry point: a TL_updates container through
    // processUpdates, with the pts check a live update goes through.
    @Test
    fun d_deleteThroughProcessUpdatesInOrder_isArchived() {
        val mid = newMessageId()
        putInCache(incomingPrivate(mid, "live private $mid"))
        setCommonPts(5000)
        onStage { controller.processUpdates(container(deleteUpdate(5001, mid)), false) }
        log("(d) processUpdates in order: dialog=$otherUserId mid=$mid lastPts=${storage.lastPtsValue} ${describe(otherUserId, mid)}")
        assertNotNull(archived(otherUserId, mid))
    }

    // (d) Out of order: the delete arrives before the update it follows, is
    // parked in the pts queue, and is applied once the gap is filled.
    @Test
    fun d_deleteThroughProcessUpdatesAfterPtsGap_isArchived() {
        val mid = newMessageId()
        val filler = newMessageId()
        putInCache(incomingPrivate(mid, "gap private $mid"))
        setCommonPts(6000)
        onStage { controller.processUpdates(container(deleteUpdate(6002, mid)), false) }
        log("(d) after gap, before fill: dialog=$otherUserId mid=$mid lastPts=${storage.lastPtsValue} ${describe(otherUserId, mid)}")
        onStage { controller.processUpdates(container(deleteUpdate(6001, filler)), false) }
        log("(d) after gap filled: dialog=$otherUserId mid=$mid lastPts=${storage.lastPtsValue} ${describe(otherUserId, mid)}")
        assertNotNull(archived(otherUserId, mid))
    }

    // (c) Channels: the same, with the per-channel pts kept by the client.
    @Test
    fun c_channelDeleteThroughProcessUpdatesInOrder_isArchived() {
        val mid = newMessageId()
        putInCache(incomingChannel(mid, "live channel $mid"))
        storage.saveChannelPts(channelId, 300)
        drainAll()
        onStage { controller.processUpdates(container(deleteChannelUpdate(301, mid)), false) }
        log("(c) channel in order: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        assertNotNull(archived(channelDialogId, mid))
    }

    @Test
    fun c_channelDeleteThroughProcessUpdatesAfterPtsGap_isArchived() {
        val mid = newMessageId()
        val filler = newMessageId()
        putInCache(incomingChannel(mid, "gap channel $mid"))
        storage.saveChannelPts(channelId, 400)
        drainAll()
        onStage { controller.processUpdates(container(deleteChannelUpdate(402, mid)), false) }
        log("(c) channel after gap, before fill: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        onStage { controller.processUpdates(container(deleteChannelUpdate(401, filler)), false) }
        log("(c) channel after gap filled: dialog=$channelDialogId mid=$mid ${describe(channelDialogId, mid)}")
        assertNotNull(archived(channelDialogId, mid))
    }
}
