package org.telegram.messenger.mzgram.test

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Live end-to-end check against Telegram's public TEST servers (test DC,
// throwaway 99966XXXXX numbers -- never the production network). This
// client signs in as account A; a second account B, driven from the CI
// host (.github/scripts/mzgram_e2e_peer.py), sends A a message and then
// deletes it for everyone. Nothing is simulated on A's side: the deletion
// arrives over the network as a real update, exactly as it does when
// another user deletes a message on a real device.
//
// Scenarios, each in a dialog A tracks:
//   private  -- B's message in the A<->B private chat
//   basic    -- B's message in a basic group with A
//   channel  -- B's message in a supergroup with A
//   nocache  -- like private, but A's local copy is dropped from
//               messages_v2 before B deletes it (as after a cache clear)
//
// Skipped unless the CI wrapper passes the instrumentation arguments
// (mzPhoneA, mzPhoneB, mzCode, mzBasicChatId, mzChannelId).
class MZGramDeletedArchiveLiveTest {

    private val account = 1
    private val tag = "MZGramE2E"

    private fun log(msg: String) = Log.i(tag, msg)

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)

    // Message ids reported by NotificationCenter.messagesDeleted, i.e. the
    // moment MessagesController finished processing a delete update.
    private val deletedIds = Collections.synchronizedSet(HashSet<Int>())
    private val deleteObserver = NotificationCenter.NotificationCenterDelegate { id, _, args ->
        if (id == NotificationCenter.messagesDeleted) {
            @Suppress("UNCHECKED_CAST")
            deletedIds.addAll(args[0] as ArrayList<Int>)
        }
    }

    private fun request(req: TLObject, flags: Int): Pair<TLObject?, TLRPC.TL_error?> {
        val latch = CountDownLatch(1)
        var response: TLObject? = null
        var error: TLRPC.TL_error? = null
        ConnectionsManager.getInstance(account).sendRequest(req, { r, e ->
            response = r
            error = e
            latch.countDown()
        }, flags)
        if (!latch.await(90, TimeUnit.SECONDS)) {
            fail("no answer to ${req.javaClass.simpleName}")
        }
        return Pair(response, error)
    }

    private fun waitFor(timeoutSec: Int, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            if (check()) {
                return true
            }
            Thread.sleep(500)
        }
        return false
    }

    private fun drainStorage() {
        val latch = CountDownLatch(1)
        storage.storageQueue.postRunnable { latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
    }

    private fun signIn(phone: String, code: String): TLRPC.User {
        val loginFlags = ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin

        val sendCode = TLRPC.TL_auth_sendCode()
        sendCode.phone_number = phone
        sendCode.api_id = BuildVars.APP_ID
        sendCode.api_hash = BuildVars.APP_HASH
        sendCode.settings = TLRPC.TL_codeSettings()
        val (sent, sendErr) = request(sendCode, loginFlags)
        assertNull("auth.sendCode error: ${sendErr?.text}", sendErr)
        val hash = (sent as TLRPC.auth_SentCode).phone_code_hash

        val signIn = TLRPC.TL_auth_signIn()
        signIn.phone_number = phone
        signIn.phone_code_hash = hash
        signIn.phone_code = code
        signIn.flags = signIn.flags or 1
        val (auth, signErr) = request(signIn, loginFlags)
        assertNull("auth.signIn error: ${signErr?.text}", signErr)
        val user = (auth as TLRPC.TL_auth_authorization).user

        // The parts of LoginActivity.onAuthSuccess that matter for receiving
        // updates: bind the session to the user and persist the account.
        ConnectionsManager.getInstance(account).setUserId(user.id)
        UserConfig.getInstance(account).clearConfig()
        UserConfig.getInstance(account).setCurrentUser(user)
        UserConfig.getInstance(account).saveConfig(true)
        storage.cleanup(true)
        storage.putUsersAndChats(arrayListOf(user), null, true, true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.putUser(user, false)
        }
        return user
    }

    private fun latestMessage(dialogId: Long): Pair<Int, String>? {
        val latch = CountDownLatch(1)
        var result: Pair<Int, String>? = null
        storage.storageQueue.postRunnable {
            try {
                val cursor = storage.database.queryFinalized(
                    "SELECT mid, data FROM messages_v2 WHERE uid = $dialogId ORDER BY mid DESC LIMIT 1")
                if (cursor.next()) {
                    val data = cursor.byteBufferValue(1)
                    if (data != null) {
                        val m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false)
                        data.reuse()
                        result = Pair(cursor.intValue(0), m.message ?: "")
                    }
                }
                cursor.dispose()
            } finally {
                latch.countDown()
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        return result
    }

    private fun dropFromCache(dialogId: Long, mid: Int) {
        val latch = CountDownLatch(1)
        storage.storageQueue.postRunnable {
            try {
                storage.database.executeFast("DELETE FROM messages_v2 WHERE uid = $dialogId AND mid = $mid").stepThis().dispose()
            } finally {
                latch.countDown()
            }
        }
        latch.await(30, TimeUnit.SECONDS)
    }

    // Returns null on success, or a description of what went wrong.
    private fun runScenario(name: String, selfId: Long, dialogId: Long, senderId: Long, dropCache: Boolean): String? {
        val before = latestMessage(dialogId)?.first ?: 0
        // The CI peer script waits for this line before sending.
        log("READY scenario=$name dialog=$dialogId")

        var received: Pair<Int, String>? = null
        if (!waitFor(120) {
                received = latestMessage(dialogId)?.takeIf { it.first > before && it.second.startsWith("MZGram E2E") }
                received != null
            }) {
            log("RESULT scenario=$name FAIL message from B never reached messages_v2")
            return "$name: B's message never arrived"
        }
        val (mid, text) = received!!
        if (dropCache) {
            dropFromCache(dialogId, mid)
            log("DROPPED_FROM_CACHE scenario=$name mid=$mid")
        }
        // The CI peer script waits for this line before deleting.
        log("RECEIVED scenario=$name mid=$mid text='$text'")

        if (!waitFor(120) { deletedIds.contains(mid) }) {
            log("RESULT scenario=$name FAIL delete update for mid=$mid never processed")
            return "$name: delete update never processed"
        }
        drainStorage()
        drainStorage()
        val stillCached = latestMessage(dialogId)?.first == mid
        val row = MZGramHistoryDatabase.getInstance().getDeleted(selfId, dialogId, mid)
        log("ARCHIVE scenario=$name dialog=$dialogId mid=$mid stillInMessagesV2=$stillCached row=" +
            (row?.let { "rowId=${it.rowId} fromId=${it.fromId} text='${it.text}'" } ?: "NONE"))
        if (row == null) {
            log("RESULT scenario=$name FAIL no archive row")
            return "$name: no archive row for mid=$mid"
        }
        if (row.text != text || row.fromId != senderId) {
            log("RESULT scenario=$name FAIL wrong row")
            return "$name: archive row has text='${row.text}' fromId=${row.fromId}"
        }
        log("RESULT scenario=$name PASS")
        return null
    }

    @Test
    fun otherUserDeletesOverTheNetwork_isArchived() {
        val args = InstrumentationRegistry.getArguments()
        val phoneA = args.getString("mzPhoneA")
        val phoneB = args.getString("mzPhoneB")
        val code = args.getString("mzCode")
        val basicChatId = args.getString("mzBasicChatId")?.toLong()
        val channelId = args.getString("mzChannelId")?.toLong()
        assumeTrue("live test needs the CI instrumentation arguments",
            phoneA != null && phoneB != null && code != null && basicChatId != null && channelId != null)

        BuildVars.LOGS_ENABLED = true
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller }
        val connections = ConnectionsManager.getInstance(account)
        if (!connections.isTestBackend) {
            connections.switchBackend(false)
        }
        assertEquals("connected to the TEST backend", true, connections.isTestBackend)
        connections.setAppPaused(false, false)

        val self = signIn(phoneA!!, code!!)
        log("signed in as A id=${self.id}")

        // Starts the server pushing live updates to this session, and loads
        // the dialog list so the two groups B created are known locally.
        controller.loadCurrentState()
        assertTrue("updates state", waitFor(60) { storage.lastPtsValue != 0 })
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.loadDialogs(0, 0, 100, true)
        }
        assertTrue("groups loaded", waitFor(60) {
            controller.getChat(basicChatId!!) != null && controller.getChat(channelId!!) != null
        })

        val resolve = TLRPC.TL_contacts_resolvePhone()
        resolve.phone = phoneB
        val (resolved, resolveErr) = request(resolve, 0)
        assertNull("contacts.resolvePhone error: ${resolveErr?.text}", resolveErr)
        val peerB = (resolved as TLRPC.TL_contacts_resolvedPeer).users.first()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.putUser(peerB, false)
            NotificationCenter.getInstance(account).addObserver(deleteObserver, NotificationCenter.messagesDeleted)
        }

        val privateDialog = peerB.id
        val basicDialog = -basicChatId!!
        val channelDialog = -channelId!!
        val savedSaveMessageHistory = MZGramConfig.saveMessageHistory
        MZGramConfig.saveMessageHistory = true
        val failures = ArrayList<String>()
        try {
            runScenario("private", self.id, privateDialog, peerB.id, false)?.let { failures.add(it) }
            runScenario("basic", self.id, basicDialog, peerB.id, false)?.let { failures.add(it) }
            runScenario("channel", self.id, channelDialog, peerB.id, false)?.let { failures.add(it) }
            // Reported, not asserted: a message whose content never stayed on
            // this device cannot be rebuilt once the server has deleted it.
            val nocache = runScenario("nocache", self.id, privateDialog, peerB.id, true)
            log("NOCACHE_OUTCOME ${nocache ?: "archived"}")
        } finally {
            MZGramConfig.saveMessageHistory = savedSaveMessageHistory
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                NotificationCenter.getInstance(account).removeObserver(deleteObserver, NotificationCenter.messagesDeleted)
            }
            log("DONE")
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("; "))
        }
    }
}
