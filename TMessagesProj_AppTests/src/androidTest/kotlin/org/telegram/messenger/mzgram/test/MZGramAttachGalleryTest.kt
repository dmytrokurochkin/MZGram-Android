package org.telegram.messenger.mzgram.test

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.MediaController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.PhotoAttachPhotoCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout
import org.telegram.ui.PhotoViewer

// The gallery of the attach menu in a chat: the camera tile comes first, as
// in the original app, and tapping the first photo opens that photo, not the
// camera. Checked with the instant camera on and off (Settings > MZGram >
// Media and calls > "Disable instant camera").
class MZGramAttachGalleryTest {

    private val account = 0
    private val otherUserId = 7_000_000_002L
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private var savedDisableInstantCamera = false
    private val inserted = ArrayList<Uri>()
    private var chat: ChatActivity? = null

    @Before
    fun setUp() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        savedDisableInstantCamera = MZGramConfig.disableInstantCamera
        val pkg = context.packageName
        for (permission in listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_EXTERNAL_STORAGE", "CAMERA")) {
            MZGramScreens.shell("pm grant $pkg android.permission.$permission")
        }
        for (i in 1..3) {
            inserted.add(insertPhoto("mzgram-attach-$i", Color.rgb(40 * i, 120, 200)))
        }
    }

    @After
    fun tearDown() {
        MZGramConfig.disableInstantCamera = savedDisableInstantCamera
        instrumentation.runOnMainSync {
            if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
                PhotoViewer.getInstance().closePhoto(false, false)
            }
            chat?.chatAttachAlert?.dismiss()
            chat?.finishFragment()
        }
        for (uri in inserted) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (ignore: Exception) {
            }
        }
    }

    private fun insertPhoto(name: String, color: Int): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MZGramTest")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return uri
    }

    // The screen cannot be captured while the live camera preview is on it
    // (the screenshot comes back empty); the checks do not depend on it.
    private fun screenshot(name: String) {
        try {
            MZGramScreens.capture(name)
        } catch (e: NullPointerException) {
            MZGramScreens.log("$name: no screenshot while the camera preview is shown")
        }
    }

    private fun photoLayout(): ChatAttachAlertPhotoLayout? = chat?.chatAttachAlert?.photoLayout

    private fun grid(layout: ChatAttachAlertPhotoLayout): ViewGroup = layout.gridView as ViewGroup

    private fun adapterPosition(grid: ViewGroup, child: View): Int =
        grid.javaClass.getMethod("getChildAdapterPosition", View::class.java).invoke(grid, child) as Int

    private fun adapterNeedsCamera(layout: ChatAttachAlertPhotoLayout): Boolean {
        val adapter = ChatAttachAlertPhotoLayout::class.java.getDeclaredField("adapter").also { it.isAccessible = true }.get(layout)
        return adapter.javaClass.getDeclaredField("needCamera").also { it.isAccessible = true }.getBoolean(adapter)
    }

    private fun tap(view: View) {
        val rect = MZGramScreens.screenRect(view)
        val x = (rect[0] + rect[2]) / 2f
        val y = (rect[1] + rect[3]) / 2f
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun openGalleryAndTapFirstPhoto(name: String) {
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val other = TLRPC.TL_user().also {
            it.id = otherUserId
            it.first_name = "MZGram test peer"
        }
        instrumentation.runOnMainSync {
            MessagesController.getInstance(account).putUser(other, false)
            MediaController.loadGalleryPhotosAlbums(0)
        }
        assertTrue("gallery loaded", MZGramScreens.waitFor(30) { (MediaController.allMediaAlbumEntry?.photos?.size ?: 0) >= 3 })
        val chat = ChatActivity(Bundle().apply { putLong("user_id", otherUserId) })
        this.chat = chat
        MZGramScreens.open(chat)
        assertTrue("chat shown", MZGramScreens.waitFor(30) { chat.chatActivityEnterView?.isShown == true && chat.chatActivityEnterView.height > 0 })
        instrumentation.runOnMainSync {
            ChatActivity::class.java.getDeclaredMethod("openAttachMenu").also { it.isAccessible = true }.invoke(chat)
        }
        assertTrue("attach menu shown", MZGramScreens.waitFor(30) {
            val layout = photoLayout()
            layout != null && layout.isShown && MZGramScreens.findView(grid(layout), PhotoAttachPhotoCell::class.java) { it.height > 0 } != null
        })
        Thread.sleep(1500)
        screenshot("$name-gallery")

        // The camera tile is the first item, as in the original app.
        var needsCamera = false
        var firstChildPosition = -1
        var firstIsPhoto = false
        var cell: PhotoAttachPhotoCell? = null
        var cellPosition = Int.MAX_VALUE
        instrumentation.runOnMainSync {
            val layout = photoLayout()!!
            val grid = grid(layout)
            needsCamera = adapterNeedsCamera(layout)
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                val position = adapterPosition(grid, child)
                if (position == 0) {
                    firstChildPosition = 0
                    firstIsPhoto = child is PhotoAttachPhotoCell
                }
                if (child is PhotoAttachPhotoCell && child.isShown && child.photoEntry != null && position in 0 until cellPosition) {
                    cell = child
                    cellPosition = position
                }
            }
        }
        MZGramScreens.log("$name: camera tile=$needsCamera, item 0 shown=${firstChildPosition == 0} photo=$firstIsPhoto, first photo at $cellPosition")
        assertTrue("$name: the gallery starts with the camera tile", needsCamera && firstChildPosition == 0 && !firstIsPhoto)
        assertTrue("$name: a photo is shown", cell != null)
        val entry = cell!!.photoEntry

        tap(cell!!)
        val opened = MZGramScreens.waitFor(15) { PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible || photoLayout()?.cameraOpened == true }
        Thread.sleep(1000)
        screenshot("$name-after-tap")
        var cameraOpened = false
        var shown: Any? = null
        instrumentation.runOnMainSync {
            cameraOpened = photoLayout()?.cameraOpened == true
            if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible) {
                val viewer = PhotoViewer.getInstance()
                val index = PhotoViewer::class.java.getDeclaredField("currentIndex").also { it.isAccessible = true }.getInt(viewer)
                val list = PhotoViewer::class.java.getDeclaredField("imagesArrLocals").also { it.isAccessible = true }.get(viewer) as List<*>
                shown = list.getOrNull(index)
            }
        }
        val shownPath = (shown as? MediaController.PhotoEntry)?.path
        MZGramScreens.log("$name: after the tap camera=$cameraOpened, viewer shows $shownPath, tapped ${entry.path}")
        assertTrue("$name: something opened", opened)
        assertFalse("$name: the tap on the first photo opened the camera", cameraOpened)
        assertTrue("$name: the tapped photo is shown: ${entry.path}, shown $shownPath", shownPath == entry.path)
    }

    @Test
    fun instantCameraOn_firstPhotoTapOpensThatPhoto() {
        MZGramConfig.disableInstantCamera = false
        openGalleryAndTapFirstPhoto("attach-instant-camera-on")
    }

    @Test
    fun instantCameraOff_cameraTileShownAndFirstPhotoTapOpensThatPhoto() {
        MZGramConfig.disableInstantCamera = true
        openGalleryAndTapFirstPhoto("attach-instant-camera-off")
    }
}
