package org.telegram.messenger.mzgram.test

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramProtectedContent
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

// Settings > MZGram > Message menu > "Forward and save protected content".
// In a channel whose owner turned on "Restrict saving content":
//   - the app's checks (forward, save, copy, screenshots) let everything
//     through while the switch is on, and keep the restriction while it is
//     off;
//   - forwarding a protected message sends a copy -- a new message with its
//     text and a fresh upload of its file -- since the server does not
//     forward it; with the switch off it is still a plain forward.
class MZGramProtectedContentTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherUserId = 7_900_000_000L + (Math.random() * 1_000_000).toLong()
    private val channelId = 2_900_000_000L + (Math.random() * 1_000_000).toLong()
    private val channelDialogId = -channelId

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MessagesController.getInstance(account)
    private val loader get() = FileLoader.getInstance(account)

    private var savedSwitch = false
    private val received = CopyOnWriteArrayList<MessageObject>()
    private val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
        if (id == NotificationCenter.didReceiveNewMessages && args[0] == selfId) {
            @Suppress("UNCHECKED_CAST")
            received.addAll(args[1] as ArrayList<MessageObject>)
        }
    }

    @Before
    fun setUp() {
        BuildVars.LOGS_ENABLED = true
        val self = TLRPC.TL_user().also {
            it.id = selfId
            it.access_hash = 1
            it.first_name = "MZGram test self"
            it.self = true
        }
        UserConfig.getInstance(account).setCurrentUser(self)
        instrumentation.runOnMainSync {
            controller.putUser(self, false)
            controller.putUser(TLRPC.TL_user().also {
                it.id = otherUserId
                it.access_hash = 1
                it.first_name = "MZGram test other"
            }, false)
            controller.putChat(TLRPC.TL_channel().also {
                it.id = channelId
                it.access_hash = 1
                it.title = "MZGram test protected channel"
                it.broadcast = true
                it.noforwards = true
            }, false)
            NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.didReceiveNewMessages)
        }
        savedSwitch = MZGramConfig.saveProtectedContent
    }

    @After
    fun tearDown() {
        MZGramConfig.saveProtectedContent = savedSwitch
        instrumentation.runOnMainSync {
            NotificationCenter.getInstance(account).removeObserver(observer, NotificationCenter.didReceiveNewMessages)
        }
    }

    private fun now() = ConnectionsManager.getInstance(account).currentTime

    private fun post(mid: Int, text: String) = TLRPC.TL_message().also {
        it.id = mid
        it.date = now()
        it.message = text
        it.peer_id = TLRPC.TL_peerChannel().also { p -> p.channel_id = channelId }
        it.dialog_id = channelDialogId
        it.post = true
        it.noforwards = true
    }

    private fun messageObject(message: TLRPC.Message): MessageObject {
        var result: MessageObject? = null
        instrumentation.runOnMainSync { result = MessageObject(account, message, false, false) }
        return result!!
    }

    private fun forward(message: MessageObject) {
        instrumentation.runOnMainSync {
            SendMessagesHelper.getInstance(account).sendMessage(arrayListOf(message), selfId, false, false, true, 0, 0)
        }
    }

    private fun waitForCopy(check: (MessageObject) -> Boolean): MessageObject? {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            received.firstOrNull(check)?.let { return it }
            Thread.sleep(200)
        }
        return received.firstOrNull(check)
    }

    // ---- what the app allows ----

    @Test
    fun appChecks_followTheSwitch() {
        val message = messageObject(post(101, "protected"))

        MZGramConfig.saveProtectedContent = false
        assertTrue("restricted with the switch off", controller.isChatNoForwards(channelId))
        assertTrue(controller.isPeerNoForwards(channelDialogId))
        assertTrue(message.isNoforwards)
        assertFalse("no Forward in the menu", message.canForwardMessage())

        MZGramConfig.saveProtectedContent = true
        assertFalse("not restricted with the switch on", controller.isChatNoForwards(channelId))
        assertFalse(controller.isPeerNoForwards(channelDialogId))
        assertFalse(message.isNoforwards)
        assertTrue("Forward in the menu", message.canForwardMessage())

        assertTrue("the server's rule is still known", controller.isChatNoForwardsOnServer(controller.getChat(channelId)))
        assertTrue(MZGramProtectedContent.isProtected(account, message))
    }

    // ---- forwarding ----

    @Test
    fun forward_text_isSentAsACopy() {
        MZGramConfig.saveProtectedContent = true
        val text = "protected text ${System.nanoTime()}"
        forward(messageObject(post(201, text)))
        val copy = waitForCopy { it.messageOwner.message == text }
        assertNotNull("a new message in Saved Messages", copy)
        assertNull("not a forward", copy!!.messageOwner.fwd_from)
        MZGramScreens.log("protected forward text: id=${copy.id} fwd=${copy.messageOwner.fwd_from}")
    }

    @Test
    fun forward_withTheSwitchOff_isAPlainForward() {
        MZGramConfig.saveProtectedContent = false
        val text = "plain forward ${System.nanoTime()}"
        forward(messageObject(post(202, text)))
        val sent = waitForCopy { it.messageOwner.message == text }
        assertNotNull(sent)
        assertNotNull("a forward, as before", sent!!.messageOwner.fwd_from)
    }

    private fun writeJpeg(file: File) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 120, 200))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun photoPost(mid: Int, caption: String) = post(mid, caption).also {
        val photoId = 600_000_000_000L + mid + (Math.random() * 1_000_000).toLong()
        it.media = TLRPC.TL_messageMediaPhoto().also { m ->
            m.flags = 1
            m.photo = TLRPC.TL_photo().also { p ->
                p.id = photoId
                p.access_hash = 1
                p.dc_id = 2
                p.date = now()
                p.file_reference = ByteArray(0)
                p.sizes.add(TLRPC.TL_photoSize().also { s ->
                    s.type = "y"
                    s.w = 1280
                    s.h = 960
                    s.size = 1000
                    s.location = TLRPC.TL_fileLocationToBeDeprecated().also { l ->
                        l.volume_id = -photoId
                        l.local_id = 'y'.code
                    }
                })
            }
        }
    }

    @Test
    fun forward_photo_isSentAsACopyWithItsFile() {
        MZGramConfig.saveProtectedContent = true
        val caption = "protected photo ${System.nanoTime()}"
        val message = photoPost(301, caption)
        val file = loader.getPathToMessage(message)
        writeJpeg(file)
        forward(messageObject(message))
        val copy = waitForCopy { it.messageOwner.message == caption }
        assertNotNull("a new message in Saved Messages", copy)
        assertNull("not a forward", copy!!.messageOwner.fwd_from)
        assertTrue("a photo", copy.messageOwner.media is TLRPC.TL_messageMediaPhoto)
        assertEquals("a new upload", 0L, copy.messageOwner.media.photo.access_hash)
        // A photo is re-encoded for upload (SendMessagesHelper.generatePhotoSizes),
        // so it goes out from a new file made from the downloaded one.
        assertTrue("the upload file exists", File(copy.messageOwner.attachPath).let { it.exists() && it.length() > 0 })
        assertTrue("made from the downloaded 320x240 photo", copy.messageOwner.media.photo.sizes.any { it.w == 320 && it.h == 240 })
        MZGramScreens.log("protected forward photo: id=${copy.id} attachPath=${copy.messageOwner.attachPath}")
    }

    private fun voicePost(mid: Int) = post(mid, "").also {
        it.media = TLRPC.TL_messageMediaDocument().also { m ->
            m.flags = 1
            m.document = TLRPC.TL_document().also { d ->
                d.id = 700_000_000_000L + mid + (Math.random() * 1_000_000).toLong()
                d.access_hash = 1
                d.dc_id = 2
                d.date = now()
                d.size = 3000
                d.mime_type = "audio/ogg"
                d.file_reference = ByteArray(0)
                d.attributes.add(TLRPC.TL_documentAttributeAudio().also { a -> a.voice = true; a.duration = 2.0 })
            }
        }
    }

    @Test
    fun forward_voice_isSentAsACopyWithItsFile() {
        MZGramConfig.saveProtectedContent = true
        val message = voicePost(401)
        val file = loader.getPathToMessage(message)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(3000) { it.toByte() })
        forward(messageObject(message))
        val copy = waitForCopy { it.messageOwner.attachPath == file.absolutePath }
        assertNotNull("a new message in Saved Messages", copy)
        assertNull("not a forward", copy!!.messageOwner.fwd_from)
        assertTrue("still a voice message", copy.isVoice)
        assertEquals("a new upload", 0L, copy.messageOwner.media.document.access_hash)
    }

    // Not on the device yet: downloaded first, then sent.
    @Test
    fun forward_notDownloaded_isDownloadedThenSent() {
        MZGramConfig.saveProtectedContent = true
        val message = voicePost(501)
        val file = loader.getPathToMessage(message)
        file.delete()
        forward(messageObject(message))
        val name = FileLoader.getAttachFileName(message.media.document)
        var loading = false
        val end = System.currentTimeMillis() + 5_000
        while (!loading && System.currentTimeMillis() < end) {
            loading = loader.isLoadingFile(name)
            Thread.sleep(100)
        }
        assertTrue("download started", loading)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(3000) { it.toByte() })
        instrumentation.runOnMainSync {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.fileLoaded, name, file)
        }
        val copy = waitForCopy { it.messageOwner.attachPath == file.absolutePath }
        assertNotNull("sent once downloaded", copy)
        assertNull("not a forward", copy!!.messageOwner.fwd_from)
    }
}
