package org.telegram.messenger.mzgram.test

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
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
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Another user's one-time media, on the paths the app
// really takes, for photos, videos, voice messages and round videos:
//
//   viewOnce   the user opens view-once media; the chat calls
//              MZGramHistoryController.onOneTimeMediaViewed and
//              createDeleteShowOnceTask as it opens the viewer
//              (ChatActivity.sendSecretMediaDelete), the viewer downloads
//              the file, and on close doDeleteShowOnceTask empties the media;
//   timer      self-destructing media (e.g. 30 s): opening it calls
//              markMessageAsRead2 with the timer, and when the timer runs
//              out the delete task empties the media;
//   unopened   media that arrives and is never opened.
//
// Where the files really are: the one-time viewer (SecretMediaViewer)
// downloads photos and videos into the cache ENCRYPTED, as
// "<name>.enc" with the key in "<internal cache>/<name>.enc.key";
// voice and round messages are played from a plain file in the cache.
//
// After each: an archive row with the FILE (same bytes); the open chat
// shown the media back as ordinary media (updateMessageMedia); and after
// reopening the chat (loading history) the media is there too.
//
// Only APIs that existed before this work, so the same test also runs on
// older commits.
class MZGramOneTimeMediaRealPathTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_800_000_000L + (Math.random() * 1_000_000).toLong()

    private var savedSaveMessageHistory = false

    private val storage get() = MessagesStorage.getInstance(account)
    private val controller get() = MessagesController.getInstance(account)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val loader get() = FileLoader.getInstance(account)

    private fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        instrumentation.runOnMainSync {
            controller.putUser(TLRPC.TL_user().also { it.id = otherUserId; it.access_hash = 1; it.first_name = "other" }, false)
        }
        savedSaveMessageHistory = MZGramConfig.saveDeletedMessages
        MZGramConfig.saveDeletedMessages = true
    }

    @After
    fun tearDown() {
        MZGramConfig.saveDeletedMessages = savedSaveMessageHistory
    }

    // ---- messages ----

    private val VIEW_ONCE = 0x7FFFFFFF

    private var nextId = 100_000 + (Math.random() * 1_000_000).toInt()
    private fun newMessageId(): Int {
        nextId += 10
        return nextId
    }

    private fun now() = ConnectionsManager.getInstance(account).currentTime

    private fun incoming(mid: Int) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = ""
        it.from_id = TLRPC.TL_peerUser().also { p -> p.user_id = otherUserId }
        it.peer_id = TLRPC.TL_peerUser().also { p -> p.user_id = otherUserId }
        it.dialog_id = otherUserId
        it.media_unread = true
        it.flags = it.flags or 256 or 512
    }

    // The location a photo size gets when the message is read from the
    // network or the cache (TLRPC.PhotoSize.TLdeserialize): -photo id and
    // the size type. The app names the file after it, "-<photo id>_<type>.jpg".
    private fun photoSize(type: String, w: Int, photoId: Long) = TLRPC.TL_photoSize().also {
        it.type = type
        it.w = w
        it.h = w * 3 / 4
        it.size = FILE_SIZE
        it.location = TLRPC.TL_fileLocationToBeDeprecated().also { l ->
            l.volume_id = -photoId
            l.local_id = type[0].code
        }
    }

    private fun photoMessage(mid: Int, ttl: Int) = incoming(mid).also {
        it.media = TLRPC.TL_messageMediaPhoto().also { m ->
            m.photo = TLRPC.TL_photo().also { p ->
                p.id = 400_000_000_000L + mid
                p.access_hash = 1
                p.dc_id = 2
                p.date = now()
                p.file_reference = ByteArray(0)
                p.sizes.add(photoSize("x", 800, p.id))
                p.sizes.add(photoSize("y", 1280, p.id))
                p.sizes.add(photoSize("w", 2560, p.id))
            }
            m.ttl_seconds = ttl
            m.flags = m.flags or 1 or 4
        }
        it.ttl = ttl
    }

    private fun documentMessage(mid: Int, ttl: Int, kind: String) = incoming(mid).also {
        val document = TLRPC.TL_document().also { d ->
            d.id = 300_000_000_000L + mid
            d.access_hash = 1
            d.dc_id = 2
            d.date = now()
            d.size = FILE_SIZE.toLong()
            d.file_reference = ByteArray(0)
            when (kind) {
                "video" -> {
                    d.mime_type = "video/mp4"
                    d.attributes.add(TLRPC.TL_documentAttributeVideo().also { a -> a.w = 640; a.h = 480; a.duration = 3.0 })
                }
                "round" -> {
                    d.mime_type = "video/mp4"
                    d.attributes.add(TLRPC.TL_documentAttributeVideo().also { a -> a.w = 240; a.h = 240; a.duration = 3.0; a.round_message = true })
                }
                else -> {
                    d.mime_type = "audio/ogg"
                    d.attributes.add(TLRPC.TL_documentAttributeAudio().also { a -> a.voice = true; a.duration = 3.0 })
                }
            }
        }
        it.media = TLRPC.TL_messageMediaDocument().also { m ->
            m.document = document
            m.ttl_seconds = ttl
            m.flags = m.flags or 1 or 4
        }
        it.ttl = ttl
    }

    private fun message(kind: String, mid: Int, ttl: Int) = if (kind == "photo") photoMessage(mid, ttl) else documentMessage(mid, ttl, kind)

    // ---- where the app puts the downloaded file ----

    // The size the message says its file has. A downloaded file of another
    // size is deleted by FileLoadOperation as broken when a download of it
    // starts, as the one started on arrival does.
    private val FILE_SIZE = 5000

    private fun bytesFor(mid: Int) = ByteArray(FILE_SIZE) { (it * 31 + mid).toByte() }

    // The one-time viewer: photos at the size closest to 1280 px, videos at
    // the message's cache path -- both encrypted.
    private fun viewerFile(kind: String, message: TLRPC.Message): File {
        if (kind == "photo") {
            val size = FileLoader.getClosestPhotoSizeWithSize(message.media.photo.sizes, 1280)
            return loader.getPathToAttach(size, true)
        }
        return loader.getPathToMessage(message)
    }

    private fun writeEncrypted(plainPath: File, bytes: ByteArray) {
        plainPath.parentFile?.mkdirs()
        val keyBytes = ByteArray(48).also { Utilities.random.nextBytes(it) }
        val key = keyBytes.copyOfRange(0, 32)
        val iv = keyBytes.copyOfRange(32, 48)
        val data = bytes.copyOf()
        Utilities.aesCtrDecryptionByteArray(data, key, iv, 0, data.size.toLong(), 0)
        File(plainPath.absolutePath + ".enc").writeBytes(data)
        File(FileLoader.getInternalCacheDir(), plainPath.name + ".enc.key").writeBytes(keyBytes)
    }

    private fun writePlain(path: File, bytes: ByteArray) {
        path.parentFile?.mkdirs()
        path.writeBytes(bytes)
    }

    // Voice and round messages are played from a plain cache file; photos
    // and videos are opened in the encrypting viewer.
    private fun download(kind: String, message: TLRPC.Message, bytes: ByteArray) {
        if (kind == "photo" || kind == "video") {
            writeEncrypted(viewerFile(kind, message), bytes)
        } else {
            writePlain(loader.getPathToMessage(message), bytes)
        }
    }

    // ---- queues ----

    private fun drain(queue: DispatchQueue) {
        val latch = CountDownLatch(1)
        queue.postRunnable { latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
    }

    private fun drainAll() {
        repeat(4) {
            drain(Utilities.stageQueue)
            drain(storage.storageQueue)
        }
    }

    private fun waitFor(timeoutSec: Int, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            if (check()) return true
            Thread.sleep(250)
        }
        return check()
    }

    // ---- what the user gets ----

    private fun archivedFile(mid: Int, bytes: ByteArray): Boolean =
        MZGramHistoryDatabase.getInstance().getAllForDialog(selfId, otherUserId, 10_000)
            .filter { it.messageId == mid }
            .any { it.mediaPath != null && File(it.mediaPath).let { f -> f.exists() && f.readBytes().contentEquals(bytes) } }

    private fun hasMedia(m: TLRPC.Message?): Boolean {
        val media = m?.media ?: return false
        return media is TLRPC.TL_messageMediaPhoto && media.photo is TLRPC.TL_photo ||
            media is TLRPC.TL_messageMediaDocument && media.document is TLRPC.TL_document
    }

    // Shown as ordinary media with its file where the chat looks for it:
    // a chat bubble shows a photo at the size closest to 1280 px from the
    // image folder; documents from the message's path.
    private fun shownWithFile(m: TLRPC.Message?, bytes: ByteArray): Boolean {
        if (!hasMedia(m) || m!!.media.ttl_seconds != 0) return false
        val f = if (m.media is TLRPC.TL_messageMediaPhoto)
            loader.getPathToAttach(FileLoader.getClosestPhotoSizeWithSize(m.media.photo.sizes, AndroidUtilities.getPhotoSize()), false)
        else loader.getPathToMessage(m)
        return f.exists() && f.readBytes().contentEquals(bytes)
    }

    private fun markChatRead() {
        val latch = CountDownLatch(1)
        storage.storageQueue.postRunnable {
            try {
                storage.database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = $otherUserId").stepThis().dispose()
                storage.database.executeFast("UPDATE dialogs SET unread_count = 0 WHERE did = $otherUserId").stepThis().dispose()
            } finally {
                latch.countDown()
            }
        }
        latch.await(30, TimeUnit.SECONDS)
    }

    // Reopening the chat: its newest messages from the local cache.
    private fun reopenChat(mid: Int): TLRPC.Message? {
        markChatRead()
        val guid = ConnectionsManager.generateClassGuid()
        val latch = CountDownLatch(1)
        var found: TLRPC.Message? = null
        val observer = object : NotificationCenter.NotificationCenterDelegate {
            override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
                if (id == NotificationCenter.messagesDidLoad && args[10] == guid && latch.count > 0) {
                    @Suppress("UNCHECKED_CAST")
                    found = (args[2] as ArrayList<MessageObject>).firstOrNull { it.id == mid }?.messageOwner
                    latch.countDown()
                }
            }
        }
        instrumentation.runOnMainSync {
            NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.messagesDidLoad)
            controller.loadMessages(otherUserId, 0, false, 50, 0, 0, true, 0, guid, 2, 0, 0, 0, 0, 0, false)
        }
        latch.await(30, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.messagesDidLoad) }
        return found
    }

    // What an open chat is told when the media is removed.
    private inner class MediaUpdates(private val mid: Int) : NotificationCenter.NotificationCenterDelegate {
        var last: TLRPC.Message? = null
        override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
            val m = args[0] as TLRPC.Message
            if (id == NotificationCenter.updateMessageMedia && m.id == mid) last = m
        }
        fun register() = instrumentation.runOnMainSync { NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.updateMessageMedia) }
        fun unregister() = instrumentation.runOnMainSync { NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.updateMessageMedia) }
    }

    private fun report(name: String, mid: Int, archived: Boolean, openChat: Boolean?, reopened: Boolean) {
        log("ONE-TIME $name mid=$mid archiveFile=$archived openChat=${openChat ?: "n/a"} reopened=$reopened")
        assertTrue("$name: archive row with the file", archived)
        if (openChat != null) assertTrue("$name: open chat shows the media", openChat)
        assertTrue("$name: shown after reopening the chat", reopened)
    }

    // ---- view once: open, view, close ----

    private fun viewOnce(kind: String) {
        val mid = newMessageId()
        val bytes = bytesFor(mid)
        val message = message(kind, mid, VIEW_ONCE)
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        // Voice and round messages are downloaded before they are played.
        if (kind == "voice" || kind == "round") download(kind, message, bytes)

        val updates = MediaUpdates(mid).also { it.register() }
        try {
            var taskId = 0L
            // ChatActivity.sendSecretMediaDelete, as the viewer opens.
            instrumentation.runOnMainSync {
                val shown = MessageObject(account, message, true, false)
                if (MZGramHistoryController.savesChat(otherUserId)) {
                    MZGramHistoryController.getInstance().onOneTimeMediaViewed(account, otherUserId, shown.messageOwner)
                }
                taskId = controller.createDeleteShowOnceTask(otherUserId, mid)
            }
            // The viewer downloads and shows it.
            if (kind == "photo" || kind == "video") download(kind, message, bytes)
            // Closed: the media is removed.
            instrumentation.runOnMainSync { controller.doDeleteShowOnceTask(taskId, otherUserId, mid) }
            drainAll()
            Thread.sleep(1000)
            drainAll()
        } finally {
            updates.unregister()
        }
        report("viewOnce-$kind", mid, archivedFile(mid, bytes), shownWithFile(updates.last, bytes), shownWithFile(reopenChat(mid), bytes))
    }

    @Test fun viewOnce_photo() = viewOnce("photo")
    @Test fun viewOnce_video() = viewOnce("video")
    @Test fun viewOnce_voice() = viewOnce("voice")
    @Test fun viewOnce_round() = viewOnce("round")

    // ---- self-destruct timer: opened, timer runs out ----

    private fun timer(kind: String) {
        val mid = newMessageId()
        val bytes = bytesFor(mid)
        val message = message(kind, mid, 1)
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        download(kind, message, bytes)

        val updates = MediaUpdates(mid).also { it.register() }
        try {
            // Opening it: ChatActivity.sendSecretMessageRead starts the timer.
            instrumentation.runOnMainSync { controller.markMessageAsRead2(otherUserId, mid, null, 1, 0, true) }
            waitFor(60) { updates.last != null }
            drainAll()
            Thread.sleep(1000)
            drainAll()
        } finally {
            updates.unregister()
        }
        report("timer-$kind", mid, archivedFile(mid, bytes), shownWithFile(updates.last, bytes), shownWithFile(reopenChat(mid), bytes))
    }

    @Test fun timer_photo() = timer("photo")
    @Test fun timer_video() = timer("video")

    // ---- never opened ----

    // One-time media that arrives and is never opened (its timer may run
    // out on the sender's side, or the user never looks at it). The app
    // must fetch it on its own, as MZGram Desktop does: a download of it
    // is started as it arrives, and once the file is in, it is archived.
    private fun unopened(kind: String) {
        val mid = newMessageId()
        val bytes = bytesFor(mid)
        val message = message(kind, mid, VIEW_ONCE)
        storage.putMessages(arrayListOf(message), false, true, false, 0, 0, 0)
        drainAll()
        Thread.sleep(500)

        val name = if (kind == "photo")
            FileLoader.getAttachFileName(FileLoader.getClosestPhotoSizeWithSize(message.media.photo.sizes, 1280))
        else FileLoader.getAttachFileName(message.media.document)
        var started = false
        instrumentation.runOnMainSync { started = loader.isLoadingFile(name) }
        log("ONE-TIME unopened-$kind mid=$mid downloadStarted=$started file=$name")

        // The download finishes: a plain file in the cache, as FileLoader
        // writes it, and the fileLoaded notification.
        val target = if (kind == "photo")
            loader.getPathToAttach(FileLoader.getClosestPhotoSizeWithSize(message.media.photo.sizes, 1280), true)
        else loader.getPathToMessage(message)
        writePlain(target, bytes)
        instrumentation.runOnMainSync {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.fileLoaded, name, target)
        }
        drainAll()
        Thread.sleep(1000)
        drainAll()

        assertTrue("unopened-$kind: download started on arrival", started)
        report("unopened-$kind", mid, archivedFile(mid, bytes), null, hasMedia(reopenChat(mid)))
    }

    @Test fun unopened_photo() = unopened("photo")
    @Test fun unopened_video() = unopened("video")
    @Test fun unopened_voice() = unopened("voice")
    @Test fun unopened_round() = unopened("round")
}
