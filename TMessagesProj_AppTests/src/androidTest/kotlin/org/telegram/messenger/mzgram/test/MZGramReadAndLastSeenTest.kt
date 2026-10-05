package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.mzgram.MZGramLastSeen
import org.telegram.messenger.mzgram.MZGramReadDates
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Message info shows when another person read the owner's message; the
// status line shows an approximate last seen for people who hide it. Both
// fed by the same updates the network layer delivers.
class MZGramReadAndLastSeenTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MessagesController.getInstance(account)

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        instrumentation.runOnMainSync { controller }
    }

    private fun newUserId() = 7_700_000_000L + (Math.random() * 1_000_000).toLong()
    private fun now() = ConnectionsManager.getInstance(account).currentTime

    private fun drain(queue: DispatchQueue) {
        val latch = CountDownLatch(1)
        queue.postRunnable { latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
    }

    private fun deliver(update: TLRPC.Update) {
        Utilities.stageQueue.postRunnable {
            controller.processUpdateArray(arrayListOf(update), ArrayList(), ArrayList(), false, 0)
        }
        repeat(3) {
            drain(Utilities.stageQueue)
            drain(MessagesStorage.getInstance(account).storageQueue)
            drain(Utilities.globalQueue)
        }
        instrumentation.runOnMainSync { }
    }

    private fun readUpTo(userId: Long, maxId: Int) {
        deliver(TL_update.TL_updateReadHistoryOutbox().also {
            it.peer = TLRPC.TL_peerUser().also { p -> p.user_id = userId }
            it.max_id = maxId
            it.pts = 0
            it.pts_count = 0
        })
    }

    @Test
    fun outboxRead_keepsWhenOwnMessagesWereRead() {
        val userId = newUserId()
        val before = now()
        readUpTo(userId, 500)
        val read = MZGramReadDates.keptReadDate(account, userId, 480)
        assertTrue("read time kept: ${read.toList()}", read[0] in before - 5..now() + 5)
        assertEquals("the time this device learned of it", 0, read[1])
        assertEquals("a later message is not read", 0, MZGramReadDates.keptReadDate(account, userId, 501)[0])

        // A later read event does not move the time of messages read earlier.
        Thread.sleep(1500)
        readUpTo(userId, 600)
        assertEquals(read[0], MZGramReadDates.keptReadDate(account, userId, 480)[0])
        assertTrue(MZGramReadDates.keptReadDate(account, userId, 550)[0] >= read[0])
    }

    // The message info of a read own message lists when it was read.
    @Test
    fun messageInfo_listsTheReadTime() {
        val userId = newUserId()
        readUpTo(userId, 900)
        val message = TLRPC.TL_message().also {
            it.id = 890
            it.out = true
            it.unread = false
            it.date = now() - 60
            it.message = "my message"
            it.from_id = TLRPC.TL_peerUser().also { p -> p.user_id = selfId }
            it.peer_id = TLRPC.TL_peerUser().also { p -> p.user_id = userId }
            it.dialog_id = userId
        }
        var messageObject: MessageObject? = null
        instrumentation.runOnMainSync { messageObject = MessageObject(account, message, false, false) }
        val latch = CountDownLatch(1)
        var readAt = 0
        var fromServer = true
        MZGramReadDates.load(account, messageObject!!) { at, server ->
            readAt = at
            fromServer = server
            latch.countDown()
        }
        assertTrue("answered", latch.await(30, TimeUnit.SECONDS))
        assertTrue("read time known: $readAt", readAt > 0)
        assertFalse("not from the server for an unknown user", fromServer)
    }

    private fun hiddenUser(status: TLRPC.UserStatus): TLRPC.User {
        val user = TLRPC.TL_user().also {
            it.id = newUserId()
            it.access_hash = 1
            it.first_name = "hidden"
            it.status = status
        }
        instrumentation.runOnMainSync { controller.putUser(user, false) }
        return user
    }

    private fun status(user: TLRPC.User): String {
        var text = ""
        instrumentation.runOnMainSync { text = LocaleController.formatUserStatus(account, user) }
        return text
    }

    @Test
    fun hiddenLastSeen_showsAnApproximateTime() {
        val user = hiddenUser(TLRPC.TL_userStatusRecently())
        val vague = status(user)
        assertEquals(LocaleController.getString(R.string.Lately), vague)

        val at = now()
        deliver(TL_update.TL_updateUserTyping().also {
            it.user_id = user.id
            it.action = TLRPC.TL_sendMessageTypingAction()
        })
        val shown = status(user)
        MZGramScreens.log("approximate last seen: '$vague' -> '$shown'")
        assertTrue("noted: ${MZGramLastSeen.lastSeen(user.id)}", MZGramLastSeen.lastSeen(user.id) >= at - 5)
        assertEquals(LocaleController.formatString(R.string.MZGramLastSeenApprox, LocaleController.formatDateOnline(MZGramLastSeen.lastSeen(user.id).toLong(), null)), shown)
    }

    @Test
    fun newMessage_isASignOfBeingOnline() {
        val user = hiddenUser(TLRPC.TL_userStatusLastWeek())
        val date = now() - 3600
        val message = TLRPC.TL_message().also {
            it.id = 1_000 + (Math.random() * 1_000_000).toInt()
            it.date = date
            it.message = "hi"
            it.from_id = TLRPC.TL_peerUser().also { p -> p.user_id = user.id }
            it.peer_id = TLRPC.TL_peerUser().also { p -> p.user_id = user.id }
            it.dialog_id = user.id
            it.flags = it.flags or 256
        }
        MessagesStorage.getInstance(account).putMessages(arrayListOf<TLRPC.Message>(message), false, true, false, 0, 0, 0)
        repeat(3) { drain(MessagesStorage.getInstance(account).storageQueue) }
        assertEquals(date, MZGramLastSeen.lastSeen(user.id))
        assertTrue("approximate within a week: ${status(user)}", status(user).endsWith(")"))
    }

    // A time older than what the server says is not shown.
    @Test
    fun tooOldEstimate_keepsTheUsualText() {
        val user = hiddenUser(TLRPC.TL_userStatusRecently())
        MZGramLastSeen.record(user.id, now() - 10 * 24 * 3600)
        assertEquals(LocaleController.getString(R.string.Lately), status(user))
    }

    @Test
    fun keptAcrossRestart() {
        val userId = newUserId()
        val at = now() - 120
        MZGramLastSeen.record(userId, at)
        drain(Utilities.globalQueue)
        val stored = org.telegram.messenger.mzgram.MZGramHistoryDatabase.getInstance().getAllLastSeen()[userId]
        assertNotNull("in the archive database", stored)
        assertEquals(at, stored)
    }
}
