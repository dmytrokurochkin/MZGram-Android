package org.telegram.messenger.mzgram.test

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.media.MZGramVideoSubtitles
import java.io.File

/**
 * Tests for feature N12 (media.minor: subtitles).
 * Verifies MIME resolution, empty and corrupted subtitle file handling,
 * state persistence, and default switch behavior.
 */
class MZGramVideoSubtitlesTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun videoSubtitles_disabledByDefault() {
        assertFalse("videoSubtitles toggle must be disabled by default", MZGramConfig.videoSubtitles)
    }

    @Test
    fun mimeTypeResolution_supportedAndUnsupportedFormats() {
        // SRT
        val srtUri = Uri.parse("file:///sdcard/Download/movie.srt")
        assertEquals(MZGramVideoSubtitles.MIME_SRT, MZGramVideoSubtitles.resolveMimeType(srtUri))

        // VTT
        val vttUri = Uri.parse("file:///sdcard/Download/movie.vtt")
        assertEquals(MZGramVideoSubtitles.MIME_VTT, MZGramVideoSubtitles.resolveMimeType(vttUri))

        // Uppercase extensions
        val srtUpper = Uri.parse("file:///sdcard/Download/movie.SRT")
        assertEquals(MZGramVideoSubtitles.MIME_SRT, MZGramVideoSubtitles.resolveMimeType(srtUpper))

        // Unsupported formats
        val txtUri = Uri.parse("file:///sdcard/Download/readme.txt")
        assertNull("TXT should not resolve to subtitle MIME unless content resolver specifies", MZGramVideoSubtitles.resolveMimeType(txtUri))

        val mp4Uri = Uri.parse("file:///sdcard/Download/video.mp4")
        assertNull("MP4 must be unsupported format for subtitles", MZGramVideoSubtitles.resolveMimeType(mp4Uri))

        val nullUri: Uri? = null
        assertNull("Null URI must resolve to null", MZGramVideoSubtitles.resolveMimeType(nullUri))
    }

    @Test
    fun loadFromUri_emptyOrCorruptedFile_failsGracefully() {
        // Non-existent file
        val nonExistent = Uri.parse("file:///sdcard/Download/does_not_exist_xyz123.srt")
        val resultNonExistent = MZGramVideoSubtitles.loadFromUri(nonExistent)
        assertFalse("Non-existent subtitle file must fail", resultNonExistent.isSuccess)
        assertEquals(MZGramVideoSubtitles.LoadError.LOAD_FAILED, resultNonExistent.error())

        // Empty file (0 bytes)
        val emptyFile = File(context.cacheDir, "test_empty_subtitles.srt")
        emptyFile.createNewFile()
        try {
            val emptyUri = Uri.fromFile(emptyFile)
            val resultEmpty = MZGramVideoSubtitles.loadFromUri(emptyUri)
            assertFalse("0-byte subtitle file must fail", resultEmpty.isSuccess)
            assertEquals(MZGramVideoSubtitles.LoadError.LOAD_FAILED, resultEmpty.error())
        } finally {
            emptyFile.delete()
        }

        // Unsupported file format
        val dummyExe = File(context.cacheDir, "test_file.exe")
        dummyExe.writeText("binary data")
        try {
            val exeUri = Uri.fromFile(dummyExe)
            val resultUnsupported = MZGramVideoSubtitles.loadFromUri(exeUri)
            assertFalse("Unsupported extension must fail", resultUnsupported.isSuccess)
            assertEquals(MZGramVideoSubtitles.LoadError.UNSUPPORTED_FORMAT, resultUnsupported.error())
        } finally {
            dummyExe.delete()
        }
    }

    @Test
    fun loadFromUri_validSubtitleFile_succeeds() {
        val validSrt = File(context.cacheDir, "test_sample.srt")
        validSrt.writeText("1\n00:00:01,000 --> 00:00:04,000\nHello MZGram Subtitles!\n")
        try {
            val uri = Uri.fromFile(validSrt)
            val result = MZGramVideoSubtitles.loadFromUri(uri)
            assertTrue("Valid subtitle file must load successfully", result.isSuccess)
            assertEquals(MZGramVideoSubtitles.LoadError.NONE, result.error())
            assertNotNull(result.subtitleState())
            assertEquals(MZGramVideoSubtitles.MIME_SRT, result.subtitleState().mimeType())
            assertEquals(validSrt.name, result.subtitleState().getDisplayName())
            assertTrue(result.subtitleState().isValid)

            val external = result.subtitleState().toExternalSubtitle()
            assertNotNull(external)
            assertEquals(MZGramVideoSubtitles.MIME_SRT, external.mimeType)
            assertEquals(Uri.fromFile(validSrt), external.uri)
        } finally {
            validSrt.delete()
        }
    }

    @Test
    fun subtitleStatePersistence_saveRestoreClear() {
        val testFile = File(context.cacheDir, "persistence_test.vtt")
        testFile.writeText("WEBVTT\n\n00:01.000 --> 00:04.000\nPersisted cue")
        try {
            val state = MZGramVideoSubtitles.SubtitleState(testFile.absolutePath, MZGramVideoSubtitles.MIME_VTT, "English Subtitles")
            val videoKey = "doc_99998888111"

            MZGramVideoSubtitles.save(videoKey, state)

            val restored = MZGramVideoSubtitles.restore(videoKey)
            assertNotNull("Saved subtitle state must be restored", restored)
            assertEquals(testFile.absolutePath, restored?.path())
            assertEquals(MZGramVideoSubtitles.MIME_VTT, restored?.mimeType())
            assertEquals("English Subtitles", restored?.getDisplayName())

            MZGramVideoSubtitles.clear(videoKey)
            val afterClear = MZGramVideoSubtitles.restore(videoKey)
            assertNull("Subtitle state must be null after clear", afterClear)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun areSame_correctlyComparesStates() {
        val state1 = MZGramVideoSubtitles.SubtitleState("/path/to/sub.srt", MZGramVideoSubtitles.MIME_SRT, "Sub")
        val state2 = MZGramVideoSubtitles.SubtitleState("/path/to/sub.srt", MZGramVideoSubtitles.MIME_SRT, "Sub")
        val state3 = MZGramVideoSubtitles.SubtitleState("/path/to/other.srt", MZGramVideoSubtitles.MIME_SRT, "Other")

        assertTrue("Identical states must be considered same", MZGramVideoSubtitles.areSame(state1, state2))
        assertFalse("Different states must not be same", MZGramVideoSubtitles.areSame(state1, state3))
        assertTrue("Null states must be handled safely", MZGramVideoSubtitles.areSame(null, null))
        assertFalse("Null vs non-null must return false", MZGramVideoSubtitles.areSame(state1, null))
    }
}
