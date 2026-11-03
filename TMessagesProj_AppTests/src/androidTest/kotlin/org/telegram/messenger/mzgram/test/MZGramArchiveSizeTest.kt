package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramArchiveSize
import org.telegram.messenger.mzgram.MZGramArchiveWipe
import org.telegram.ui.mzgram.MZGramSettingsActivity
import java.io.File

// Settings > MZGram > Archive: the archive's size by kind, as Telegram's
// Storage Usage shows it: the messages database, the copies of media, Saved
// Attachments and the rest; what a wipe set aside is not counted.
class MZGramArchiveSizeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun write(file: File, size: Int) {
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(size) { 'a'.code.toByte() })
    }

    @Test
    fun measure_countsEachKind() {
        val root = File(context.cacheDir, "mzgram_size_test").also { it.deleteRecursively() }
        val folder = File(root, "mzgram")
        val media = File(folder, "media")
        val attachments = File(root, "Downloads/MZGram/Saved Attachments")
        write(File(folder, "history.db"), 1000)
        write(File(folder, "history.db-wal"), 24)
        write(File(media, "1/2/a.jpg"), 3000)
        write(File(media, "1/3/b.pdf"), 500)
        write(File(folder, "notes.txt"), 7)
        write(File(folder, "media" + MZGramArchiveWipe.ASIDE + "1/x.jpg"), 9000)
        write(File(attachments, "c.mp4"), 200)
        val sizes = MZGramArchiveSize.measure(folder, "history.db", media, attachments)
        assertEquals("database", 1024L, sizes.database)
        assertEquals("media", 3500L, sizes.media)
        assertEquals("attachments", 200L, sizes.attachments)
        assertEquals("other", 7L, sizes.other)
        assertEquals("total", 4731L, sizes.total())
        // Nothing there yet
        assertEquals(0L, MZGramArchiveSize.measure(File(root, "none"), "history.db", File(root, "none/media"), File(root, "none/a")).total())
        root.deleteRecursively()
    }

    @Test
    fun archiveTopic_showsTheSize() {
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val page = MZGramSettingsActivity(MZGramSettingsActivity.SECTION_ARCHIVE)
        MZGramScreens.open(page)
        assertTrue("Archive opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() === page && page.fragmentView?.isShown == true })
        val view = page.sizeViewForTest()
        assertTrue("the size is in the Archive topic", view != null)
        assertTrue("counted", MZGramScreens.waitFor(60) { !view!!.countingForTest() })
        val expected = MZGramArchiveSize.measureArchive()
        MZGramScreens.capture("archive-size")
        assertEquals("the total", AndroidUtilities.formatFileSize(expected.total()), view!!.totalForTest())
        for (i in 0 until 4) {
            assertEquals("kind $i", AndroidUtilities.formatFileSize(expected.parts()[i]), view.sizeForTest(i))
        }
        assertTrue("on the screen", view.isShown)
        assertTrue("the title", LocaleController.getString(R.string.MZGramArchiveSize).isNotBlank())
    }
}
