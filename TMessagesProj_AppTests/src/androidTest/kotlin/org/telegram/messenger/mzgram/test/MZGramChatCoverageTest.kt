package org.telegram.messenger.mzgram.test

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DialogObject
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
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_update
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The rest of "other people's deleted messages and media stay in the
// chat", beyond plain private chats:
//   - one-time (view once / self-destruct) media in an open chat stays
//     available right away, never "expired";
//   - supergroups, forum topics and reply threads get deleted messages and
//     removed media back when history loads, like private chats;
//   - secret chats too;
//   - clearing a chat's history does not wipe its archive;
//   - the media file of a deleted message stays where the chat shows it.
// Loads the server answers are fed straight into
// MessagesController.processLoadedMessages, exactly as the network
// callback does (this test account is not signed in).
class MZGramChatCoverageTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_600_000_000L + (Math.random() * 1_000_000).toLong()
    private val newChatUserId = 7_650_000_000L + (Math.random() * 1_000_000).toLong()
    private val channelId = 7_700_000_000L + (Math.random() * 1_000_000).toLong()
    private val channelDialogId = -channelId
    private val secretDialogId = DialogObject.makeEncryptedDialogId(100_000L + (Math.random() * 1_000_000).toLong())

    private var savedSaveMessageHistory = false

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val db get() = MZGramHistoryDatabase.getInstance()

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
            controller.putUser(user(newChatUserId), false)
        }
        savedSaveMessageHistory = MZGramConfig.saveMessageHistory
        MZGramConfig.saveMessageHistory = true
    }

    @After
    fun tearDown() {
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

    private fun supergroup(forum: Boolean) = TLRPC.TL_channel().also {
        it.id = channelId
        it.access_hash = 1
        it.title = "MZGram test supergroup"
        it.megagroup = true
        it.forum = forum
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

    private fun inChannel(mid: Int, text: String, replyTo: Int = 0, topic: Boolean = false) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.from_id = userPeer(otherUserId)
        it.peer_id = TLRPC.TL_peerChannel().also { p -> p.channel_id = channelId }
        it.dialog_id = channelDialogId
        it.flags = it.flags or 256
        if (replyTo != 0) {
            it.reply_to = TLRPC.TL_messageReplyHeader().also { r ->
                r.reply_to_msg_id = replyTo
                r.forum_topic = topic
                r.flags = r.flags or 16 or (if (topic) 8 else 0)
            }
            it.flags = it.flags or 8
        }
    }

    private fun inSecret(mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.from_id = userPeer(otherUserId)
        it.peer_id = userPeer(otherUserId)
        it.dialog_id = secretDialogId
        it.random_id = Utilities.random.nextLong()
        it.flags = it.flags or 256
    }

    // A document with a self-destruct timer, and its "downloaded" file.
    private fun withOneTimeDocument(message: TLRPC.Message, bytes: ByteArray): TLRPC.Message {
        message.media = TLRPC.TL_messageMediaDocument().also {
            it.document = document(message.id, bytes)
            it.ttl_seconds = 10
            it.flags = it.flags or 1 or 4
        }
        message.flags = message.flags or 512
        return message
    }

    private fun withDocument(message: TLRPC.Message, bytes: ByteArray): TLRPC.Message {
        message.media = TLRPC.TL_messageMediaDocument().also {
            it.document = document(message.id, bytes)
            it.flags = it.flags or 1
        }
        message.flags = message.flags or 512
        return message
    }

    private fun document(mid: Int, bytes: ByteArray) = TLRPC.TL_document().also { d ->
        d.id = 600_000_000_000L + mid
        d.access_hash = 1
        d.dc_id = 2
        d.date = now()
        d.mime_type = "application/octet-stream"
        d.size = bytes.size.toLong()
        d.file_reference = ByteArray(0)
        d.attributes.add(TLRPC.TL_documentAttributeFilename().also { a -> a.file_name = "mzgram_cov_$mid.bin" })
    }

    private fun writeDownloadedFile(message: TLRPC.Message, bytes: ByteArray): File {
        val file = FileLoader.getInstance(account).getPathToMessage(message)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    // emptyMessagesMedia's result as the server sends it again: media
    // without its document.
    private fun withRemovedMedia(message: TLRPC.Message): TLRPC.Message {
        message.media = TLRPC.TL_messageMediaDocument().also { it.ttl_seconds = 10; it.flags = 4 }
        message.flags = message.flags or 512
        return message
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

    private fun deliver(update: TLRPC.Update) {
        Utilities.stageQueue.postRunnable {
            controller.processUpdateArray(arrayListOf(update), ArrayList(), ArrayList(), false, 0)
        }
        drainAll()
    }

    private fun deletePrivate(vararg mids: Int) = deliver(TL_update.TL_updateDeleteMessages().also { u -> mids.forEach { u.messages.add(it) } })

    private fun deleteInChannel(vararg mids: Int) = deliver(TL_update.TL_updateDeleteChannelMessages().also { u ->
        u.channel_id = channelId
        mids.forEach { u.messages.add(it) }
    })

    private fun markChatRead(dialogId: Long) {
        val latch = CountDownLatch(1)
        storage.storageQueue.postRunnable {
            try {
                storage.database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = $dialogId").stepThis().dispose()
                storage.database.executeFast("UPDATE dialogs SET unread_count = 0 WHERE did = $dialogId").stepThis().dispose()
            } finally {
                latch.countDown()
            }
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
    }

    private fun awaitLoad(guid: Int, start: () -> Unit): List<MessageObject> {
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
        }
        try {
            start()
            assertTrue("messagesDidLoad", latch.await(30, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.messagesDidLoad)
            }
        }
        log("loaded: " + loaded.joinToString { "${it.id}${if (runCatching { isMarkedDeleted(it.messageOwner) }.getOrDefault(false)) "(deleted)" else ""}" })
        return loaded
    }

    // Newest messages of a chat from the local cache, as when it is opened.
    private fun loadFromCache(dialogId: Long): List<MessageObject> {
        markChatRead(dialogId)
        val guid = ConnectionsManager.generateClassGuid()
        return awaitLoad(guid) {
            instrumentation.runOnMainSync {
                controller.loadMessages(dialogId, 0, false, 50, 0, 0, true, 0, guid, 2, 0, 0, 0, 0, 0, false)
            }
        }
    }

    // The newest messages of a chat, a forum topic or a reply thread as the
    // server returns them: handed to processLoadedMessages the way the
    // network callback does.
    private fun loadFromServer(dialogId: Long, serverMessages: List<TLRPC.Message>, threadMessageId: Long = 0, isTopic: Boolean = false): List<MessageObject> {
        val guid = ConnectionsManager.generateClassGuid()
        val res = TLRPC.TL_messages_messages()
        res.messages.addAll(serverMessages)
        return awaitLoad(guid) {
            Utilities.stageQueue.postRunnable {
                controller.processLoadedMessages(res, res.messages.size, dialogId, 0, 50, 0, 0, false, guid,
                    0, 0, 0, 0, 2, false, 0, threadMessageId, 0, false, 0, true, isTopic, null)
            }
        }
    }

    private fun isMarkedDeleted(message: TLRPC.Message): Boolean =
        message.javaClass.getField("mzgramDeleted").getBoolean(message)

    private fun isRestoredMedia(message: TLRPC.Message): Boolean =
        message.javaClass.getField("mzgramRestoredMedia").getBoolean(message)

    private fun controllerCall(name: String, vararg args: Any?): Any? =
        MZGramHistoryController::class.java.methods.first { it.name == name && it.parameterTypes.size == args.size }
            .invoke(MZGramHistoryController.getInstance(), *args)

    private fun ids(loaded: List<MessageObject>, vararg mids: Int) = loaded.map { it.id }.filter { it in mids.toList() }

    // ---- 1. one-time media in an open chat ----

    // View once / self-destruct media of another user, removed while the
    // chat is open: the chat is told about the media change
    // (updateMessageMedia) and must get the media back, as ordinary media,
    // straight away.
    @Test
    fun oneTime_removedWhileChatOpen_comesBackRightAway() {
        val mid = newMessageId()
        val bytes = ByteArray(3000) { (it * 13 + mid).toByte() }
        val message = withOneTimeDocument(incoming(otherUserId, mid, ""), bytes)
        putInCache(message)
        writeDownloadedFile(message, bytes)
        val documentId = message.media.document.id

        // The message as the open chat holds it, already marked expired by the viewer.
        val shown = MessageObject(account, withOneTimeDocument(incoming(otherUserId, mid, ""), bytes), true, false)
        shown.forceExpired = true

        val latch = CountDownLatch(1)
        var posted: TLRPC.Message? = null
        val observer = object : NotificationCenter.NotificationCenterDelegate {
            override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
                val m = args[0] as TLRPC.Message
                if (id == NotificationCenter.updateMessageMedia && m.id == mid) {
                    posted = m
                    latch.countDown()
                }
            }
        }
        instrumentation.runOnMainSync { NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.updateMessageMedia) }
        try {
            storage.emptyMessagesMedia(otherUserId, arrayListOf(mid))
            assertTrue("updateMessageMedia", latch.await(30, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.updateMessageMedia) }
        }
        val media = posted!!.media
        log("one-time open chat: posted document=${media.document?.javaClass?.simpleName} ttl=${media.ttl_seconds}")
        assertTrue("the chat is sent the media back", media.document is TLRPC.TL_document)
        assertEquals(documentId, media.document.id)
        assertEquals(0, media.ttl_seconds)
        assertTrue(isRestoredMedia(posted!!))

        instrumentation.runOnMainSync { controllerCall("applyRestoredMedia", shown, posted) }
        log("one-time open chat: forceExpired=${shown.forceExpired} blurred=${shown.needDrawBluredPreview()} secret=${shown.isSecretMedia()}")
        assertFalse("not expired", shown.forceExpired)
        assertFalse("not a one-time preview any more", shown.needDrawBluredPreview())
        assertFalse(shown.isSecretMedia())
        assertTrue("file where the chat looks for it", FileLoader.getInstance(account).getPathToMessage(shown.messageOwner).exists())
    }

    @Test
    fun oneTime_archiveOff_stillExpires() {
        MZGramConfig.saveMessageHistory = false
        val mid = newMessageId()
        val bytes = ByteArray(1000) { it.toByte() }
        val message = withOneTimeDocument(incoming(newChatUserId, mid, ""), bytes)
        putInCache(message)
        writeDownloadedFile(message, bytes)
        val latch = CountDownLatch(1)
        var posted: TLRPC.Message? = null
        val observer = object : NotificationCenter.NotificationCenterDelegate {
            override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
                val m = args[0] as TLRPC.Message
                if (id == NotificationCenter.updateMessageMedia && m.id == mid) {
                    posted = m
                    latch.countDown()
                }
            }
        }
        instrumentation.runOnMainSync { NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.updateMessageMedia) }
        try {
            storage.emptyMessagesMedia(newChatUserId, arrayListOf(mid))
            assertTrue(latch.await(30, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.updateMessageMedia) }
        }
        assertTrue("expires as usual", posted!!.media.document == null || posted!!.media.document is TLRPC.TL_documentEmpty)
    }

    // The viewer must not mark another user's one-time media "expired", in
    // any chat, without adding the chat anywhere (ChatActivity asks
    // keepsOneTimeMediaInChat). Own media and a switched-off archive expire.
    @Test
    fun oneTime_keptForOtherPeopleInEveryChat() {
        val other = withOneTimeDocument(incoming(otherUserId, newMessageId(), ""), ByteArray(10))
        val own = withOneTimeDocument(incoming(otherUserId, newMessageId(), ""), ByteArray(10)).also {
            it.out = true
            it.from_id = userPeer(selfId)
        }
        val newChat = withOneTimeDocument(incoming(newChatUserId, newMessageId(), ""), ByteArray(10))
        assertEquals(true, controllerCall("keepsOneTimeMediaInChat", account, otherUserId, other))
        assertEquals(false, controllerCall("keepsOneTimeMediaInChat", account, otherUserId, own))
        assertEquals("a chat never added anywhere", true, controllerCall("keepsOneTimeMediaInChat", account, newChatUserId, newChat))
        MZGramConfig.saveMessageHistory = false
        assertEquals("archive off", false, controllerCall("keepsOneTimeMediaInChat", account, otherUserId, other))
    }

    // A deleted one-time message kept in the open chat is shown as ordinary
    // media, not as a one-time preview the user can no longer open.
    @Test
    fun oneTime_keptDeletedMessage_isOrdinaryMedia() {
        val message = withOneTimeDocument(incoming(otherUserId, newMessageId(), ""), ByteArray(10))
        val obj = MessageObject(account, message, true, false)
        assertTrue(obj.needDrawBluredPreview())
        instrumentation.runOnMainSync { controllerCall("markKeptDeleted", obj) }
        assertTrue(isMarkedDeleted(obj.messageOwner))
        assertFalse(obj.needDrawBluredPreview())
        assertTrue(obj.forceUpdate)
    }

    // ---- deleted message's file ----

    // Another user's deleted message stays in the open chat with its media:
    // the downloaded file must not be deleted with the row.
    @Test
    fun live_deletedMessageMedia_fileStays() {
        val mid = newMessageId()
        val bytes = ByteArray(2000) { (it * 5).toByte() }
        val message = withDocument(incoming(otherUserId, mid, "with a file"), bytes)
        putInCache(message)
        val file = writeDownloadedFile(message, bytes)
        deletePrivate(mid)
        Thread.sleep(1500) // files are deleted on their own thread
        log("deleted message file: $file exists=${file.exists()}")
        assertNotNull(db.getDeleted(selfId, otherUserId, mid))
        assertTrue("file still there for the chat", file.exists())
    }

    // ---- 2. supergroups, forum topics, reply threads ----

    @Test
    fun supergroup_deletedMessageAndRemovedMediaAreShownAgain() {
        instrumentation.runOnMainSync { controller.putChat(supergroup(false), false) }
        val m1 = newMessageId()
        val m2 = newMessageId()
        val m3 = newMessageId()
        val m4 = newMessageId()
        val bytes = ByteArray(1500) { (it + 3).toByte() }
        val oneTime = withOneTimeDocument(inChannel(m3, ""), bytes)
        putInCache(inChannel(m1, "a $m1"), inChannel(m2, "deleted $m2"), oneTime, inChannel(m4, "d $m4"))
        writeDownloadedFile(oneTime, bytes)
        storage.emptyMessagesMedia(channelDialogId, arrayListOf(m3))
        deleteInChannel(m2)

        val loaded = loadFromServer(channelDialogId, listOf(inChannel(m4, "d $m4"), withRemovedMedia(inChannel(m3, "")), inChannel(m1, "a $m1")))
        assertEquals(listOf(m4, m3, m2, m1), ids(loaded, m1, m2, m3, m4))
        assertTrue(isMarkedDeleted(loaded.first { it.id == m2 }.messageOwner))
        assertTrue("removed media is back", loaded.first { it.id == m3 }.messageOwner.media.document is TLRPC.TL_document)
    }

    @Test
    fun forumTopic_deletedMessageAndRemovedMediaAreShownAgain_otherTopicsNot() {
        instrumentation.runOnMainSync { controller.putChat(supergroup(true), false) }
        val topic = newMessageId()
        val otherTopic = newMessageId()
        val m1 = newMessageId()
        val m2 = newMessageId()
        val m3 = newMessageId()
        val inOther = newMessageId()
        val m4 = newMessageId()
        val bytes = ByteArray(1200) { (it * 3).toByte() }
        val oneTime = withOneTimeDocument(inChannel(m3, "", topic, true), bytes)
        putInCache(
            inChannel(m1, "t a $m1", topic, true),
            inChannel(m2, "t deleted $m2", topic, true),
            oneTime,
            inChannel(inOther, "other topic $inOther", otherTopic, true),
            inChannel(m4, "t d $m4", topic, true),
        )
        writeDownloadedFile(oneTime, bytes)
        storage.emptyMessagesMedia(channelDialogId, arrayListOf(m3))
        deleteInChannel(m2, inOther)

        val loaded = loadFromServer(channelDialogId,
            listOf(inChannel(m4, "t d $m4", topic, true), withRemovedMedia(inChannel(m3, "", topic, true)), inChannel(m1, "t a $m1", topic, true)),
            topic.toLong(), true)
        assertEquals("topic's own deleted message back, the other topic's not", listOf(m4, m3, m2, m1), ids(loaded, m1, m2, m3, m4, inOther))
        assertTrue(isMarkedDeleted(loaded.first { it.id == m2 }.messageOwner))
        assertTrue("removed media is back", loaded.first { it.id == m3 }.messageOwner.media.document is TLRPC.TL_document)
    }

    @Test
    fun replyThread_deletedReplyIsShownAgain_otherRepliesNot() {
        instrumentation.runOnMainSync { controller.putChat(supergroup(false), false) }
        val root = newMessageId()
        val r1 = newMessageId()
        val r2 = newMessageId()
        val notInThread = newMessageId()
        val r3 = newMessageId()
        putInCache(inChannel(root, "root $root"), inChannel(r1, "r1", root), inChannel(r2, "r2 deleted", root),
            inChannel(notInThread, "elsewhere"), inChannel(r3, "r3", root))
        deleteInChannel(r2, notInThread)

        val loaded = loadFromServer(channelDialogId, listOf(inChannel(r3, "r3", root), inChannel(r1, "r1", root)), root.toLong(), false)
        assertEquals(listOf(r3, r2, r1), ids(loaded, r1, r2, r3, notInThread))
        assertTrue(isMarkedDeleted(loaded.first { it.id == r2 }.messageOwner))
    }

    // ---- 3. secret chats ----

    @Test
    fun secretChat_deletedMessageIsShownAgain() {
        // Secret chat ids are negative and go down: -base is the oldest.
        val base = 1_000 + (Math.random() * 100_000).toInt() * 10
        val oldest = -base
        val deleted = -(base + 10)
        val newest = -(base + 20)
        val deletedMessage = inSecret(deleted, "secret deleted $deleted")
        putInCache(inSecret(oldest, "secret a"), deletedMessage, inSecret(newest, "secret c"))
        // The peer deletes it: secret chats delete by random_id.
        storage.markMessagesAsDeletedByRandoms(arrayListOf(deletedMessage.random_id))
        drainAll()
        assertNotNull("archived", db.getDeleted(selfId, secretDialogId, deleted))

        val loaded = loadFromCache(secretDialogId)
        assertEquals("newest first, deleted one in its place", listOf(newest, deleted, oldest), ids(loaded, oldest, deleted, newest))
        assertTrue(isMarkedDeleted(loaded.first { it.id == deleted }.messageOwner))
    }

    // ---- 5. clearing history keeps the archive ----

    @Test
    fun clearHistory_keepsArchivedDeletedMessages() {
        val m1 = newMessageId()
        val m2 = newMessageId()
        putInCache(incoming(otherUserId, m1, "a $m1"), incoming(otherUserId, m2, "deleted $m2"))
        deletePrivate(m2)
        storage.deleteDialog(otherUserId, 1)
        drainAll()

        assertNotNull("archive kept", db.getDeleted(selfId, otherUserId, m2))
        // After clearing, the server has nothing for this chat.
        val loaded = loadFromServer(otherUserId, emptyList())
        assertEquals(listOf(m2), ids(loaded, m1, m2))
        assertTrue(isMarkedDeleted(loaded.first { it.id == m2 }.messageOwner))
    }
}
