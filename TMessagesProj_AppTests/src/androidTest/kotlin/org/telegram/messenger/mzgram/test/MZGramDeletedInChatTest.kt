package org.telegram.messenger.mzgram.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramHistoryController
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Deleted messages and removed media of other people stay in the user's
// chat. Two ways a chat gets its messages, both covered:
//   - loading history (opening the chat, scrolling): the chat asks
//     MessagesController.loadMessages and gets the list in
//     NotificationCenter.messagesDidLoad -- archived deleted messages that
//     belong in the loaded range must be in that list, marked as deleted,
//     and removed media must be back;
//   - a deletion arriving while the chat is open: ChatActivity asks
//     MZGramHistoryController.keepsDeletedInChat whether to keep the
//     message instead of removing it.
// The look (deleted: 50% opacity and a "deleted" mark; edited: a pencil
// next to the time) is checked on a real ChatMessageCell, and each case is
// saved as a PNG under the app's external files dir (mzgram-screens/).
class MZGramDeletedInChatTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_400_000_000L + (Math.random() * 1_000_000).toLong()
    private val untrackedUserId = 7_500_000_000L + (Math.random() * 1_000_000).toLong()

    private var savedSaveMessageHistory = false

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        instrumentation.runOnMainSync {
            controller.putUser(user(otherUserId), false)
            controller.putUser(user(untrackedUserId), false)
        }
        savedSaveMessageHistory = MZGramConfig.saveMessageHistory
        MZGramConfig.saveMessageHistory = true
        MZGramConfig.setDialogTracked(otherUserId, true)
        MZGramConfig.setDialogTracked(untrackedUserId, false)
    }

    @After
    fun tearDown() {
        MZGramConfig.setDialogTracked(otherUserId, false)
        MZGramConfig.saveMessageHistory = savedSaveMessageHistory
    }

    // ---- fixtures ----

    private fun now() = ConnectionsManager.getInstance(account).currentTime

    private var nextId = 100_000 + (Math.random() * 1_000_000).toInt()
    private fun newMessageId(): Int {
        nextId += 10
        return nextId
    }

    private fun userPeer(id: Long) = TLRPC.TL_peerUser().also { it.user_id = id }

    private fun user(id: Long) = TLRPC.TL_user().also {
        it.id = id
        it.access_hash = 1
        it.first_name = "MZGram test other"
    }

    private fun incoming(dialogId: Long, mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.from_id = userPeer(dialogId)
        it.peer_id = userPeer(dialogId)
        it.dialog_id = dialogId
        it.flags = it.flags or 256
    }

    private fun outgoing(dialogId: Long, mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.out = true
        it.from_id = userPeer(selfId)
        it.peer_id = userPeer(dialogId)
        it.dialog_id = dialogId
        it.flags = it.flags or 256 or 2
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

    private fun putInCache(vararg messages: TLRPC.Message) {
        storage.putMessages(arrayListOf(*messages), false, true, false, 0, 0, 0)
        drainAll()
    }

    private fun deliverDelete(vararg mids: Int) {
        val update = TL_update.TL_updateDeleteMessages()
        mids.forEach { update.messages.add(it) }
        Utilities.stageQueue.postRunnable {
            controller.processUpdateArray(arrayListOf<TLRPC.Update>(update), ArrayList(), ArrayList(), false, 0)
        }
        drainAll()
    }

    // Loads the newest messages of a dialog from the local cache the way
    // ChatActivity does when a chat is opened, and returns the list it
    // would get in messagesDidLoad.
    // The test messages arrive unread; a chat with unread messages loads
    // around the first unread one and then asks the server, which this
    // signed-out test account cannot. Mark the chat read, as after the user
    // has seen it, so the newest messages come from the cache.
    private fun markChatRead(dialogId: Long) {
        val latch = CountDownLatch(1)
        storage.storageQueue.postRunnable {
            try {
                storage.database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = $dialogId").stepThis().dispose()
                storage.database.executeFast("UPDATE dialogs SET unread_count = 0, inbox_max = (SELECT MAX(mid) FROM messages_v2 WHERE uid = $dialogId) WHERE did = $dialogId").stepThis().dispose()
            } finally {
                latch.countDown()
            }
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
    }

    private fun loadHistory(dialogId: Long): List<MessageObject> {
        markChatRead(dialogId)
        val guid = ConnectionsManager.generateClassGuid()
        val latch = CountDownLatch(1)
        var loaded: List<MessageObject> = emptyList()
        val observer = object : NotificationCenter.NotificationCenterDelegate {
            override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
                if (id == NotificationCenter.messagesDidLoad && args[10] == guid && latch.count > 0) {
                    @Suppress("UNCHECKED_CAST")
                    loaded = ArrayList(args[2] as ArrayList<MessageObject>)
                    latch.countDown()
                }
            }
        }
        instrumentation.runOnMainSync {
            NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.messagesDidLoad)
            controller.loadMessages(dialogId, 0, false, 50, 0, 0, true, 0, guid, 2, 0, 0, 0, 0, 0, false)
        }
        try {
            assertTrue("messagesDidLoad for dialog $dialogId", latch.await(30, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.messagesDidLoad)
            }
        }
        log("loaded dialog=$dialogId: " + loaded.joinToString { "${it.id}${if (runCatching { isMarkedDeleted(it.messageOwner) }.getOrDefault(false)) "(deleted)" else ""}" })
        return loaded
    }

    // TLRPC.Message.mzgramDeleted: the local "kept after deletion" mark.
    private fun isMarkedDeleted(message: TLRPC.Message): Boolean =
        message.javaClass.getField("mzgramDeleted").getBoolean(message)

    private fun markDeleted(message: TLRPC.Message) =
        message.javaClass.getField("mzgramDeleted").setBoolean(message, true)

    private fun keepsDeletedInChat(dialogId: Long, message: TLRPC.Message): Boolean =
        MZGramHistoryController::class.java
            .getMethod("keepsDeletedInChat", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, TLRPC.Message::class.java)
            .invoke(MZGramHistoryController.getInstance(), account, dialogId, message) as Boolean

    // ---- loading history ----

    @Test
    fun history_otherUsersDeletedMessage_isShownAgainOnLoad() {
        val m1 = newMessageId()
        val m2 = newMessageId()
        val m3 = newMessageId()
        putInCache(incoming(otherUserId, m1, "first $m1"), incoming(otherUserId, m2, "deleted later $m2"), incoming(otherUserId, m3, "third $m3"))
        deliverDelete(m2)

        val loaded = loadHistory(otherUserId)
        assertEquals("newest first, deleted message in its place", listOf(m3, m2, m1), loaded.map { it.id }.filter { it in listOf(m1, m2, m3) })
        val kept = loaded.first { it.id == m2 }
        assertEquals("deleted later $m2", kept.messageOwner.message)
        assertTrue("marked as deleted", isMarkedDeleted(kept.messageOwner))
        assertFalse("others are not marked", isMarkedDeleted(loaded.first { it.id == m1 }.messageOwner))
    }

    // The most common case: someone sends a message and deletes it right
    // away, so it was the newest one in the chat.
    @Test
    fun history_otherUsersDeletedNewestMessage_isShownAgainOnLoad() {
        val m1 = newMessageId()
        val m2 = newMessageId()
        putInCache(incoming(otherUserId, m1, "stays $m1"), incoming(otherUserId, m2, "newest, deleted $m2"))
        deliverDelete(m2)

        val loaded = loadHistory(otherUserId)
        assertEquals(listOf(m2, m1), loaded.map { it.id }.filter { it == m1 || it == m2 })
        assertTrue(isMarkedDeleted(loaded.first { it.id == m2 }.messageOwner))
    }

    @Test
    fun history_ownDeletedMessage_isNotShownAgain() {
        val m1 = newMessageId()
        val m2 = newMessageId()
        val m3 = newMessageId()
        putInCache(incoming(otherUserId, m1, "a $m1"), outgoing(otherUserId, m2, "own $m2"), incoming(otherUserId, m3, "c $m3"))
        deliverDelete(m2)

        val loaded = loadHistory(otherUserId)
        assertEquals(listOf(m3, m1), loaded.map { it.id }.filter { it in listOf(m1, m2, m3) })
    }

    @Test
    fun history_untrackedChat_deletedMessageIsGone() {
        val m1 = newMessageId()
        val m2 = newMessageId()
        val m3 = newMessageId()
        putInCache(incoming(untrackedUserId, m1, "a $m1"), incoming(untrackedUserId, m2, "b $m2"), incoming(untrackedUserId, m3, "c $m3"))
        deliverDelete(m2)

        val loaded = loadHistory(untrackedUserId)
        assertEquals(listOf(m3, m1), loaded.map { it.id }.filter { it in listOf(m1, m2, m3) })
    }

    // Self-destructing / view-once media removed from another user's
    // message: the chat shows the media again, from the archived file, as
    // ordinary media (no timer).
    @Test
    fun history_removedMediaIsBackOnLoad() {
        val m1 = newMessageId()
        val mid = newMessageId()
        val message = incoming(otherUserId, mid, "")
        val bytes = ByteArray(2048) { (it * 7 + mid).toByte() }
        val documentId = 800_000_000_000L + mid
        message.media = TLRPC.TL_messageMediaDocument().also {
            it.document = TLRPC.TL_document().also { d ->
                d.id = documentId
                d.access_hash = 1
                d.dc_id = 2
                d.date = message.date
                d.mime_type = "application/octet-stream"
                d.size = bytes.size.toLong()
                d.file_reference = ByteArray(0)
                d.attributes.add(TLRPC.TL_documentAttributeFilename().also { a -> a.file_name = "mzgram_restore_$mid.bin" })
            }
            it.ttl_seconds = 10
            it.flags = it.flags or 1 or 4
        }
        message.flags = message.flags or 512
        putInCache(incoming(otherUserId, m1, "before $m1"), message)
        val file = FileLoader.getInstance(account).getPathToMessage(message)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)

        storage.emptyMessagesMedia(otherUserId, arrayListOf(mid))
        drainAll()
        // emptyMessagesMedia deletes the file on its own thread.
        Thread.sleep(1000)

        val loaded = loadHistory(otherUserId).first { it.id == mid }
        val document = loaded.messageOwner.media?.document
        log("restored media: mid=$mid document=${document?.javaClass?.simpleName} id=${document?.id} ttl=${loaded.messageOwner.media?.ttl_seconds}")
        assertTrue("media is back", document is TLRPC.TL_document)
        assertEquals(documentId, document!!.id)
        assertEquals("shown as ordinary media, no timer", 0, loaded.messageOwner.media.ttl_seconds)
        val shownFile = FileLoader.getInstance(account).getPathToMessage(loaded.messageOwner)
        assertTrue("file present where the chat looks for it: $shownFile", shownFile.exists())
        assertTrue(shownFile.readBytes().contentEquals(bytes))
    }

    // ---- deletion while the chat is open ----

    @Test
    fun live_otherUsersDeletion_isKeptInOpenChat() {
        val mid = newMessageId()
        val message = incoming(otherUserId, mid, "live $mid")
        putInCache(message)
        deliverDelete(mid)
        assertTrue(keepsDeletedInChat(otherUserId, message))
    }

    @Test
    fun live_ownMessage_isRemoved() {
        val mid = newMessageId()
        val message = outgoing(otherUserId, mid, "own live $mid")
        putInCache(message)
        deliverDelete(mid)
        assertFalse(keepsDeletedInChat(otherUserId, message))
    }

    @Test
    fun live_untrackedChat_isRemoved() {
        val mid = newMessageId()
        val message = incoming(untrackedUserId, mid, "untracked live $mid")
        putInCache(message)
        deliverDelete(mid)
        assertFalse(keepsDeletedInChat(untrackedUserId, message))
    }

    // The user deleting a message themself (also a kept deleted one) removes
    // it from the chat, and it does not come back when the chat reloads.
    @Test
    fun live_userDeletesMessageThemself_isRemovedAndStaysGone() {
        val m1 = newMessageId()
        val m2 = newMessageId()
        val m3 = newMessageId()
        putInCache(incoming(otherUserId, m1, "a $m1"), incoming(otherUserId, m2, "b $m2"), incoming(otherUserId, m3, "c $m3"))
        deliverDelete(m2)
        assertTrue("kept while only the other user deleted it", loadHistory(otherUserId).any { it.id == m2 })

        val kept = incoming(otherUserId, m2, "b $m2")
        instrumentation.runOnMainSync {
            controller.deleteMessages(arrayListOf(m2), null, null, otherUserId, 0, false, 0)
        }
        drainAll()
        assertFalse("user's own Delete removes it from the chat", keepsDeletedInChat(otherUserId, kept))
        assertFalse("and it is not shown again", loadHistory(otherUserId).any { it.id == m2 })
    }

    // ---- look ----

    private fun renderCell(name: String, message: TLRPC.Message): ChatMessageCell {
        val context = instrumentation.targetContext
        var cell: ChatMessageCell? = null
        instrumentation.runOnMainSync {
            Theme.createCommonResources(context)
            Theme.createCommonMessageResources()
            Theme.createChatResources(context, false)
            val obj = MessageObject(account, message, true, false)
            val c = ChatMessageCell(context, account)
            // Not in a window here; let setMessageObject lay out right away.
            ChatMessageCell::class.java.getDeclaredField("attachedToWindow").also { it.isAccessible = true }.setBoolean(c, true)
            c.setMessageObject(obj, null, false, false, false)
            c.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            c.layout(0, 0, c.measuredWidth, c.measuredHeight)
            val bitmap = Bitmap.createBitmap(c.measuredWidth, maxOf(1, c.measuredHeight), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            c.draw(canvas)
            val dir = File(context.getExternalFilesDir(null), "mzgram-screens").also { it.mkdirs() }
            val out = File(dir, "mzgram-$name.png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            log("screenshot $name: ${out.absolutePath}")
            cell = c
        }
        // The test run uninstalls the app, and its files dir with it; keep a
        // copy where the CI script can pull it from afterwards.
        val src = File(File(instrumentation.targetContext.getExternalFilesDir(null), "mzgram-screens"), "mzgram-$name.png")
        shell("mkdir -p /data/local/tmp/mzgram-screens")
        shell("cp ${src.absolutePath} /data/local/tmp/mzgram-screens/")
        return cell!!
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
    }

    private fun timeText(cell: ChatMessageCell): String =
        ChatMessageCell::class.java.getDeclaredField("currentTimeString").also { it.isAccessible = true }.get(cell)?.toString() ?: ""

    private fun dimAlpha(cell: ChatMessageCell): Float =
        ChatMessageCell::class.java.getMethod("getMZGramDimAlpha").invoke(cell) as Float

    @Test
    fun look_deletedMessage_isHalfTransparentAndMarked() {
        val message = incoming(otherUserId, newMessageId(), "This message was deleted by its sender")
        markDeleted(message)
        val cell = renderCell("deleted", message)
        log("deleted cell: time='${timeText(cell)}' alpha=${dimAlpha(cell)}")
        assertEquals(0.5f, dimAlpha(cell), 0.001f)
        assertTrue("time reads 'deleted ...': ${timeText(cell)}", timeText(cell).startsWith("deleted "))
    }

    @Test
    fun look_editedMessage_hasPencilAndIsNotDimmed() {
        val message = incoming(otherUserId, newMessageId(), "This message was edited")
        message.edit_date = now()
        message.flags = message.flags or 32768
        val cell = renderCell("edited", message)
        log("edited cell: time='${timeText(cell)}' alpha=${dimAlpha(cell)}")
        assertEquals(1f, dimAlpha(cell), 0.001f)
        assertTrue("pencil next to the time: ${timeText(cell)}", timeText(cell).contains("✏"))
    }

    @Test
    fun look_ordinaryMessage_isUnchanged() {
        val message = incoming(otherUserId, newMessageId(), "An ordinary message")
        val cell = renderCell("ordinary", message)
        log("ordinary cell: time='${timeText(cell)}' alpha=${dimAlpha(cell)}")
        assertEquals(1f, dimAlpha(cell), 0.001f)
        assertFalse(timeText(cell).contains("✏"))
        assertFalse(timeText(cell).contains("deleted"))
    }
}
