package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramHistoryController
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramHistoryMessage
import org.telegram.ui.Components.UItem
import org.telegram.ui.mzgram.MZGramSettingsActivity
import java.io.File

// Settings > MZGram > Archive > Export as the user taps it, on MZGram's own
// package: the app opens Android's "save as" choice (here answered by the
// test with a file) and writes the archive there, without crashing. Import
// reads a picked file back, and a file that is not an archive is refused
// with the archive kept.
class MZGramArchiveExportTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val db get() = MZGramHistoryDatabase.getInstance()
    private val self = 7_000_000_001L
    private val dialog = 7_400_000_000L + (Math.random() * 1_000_000).toLong()
    private val mid = 100_000 + (Math.random() * 1_000_000).toInt()
    private val dir get() = File(context.filesDir, "cache/mzgram-export-test")
    private var monitor: Instrumentation.ActivityMonitor? = null

    @Before
    fun setUp() {
        dir.mkdirs()
        db.insert(MZGramHistoryMessage().also {
            it.kind = MZGramHistoryMessage.KIND_DELETED
            it.accountUserId = self
            it.dialogId = dialog
            it.messageId = mid
            it.date = (System.currentTimeMillis() / 1000).toInt()
            it.text = "exported $mid"
        })
    }

    @After
    fun tearDown() {
        monitor?.let { instrumentation.removeMonitor(it) }
        db.delete(self, dialog, mid)
        dir.listFiles()?.forEach { it.delete() }
    }

    // A content address of a file, as a document picker gives one
    private fun contentUri(file: File): Uri =
        // androidx is not on this module's compile classpath
        Class.forName("androidx.core.content.FileProvider")
            .getMethod("getUriForFile", Context::class.java, String::class.java, File::class.java)
            .invoke(null, context, context.packageName + ".provider", file) as Uri

    // The Archive page, open on the screen
    private fun archivePage(): MZGramSettingsActivity {
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val page = MZGramSettingsActivity(MZGramSettingsActivity.SECTION_ARCHIVE)
        MZGramScreens.open(page)
        assertTrue("archive page shown", MZGramScreens.waitFor(30) { (MZGramScreens.listViewOf(page)?.childCount ?: 0) > 0 })
        return page
    }

    private fun tap(page: MZGramSettingsActivity, title: String) {
        instrumentation.runOnMainSync {
            val items = ArrayList<UItem>()
            val fill = MZGramSettingsActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, org.telegram.ui.Components.UniversalAdapter::class.java)
            fill.isAccessible = true
            fill.invoke(page, items, null)
            val row = items.first { it.text?.toString() == title }
            val onClick = MZGramSettingsActivity::class.java.getDeclaredMethod("onClick", UItem::class.java, View::class.java, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            onClick.isAccessible = true
            onClick.invoke(page, row, null, 0, 0f, 0f)
        }
    }

    private fun rowsIn(file: File): Int {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { saved ->
            saved.rawQuery("SELECT COUNT(*) FROM history_message WHERE dialogId = ? AND messageId = ?", arrayOf(dialog.toString(), mid.toString())).use {
                it.moveToFirst()
                return it.getInt(0)
            }
        }
    }

    @Test
    fun export_opensTheSaveChoice_andWritesTheArchiveThere() {
        val saved = File(dir, "picked.db")
        saved.writeBytes(ByteArray(0))
        var asked: Intent? = null
        monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CREATE_DOCUMENT) {
                    return null
                }
                asked = intent
                val answer = Intent().setData(contentUri(saved)).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                return Instrumentation.ActivityResult(Activity.RESULT_OK, answer)
            }
        }.also { instrumentation.addMonitor(it) }

        val page = archivePage()
        tap(page, context.getString(org.telegram.messenger.R.string.MZGramExportArchive))
        assertTrue("the save choice opened", MZGramScreens.waitFor(30) { asked != null })
        assertEquals("mzgram_archive_export.db", asked!!.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue("the archive written to the picked file", MZGramScreens.waitFor(30) { saved.length() > 0 })
        assertTrue("written completely", MZGramScreens.waitFor(10) { runCatching { rowsIn(saved) }.getOrDefault(0) == 1 })
        assertTrue("the app is still there", MZGramScreens.waitFor(10) { page.fragmentView?.isShown == true })
        MZGramScreens.capture("archive-exported")
    }

    @Test
    fun import_readsThePickedFile_andRefusesOneThatIsNotAnArchive() {
        val page = archivePage()
        val good = File(dir, "good.db")
        MZGramHistoryController.getInstance().exportArchive().copyTo(good, overwrite = true)
        val result = MZGramSettingsActivity::class.java.getDeclaredMethod("onActivityResultFragment", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
        val importCode = MZGramSettingsActivity::class.java.getDeclaredField("REQUEST_CODE_IMPORT_ARCHIVE").also { it.isAccessible = true }.getInt(null)

        // Not an archive: the archive stays as it is
        val broken = File(dir, "broken.db")
        broken.writeText("not an MZGram archive")
        instrumentation.runOnMainSync { result.invoke(page, importCode, Activity.RESULT_OK, Intent().setData(contentUri(broken))) }
        Thread.sleep(3000)
        assertNotNull("the archive kept after a broken file", db.getDeleted(self, dialog, mid))

        db.delete(self, dialog, mid)
        instrumentation.runOnMainSync { result.invoke(page, importCode, Activity.RESULT_OK, Intent().setData(contentUri(good))) }
        assertTrue("imported", MZGramScreens.waitFor(30) { db.getDeleted(self, dialog, mid) != null })
        assertTrue("the app is still there", page.fragmentView?.isShown == true)
    }
}
