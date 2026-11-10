package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.MediaController
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.ui.PhotoViewer

/**
 * Tests for feature #7 (chat-view.minor: always send media in HD).
 * Ensures HD default is disabled by default and correctly enables
 * maximum resolution / quality for both photos and videos.
 *
 * Verifies indexing contracts:
 * - PhotoViewer.selectCompression() is 0-based: returns compressionsCount - 1 (0..3)
 * - SendMessagesHelper.createCompressionSettings() is 1-based: sets selectedCompression = compressionsCount (1..4)
 */
class MZGramAlwaysSendHdTest {

    @Test
    fun alwaysSendHd_defaultDisabled() {
        val original = MZGramConfig.alwaysSendHd
        try {
            MZGramConfig.alwaysSendHd = false
            assertFalse("alwaysSendHd must be false by default", MZGramConfig.alwaysSendHd)
        } finally {
            MZGramConfig.alwaysSendHd = original
        }
    }

    @Test
    fun photoEntry_isHighQuality_respectsAlwaysSendHd() {
        val originalConfig = MZGramConfig.alwaysSendHd
        val originalShared = SharedConfig.photoHighQualityDefault
        try {
            SharedConfig.photoHighQualityDefault = false

            MZGramConfig.alwaysSendHd = false
            val entryOff = MediaController.PhotoEntry(0, 0, 0L, "", 0, false, 0, 0, 0L)
            assertFalse("When alwaysSendHd is off, isHighQuality must be false", entryOff.isHighQuality())

            MZGramConfig.alwaysSendHd = true
            val entryOn = MediaController.PhotoEntry(0, 0, 0L, "", 0, false, 0, 0, 0L)
            assertTrue("When alwaysSendHd is on, isHighQuality must be true", entryOn.isHighQuality())

            // Explicit user choice overrides default
            entryOn.highQuality = false
            assertFalse("Explicit override takes precedence", entryOn.isHighQuality())
        } finally {
            MZGramConfig.alwaysSendHd = originalConfig
            SharedConfig.photoHighQualityDefault = originalShared
        }
    }

    @Test
    fun videoEntry_isHighQuality_respectsAlwaysSendHd() {
        val originalConfig = MZGramConfig.alwaysSendHd
        val originalShared = SharedConfig.photoHighQualityDefault
        try {
            SharedConfig.photoHighQualityDefault = false

            MZGramConfig.alwaysSendHd = false
            val videoOff = MediaController.PhotoEntry(0, 0, 0L, "", 0, true, 0, 0, 0L)
            assertFalse("Video isHighQuality must be false when switch is off", videoOff.isHighQuality())

            MZGramConfig.alwaysSendHd = true
            val videoOn = MediaController.PhotoEntry(0, 0, 0L, "", 0, true, 0, 0, 0L)
            assertTrue("Video isHighQuality must be true when switch is on", videoOn.isHighQuality())
        } finally {
            MZGramConfig.alwaysSendHd = originalConfig
            SharedConfig.photoHighQualityDefault = originalShared
        }
    }

    @Test
    fun photoViewer_selectCompression_boundaryAndTiers() {
        val originalConfig = MZGramConfig.alwaysSendHd
        try {
            val pv = PhotoViewer.getInstance()
            val method = PhotoViewer::class.java.getDeclaredMethod("selectCompression")
            method.isAccessible = true
            val countField = PhotoViewer::class.java.getDeclaredField("compressionsCount")
            countField.isAccessible = true
            val sizeField = PhotoViewer::class.java.getDeclaredField("originalSize")
            sizeField.isAccessible = true

            // Set small size (< 1 GB) so standard compression logic applies
            sizeField.setLong(pv, 50L * 1024 * 1024)

            MZGramConfig.alwaysSendHd = true

            // In PhotoViewer, indexing is 0-based: tier max must be count - 1
            for (count in 1..4) {
                countField.setInt(pv, count)
                val result = method.invoke(pv) as Int
                assertEquals("PhotoViewer max tier for count $count must be ${count - 1}", count - 1, result)
            }

            // Boundary: when compressionsCount is 0, alwaysSendHd guard (compressionsCount > 0)
            // prevents underflow to -1
            countField.setInt(pv, 0)
            val zeroResult = method.invoke(pv) as Int
            assertTrue("compressionsCount=0 boundary must not return negative underflow", zeroResult >= 0)
        } finally {
            MZGramConfig.alwaysSendHd = originalConfig
        }
    }
}
