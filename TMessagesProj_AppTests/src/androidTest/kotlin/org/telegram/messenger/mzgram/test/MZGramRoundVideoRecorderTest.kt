package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.utils.recorder.round.RoundVideoSession
import org.telegram.utils.recorder.round.RoundVideoSwitchTimingStore

class MZGramRoundVideoRecorderTest {

    @Test
    fun roundVideoConfig_defaultSettingsAreOff() {
        val originalCamera2 = MZGramConfig.roundVideoCamera2
        val originalHighFps = MZGramConfig.roundVideoHighFps
        val originalBitrate = MZGramConfig.roundVideoBitrate
        try {
            MZGramConfig.roundVideoCamera2 = false
            MZGramConfig.roundVideoHighFps = false
            MZGramConfig.roundVideoBitrate = 1200000

            assertFalse(MZGramConfig.roundVideoCamera2)
            assertFalse(MZGramConfig.roundVideoHighFps)
            assertEquals(1200000, MZGramConfig.roundVideoBitrate)

            MZGramConfig.toggleRoundVideoCamera2()
            assertTrue(MZGramConfig.roundVideoCamera2)
            MZGramConfig.toggleRoundVideoCamera2()
            assertFalse(MZGramConfig.roundVideoCamera2)

            MZGramConfig.toggleRoundVideoHighFps()
            assertTrue(MZGramConfig.roundVideoHighFps)
            MZGramConfig.toggleRoundVideoHighFps()
            assertFalse(MZGramConfig.roundVideoHighFps)

            MZGramConfig.setRoundVideoBitrate(2000000)
            assertEquals(2000000, MZGramConfig.roundVideoBitrate)
        } finally {
            MZGramConfig.roundVideoCamera2 = originalCamera2
            MZGramConfig.roundVideoHighFps = originalHighFps
            MZGramConfig.roundVideoBitrate = originalBitrate
        }
    }

    @Test
    fun roundVideoSession_frameRateValues() {
        assertEquals(30, RoundVideoSession.FrameRate.FPS_30.value)
        assertEquals(60, RoundVideoSession.FrameRate.FPS_60.value)
    }

    @Test
    fun roundVideoSession_outputResolutions() {
        assertEquals(480, RoundVideoSession.OutputResolution.P480.size)
        assertEquals(360, RoundVideoSession.OutputResolution.P360.size)
    }

    @Test
    fun roundVideoSession_cameraResolutionsExist() {
        val resolutions = RoundVideoSession.CameraResolution.values()
        assertTrue(resolutions.contains(RoundVideoSession.CameraResolution.HIGH))
        assertTrue(resolutions.contains(RoundVideoSession.CameraResolution.MEDIUM))
        assertTrue(resolutions.contains(RoundVideoSession.CameraResolution.LOW))
    }

    @Test
    fun roundVideoSession_cameraFacingEnums() {
        val facings = RoundVideoSession.CameraFacing.values()
        assertTrue(facings.contains(RoundVideoSession.CameraFacing.FRONT))
        assertTrue(facings.contains(RoundVideoSession.CameraFacing.BACK))
    }

    @Test
    fun roundVideoSwitchTimingStore_returnsExpectedKeys() {
        val f2b = RoundVideoSwitchTimingStore.getStoreKey(
            RoundVideoSession.CameraFacing.FRONT,
            RoundVideoSession.CameraFacing.BACK
        )
        val b2f = RoundVideoSwitchTimingStore.getStoreKey(
            RoundVideoSession.CameraFacing.BACK,
            RoundVideoSession.CameraFacing.FRONT
        )
        assertEquals("front_to_back", f2b)
        assertEquals("back_to_front", b2f)
    }
}
