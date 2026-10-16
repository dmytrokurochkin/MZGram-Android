package org.telegram.messenger.mzgram.test

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramHistoryController
import org.telegram.messenger.mzgram.MZGramHistoryDatabase
import org.telegram.messenger.mzgram.MZGramHistoryMessage
import java.io.File

// Moving the archive to MZGram's new package (org.mzgram.messenger) from the
// old one: Export in the old app gives the archive file, Import in the new
// one reads it back through the content address the system file picker
// gives, whichever app the file came from.
class MZGramArchiveMoveTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exportedArchive_isImportedFromAContentAddress() {
        val db = MZGramHistoryDatabase.getInstance()
        val self = 7_000_000_001L
        val dialog = 7_300_000_000L + (Math.random() * 1_000_000).toLong()
        val mid = 100_000 + (Math.random() * 1_000_000).toInt()
        db.insert(MZGramHistoryMessage().also {
            it.kind = MZGramHistoryMessage.KIND_DELETED
            it.accountUserId = self
            it.dialogId = dialog
            it.messageId = mid
            it.date = (System.currentTimeMillis() / 1000).toInt()
            it.text = "moved with the archive $mid"
        })
        val exported = MZGramHistoryController.getInstance().exportArchive()
        // As the user keeps it: a file elsewhere, opened by its content address
        val kept = File(context.filesDir, "cache/mzgram-move/mzgram_archive_export.db")
        kept.parentFile!!.mkdirs()
        exported.copyTo(kept, overwrite = true)

        db.delete(self, dialog, mid)
        assertNull("gone before the import", db.getDeleted(self, dialog, mid))

        // androidx is not on this module's compile classpath
        val uri = Class.forName("androidx.core.content.FileProvider")
            .getMethod("getUriForFile", Context::class.java, String::class.java, File::class.java)
            .invoke(null, context, context.packageName + ".provider", kept) as Uri
        assertEquals("content", uri.scheme)
        context.contentResolver.openInputStream(uri)!!.use { MZGramHistoryController.getInstance().importArchive(it) }
        val back = db.getDeleted(self, dialog, mid)
        assertNotNull("imported", back)
        assertEquals("moved with the archive $mid", back!!.text)
        kept.delete()
        db.delete(self, dialog, mid)
    }
}
