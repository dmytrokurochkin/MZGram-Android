package org.telegram.messenger.mzgram.test

import android.util.Size
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramCameraX
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramLensStops

// The CameraX round camera: the stops of the zoom slider from the lenses,
// the order of the preview sizes, and the switches off by default.
class MZGramCameraXTest {

    @Test
    fun lensStops_fromTheLenses() {
        // Ultra-wide .5, main 1, telephoto 3: the reach stop 6 is added
        assertArrayEquals(floatArrayOf(0.5f, 1f, 3f, 6f),
            MZGramLensStops.buildToggleStops(0.5f, 10f, floatArrayOf(0.5f, 1f, 3f)), 0.0001f)
        // Without a telephoto lens the slider takes its usual stops
        assertNull(MZGramLensStops.buildToggleStops(0.5f, 10f, floatArrayOf(0.5f, 1f)))
        // The ruler: the stops, the edge and 2 between 1 and 3
        assertArrayEquals(floatArrayOf(0.5f, 1f, 2f, 3f, 6f, 10f),
            MZGramLensStops.buildRulerStops(0.5f, 10f, floatArrayOf(0.5f, 1f, 3f, 6f)), 0.0001f)
    }

    @Test
    fun lensStops_normalized() {
        // Snapped to round numbers near the lens
        assertArrayEquals(floatArrayOf(0.6f, 1f, 3f),
            MZGramLensStops.normalizeRatios(floatArrayOf(0.6f, 1f, 3.05f), 0.6f), 0.0001f)
        // One lens is no stops
        assertEquals(0, MZGramLensStops.normalizeRatios(floatArrayOf(1f), 1f).size)
        // No lens near 1x: no stops
        assertEquals(0, MZGramLensStops.normalizeRatios(floatArrayOf(0.5f, 3f), 0.5f).size)
    }

    @Test
    fun previewSizes_fieldOfViewFirst() {
        val sizes = listOf(Size(640, 480), Size(1920, 1080), Size(1440, 1080), Size(3840, 2160))
        val sorted = MZGramCameraX.sortRoundPreviewSizes(sizes, Size(4000, 3000), 384)
        assertEquals(listOf(Size(1440, 1080), Size(640, 480), Size(1920, 1080), Size(3840, 2160)), sorted)
        // Sizes smaller than the round video go last, the bigger first
        val small = MZGramCameraX.sortRoundPreviewSizes(listOf(Size(320, 240), Size(352, 288), Size(640, 480)), Size(4000, 3000), 384)
        assertEquals(Size(640, 480), small[0])
        assertEquals(Size(352, 288), small[1])
    }

    @Test
    fun switches_offByDefault() {
        // The fields' own values; the saved ones are the user's
        val config = MZGramConfig::class.java
        assertFalse(config.getField("cameraX").getBoolean(null) && !hasSaved("useCameraX"))
        assertFalse(config.getField("cameraXWideLens").getBoolean(null) && !hasSaved("cameraXWideLens"))
    }

    private fun hasSaved(key: String): Boolean {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        return context.getSharedPreferences("mzgram_config", android.content.Context.MODE_PRIVATE).contains(key)
    }
}
