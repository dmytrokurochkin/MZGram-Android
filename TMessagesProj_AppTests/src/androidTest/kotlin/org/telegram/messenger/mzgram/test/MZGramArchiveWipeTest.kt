package org.telegram.messenger.mzgram.test

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramArchiveWipe
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramHistoryMessage
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.UItem
import org.telegram.ui.mzgram.MZGramSettingsActivity
import java.io.File

// Settings > MZGram > Archive > Clear archive. The owner's phone: the box
// closed, the archive was gone, and nothing said so. Now the Delete button
// shows progress, the folders go aside at once and are deleted in the
// background, and a message at the bottom of the screen says the archive
// is cleared and how much was freed.
class MZGramArchiveWipeTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val account = -1_000_000_000_000L
    private val dialog = -1_000_000_000_002L

    @Before
    fun setUp() {
        val ui = instrumentation.uiAutomation
        val info = ui.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        ui.serviceInfo = info
    }

    private fun write(file: File, size: Int) {
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(size) { 'a'.code.toByte() })
    }

    @Test
    fun folders_goAsideAtOnce_andAreDeleted() {
        val root = File(context.cacheDir, "mzgram_wipe_test").also { it.deleteRecursively() }
        val media = File(root, "media")
        val attachments = File(root, "Saved Attachments")
        write(File(media, "1/2/2_10.jpg"), 1000)
        write(File(media, "1/3/3_11.pdf"), 2000)
        write(File(attachments, "5_12.mp4"), 3000)
        val aside = MZGramArchiveWipe.setAside(listOf(media, attachments, File(root, "not here")))
        assertEquals("set aside: $aside", 2, aside.size)
        assertFalse("the folders are still where the archive looks", media.exists() || attachments.exists())
        for (folder in aside) assertTrue("aside: $folder", folder.isDirectory && folder.name.contains(MZGramArchiveWipe.ASIDE))
        assertEquals("an unfinished wipe is found", 2, MZGramArchiveWipe.leftovers(listOf(media, attachments)).size)
        val removed = MZGramArchiveWipe.remove(aside)
        assertEquals(3, removed.files)
        assertEquals(6000L, removed.bytes)
        assertTrue(removed.complete)
        assertTrue("nothing left", aside.none { it.exists() } && MZGramArchiveWipe.leftovers(listOf(media, attachments)).isEmpty())
        // A second wipe while the first is still deleting
        write(File(media, "a.jpg"), 10)
        val first = MZGramArchiveWipe.setAside(listOf(media))
        write(File(media, "b.jpg"), 10)
        val second = MZGramArchiveWipe.setAside(listOf(media))
        assertEquals(1, first.size)
        assertNotEquals("two wipes share a folder", first, second)
        assertEquals(2, MZGramArchiveWipe.remove(first + second).files)
        assertTrue(MZGramArchiveWipe.remove(emptyList()).complete)
        root.deleteRecursively()
    }

    private fun windowTexts(): List<String> {
        val result = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        return result
    }

    private fun findNode(text: String): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text) return node
            for (i in 0 until node.childCount) walk(node.getChild(i))?.let { return it }
            return null
        }
        return walk(instrumentation.uiAutomation.rootInActiveWindow)
    }

    // A real tap on the screen, as the user's finger
    private fun tap(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY(), 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY(), 0))
    }

    private fun row(messageId: Int): MZGramHistoryMessage {
        val row = MZGramHistoryMessage()
        row.kind = MZGramHistoryMessage.KIND_DELETED
        row.accountUserId = account
        row.dialogId = dialog
        row.messageId = messageId
        row.fromId = 12345
        row.date = 1_700_000_000
        row.entityCreateDate = 1_700_000_100
        row.text = "will be cleared"
        return row
    }

    @Test
    fun clearArchive_showsTheResult() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })

        val db = MZGramHistoryDatabase.getInstance()
        // What another test's wipe set aside is not this test's
        MZGramArchiveWipe.remove(MZGramArchiveWipe.leftovers(MZGramHistoryDatabase.archiveFolders()))
        db.insert(row(556001))
        val media = MZGramHistoryDatabase.mediaRoot()
        write(File(media, "${account}/${dialog}/a.jpg"), 4096)
        write(File(media, "${account}/${dialog}/b.pdf"), 8192)
        assertTrue(db.hasHistory(account, dialog, 556001))

        val page = MZGramSettingsActivity(MZGramSettingsActivity.SECTION_ARCHIVE)
        MZGramScreens.open(page)
        assertTrue("Archive opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() === page && page.fragmentView?.isShown == true })
        Thread.sleep(800)
        val id = MZGramSettingsActivity::class.java.getDeclaredField("BUTTON_WIPE_ARCHIVE").also { it.isAccessible = true }.getInt(null)
        instrumentation.runOnMainSync {
            val onClick = page.javaClass.getDeclaredMethod("onClick", UItem::class.java, View::class.java, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            onClick.isAccessible = true
            onClick.invoke(page, UItem.asButton(id, "x"), null, 0, 0f, 0f)
        }
        val confirm = LocaleController.getString(R.string.MZGramWipeArchiveConfirm)
        assertTrue("the box", MZGramScreens.waitFor(10) { windowTexts().any { it.contains(confirm) } })
        MZGramScreens.capture("wipe-box")
        val delete = LocaleController.getString(R.string.MZGramDelete)
        val button = findNode(delete)
        assertTrue("the Delete button", button != null)
        tap(button!!)

        val cleared = LocaleController.getString(R.string.MZGramArchiveCleared)
        val freed = LocaleController.getString(R.string.MZGramArchiveFilesDeleted).substringBefore("%1\$s").trim()
        var seen = ArrayList<String>()
        val shown = MZGramScreens.waitFor(30) {
            seen = ArrayList(windowTexts())
            seen.any { it.contains(cleared) || it.startsWith(freed) }
        }
        MZGramScreens.capture("wipe-result")
        MZGramScreens.log("after Clear archive: $seen")
        assertFalse("the box closed", seen.any { it.contains(confirm) })
        assertTrue("a message at the bottom says the archive is cleared: $seen", shown)
        assertTrue("the freed size is shown", MZGramScreens.waitFor(30) { windowTexts().any { it.startsWith(freed) } })
        assertFalse("the rows are gone", db.hasHistory(account, dialog, 556001))
        assertFalse("the files are gone", File(media, "${account}/${dialog}/a.jpg").exists())
        assertTrue("nothing left aside", MZGramScreens.waitFor(30) {
            MZGramArchiveWipe.leftovers(listOf(media, MZGramHistoryDatabase.attachmentsRoot())).isEmpty()
        })
        instrumentation.runOnMainSync { page.finishFragment(false) }
    }
}
