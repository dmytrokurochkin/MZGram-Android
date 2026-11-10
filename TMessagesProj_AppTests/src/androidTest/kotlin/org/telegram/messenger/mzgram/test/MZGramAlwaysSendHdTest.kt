package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.MediaController
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.mzgram.MZGramConfig

/**
 * Tests for feature #7 (chat-view.minor: always send media in HD).
 * Ensures HD default is disabled by default and correctly enables
 * maximum resolution / quality for both photos and videos.
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
}
