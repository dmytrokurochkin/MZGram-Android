package org.telegram.messenger.mzgram.test

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

// A chat whose only message (or whole history) the other side deleted: the
// message is kept in the archive, and the chat must stay in the chat list,
// showing it, also when the server then says the chat has no messages
// (Telegram removes such a chat). Without kept messages the chat goes as before.
class MZGramDeletedChatStaysTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)
    private var savedSaveDeleted = false

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        instrumentation.runOnMainSync { controller }
        savedSaveDeleted = MZGramConfig.saveDeletedMessages
        MZGramConfig.saveDeletedMessages = true
    }

    @After
    fun tearDown() {
        MZGramConfig.saveDeletedMessages = savedSaveDeleted
    }

    private fun newUserId() = 7_200_000_000L + (Math.random() * 1_000_000).toLong()
    private fun newMessageId() = 100_000 + (Math.random() * 1_000_000).toInt()
    private fun now() = (System.currentTimeMillis() / 1000).toInt()

    private fun drain(queue: DispatchQueue) {
        val latch = CountDownLatch(1)
        queue.postRunnable { latch.countDown() }
        assertTrue("queue drained", latch.await(30, TimeUnit.SECONDS))
    }

    private fun drainAll() {
        repeat(4) {
            drain(Utilities.stageQueue)
            drain(storage.storageQueue)
            instrumentation.runOnMainSync { }
        }
    }

    private fun incoming(userId: Long, mid: Int, text: String, date: Int) = TLRPC.TL_message().also {
        it.id = mid
        it.date = date
        it.message = text
        it.from_id = TLRPC.TL_peerUser().also { p -> p.user_id = userId }
        it.peer_id = TLRPC.TL_peerUser().also { p -> p.user_id = userId }
        it.dialog_id = userId
        it.flags = it.flags or 256
    }

    // A private chat with these messages, in the cache and in the chat list.
    private fun chat(userId: Long, vararg messages: TLRPC.Message) {
        val user = TLRPC.TL_user().also {
            it.id = userId
            it.first_name = "Deleted chat $userId"
            it.access_hash = 1
        }
        instrumentation.runOnMainSync { controller.putUser(user, false) }
        storage.putUsersAndChats(arrayListOf<TLRPC.User>(user), null, true, true)
        storage.putMessages(arrayListOf(*messages), false, true, false, 0, 0, 0)
        drainAll()
        val newest = messages.maxByOrNull { it.id }!!
        val dialogs = TLRPC.TL_messages_dialogs()
        dialogs.dialogs.add(TLRPC.TL_dialog().also {
            it.peer = TLRPC.TL_peerUser().also { p -> p.user_id = userId }
            it.id = userId
            it.top_message = newest.id
            it.last_message_date = newest.date
        })
        dialogs.messages.add(newest)
        dialogs.users.add(user)
        controller.processDialogsUpdate(dialogs, null, false)
        drainAll()
        assertNotNull("the chat is in the list before", controller.dialogs_dict.get(userId))
    }

    private fun deleteByTheOtherSide(vararg mids: Int) {
        val update = TL_update.TL_updateDeleteMessages().also {
            mids.forEach { mid -> it.messages.add(mid) }
            it.pts = 0
            it.pts_count = mids.size
        }
        Utilities.stageQueue.postRunnable {
            controller.processUpdateArray(arrayListOf<TLRPC.Update>(update), ArrayList(), ArrayList(), false, 0)
        }
        drainAll()
    }

    // What MessagesController does when the server answers that the chat
    // has no messages (checkLastDialogMessage).
    private fun serverHasNoMessages(userId: Long) {
        val method = MessagesController::class.java.getDeclaredMethod("onLastDialogMessageMissing", Long::class.javaPrimitiveType)
        method.isAccessible = true
        instrumentation.runOnMainSync { method.invoke(controller, userId) }
        drainAll()
    }

    private fun shownMessage(userId: Long) = controller.dialogMessage.get(userId)?.firstOrNull()?.messageOwner

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until) {
            drainAll()
            var met = false
            instrumentation.runOnMainSync { met = condition() }
            if (met) return
            Thread.sleep(200)
        }
        var met = false
        instrumentation.runOnMainSync { met = condition() }
        assertTrue(what, met)
    }

    private fun inTheList(userId: Long): Boolean {
        var listed = false
        instrumentation.runOnMainSync {
            listed = controller.dialogs_dict.get(userId) != null && controller.getDialogs(0).any { it.id == userId }
        }
        return listed
    }

    @Test
    fun onlyMessageDeleted_chatStaysAndShowsIt() {
        val userId = newUserId()
        val mid = newMessageId()
        chat(userId, incoming(userId, mid, "the only message $mid", now() - 60))

        deleteByTheOtherSide(mid)
        assertNotNull("archived", MZGramHistoryDatabase.getInstance().getDeleted(selfId, userId, mid))
        waitFor("the chat shows the kept message: ${shownMessage(userId)?.id}") {
            shownMessage(userId)?.let { it.id == mid && it.mzgramDeleted } == true
        }
        assertTrue("the chat is in the list", inTheList(userId))
        MZGramScreens.log("only message deleted: chat $userId shows ${shownMessage(userId)?.message}")

        serverHasNoMessages(userId)
        assertTrue("the chat stays after the server says it is empty", inTheList(userId))
        assertEquals("still showing the kept message", mid, shownMessage(userId)?.id)
    }

    @Test
    fun wholeHistoryDeleted_chatStaysWithTheNewestKept() {
        val userId = newUserId()
        val first = newMessageId()
        val second = first + 1
        chat(userId, incoming(userId, first, "first $first", now() - 120), incoming(userId, second, "second $second", now() - 60))

        deleteByTheOtherSide(first, second)
        waitFor("the newest kept message is shown: ${shownMessage(userId)?.id}") {
            shownMessage(userId)?.let { it.id == second && it.mzgramDeleted } == true
        }
        serverHasNoMessages(userId)
        assertTrue("the chat stays after the history is cleared", inTheList(userId))
    }

    @Test
    fun nothingKept_chatGoesAsBefore() {
        MZGramConfig.saveDeletedMessages = false
        val userId = newUserId()
        val mid = newMessageId()
        chat(userId, incoming(userId, mid, "not kept $mid", now() - 60))

        deleteByTheOtherSide(mid)
        assertNull("not archived", MZGramHistoryDatabase.getInstance().getDeleted(selfId, userId, mid))
        serverHasNoMessages(userId)
        waitFor("the chat is removed as in Telegram") { controller.dialogs_dict.get(userId) == null }
    }
}
