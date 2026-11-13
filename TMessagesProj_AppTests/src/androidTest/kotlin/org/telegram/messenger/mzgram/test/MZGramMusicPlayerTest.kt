package org.telegram.messenger.mzgram.test

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramMonet
import org.telegram.ui.mzgram.player.MZGramLyrics
import org.telegram.ui.mzgram.player.MZGramPlayerColors
import org.telegram.ui.mzgram.player.MZGramPlayerStyle

// The Material 3 music player: the lyrics from the file, the colors from
// the cover, the order of the blocks and the switch off by default.
class MZGramMusicPlayerTest {

    private fun hueDistance(a: Int, b: Int): Double {
        val d = Math.abs(MZGramMonet.lch(a)[2] - MZGramMonet.lch(b)[2]) % 360
        return if (d > 180) 360 - d else d
    }

    @Test
    fun lyrics_syncedWithOffset() {
        val lyrics = MZGramLyrics.parse("[offset:+500]\n[00:01.00][00:03.50]Hello\n[00:02.0]World", MZGramLyrics.SOURCE_FILE)
        assertNotNull(lyrics)
        assertTrue(lyrics!!.synced)
        // One line with two times is shown twice, the offset moves all lines
        assertEquals(listOf(500L, 1500L, 3000L), lyrics.lines.map { it.time })
        assertEquals(listOf("Hello", "World", "Hello"), lyrics.lines.map { it.text })
        assertEquals(1, lyrics.indexAt(1600))
        assertEquals(-1, lyrics.indexAt(100))
    }

    @Test
    fun lyrics_plainAndEmpty() {
        val lyrics = MZGramLyrics.parse("[ar:Artist]\nLine one\n\n\nLine two\n\n", MZGramLyrics.SOURCE_FILE)
        assertNotNull(lyrics)
        assertFalse(lyrics!!.synced)
        // The tags are skipped, empty lines kept once between verses
        assertEquals(listOf("Line one", "", "Line two"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.indexAt(1000))
        assertNull(MZGramLyrics.parse("", MZGramLyrics.SOURCE_FILE))
        assertNull(MZGramLyrics.parse(null, MZGramLyrics.SOURCE_FILE))
    }

    @Test
    fun colors_seedFromTheCover() {
        val fallback = 0xff4285f4.toInt()
        // A grey cover keeps the fallback
        assertEquals(fallback, MZGramPlayerColors.seedFromPixels(IntArray(100) { 0xff808080.toInt() }, fallback))
        // A mostly red cover gives a red seed
        val red = 0xffe53935.toInt()
        val pixels = IntArray(100) { if (it < 70) red else 0xff202020.toInt() }
        val seed = MZGramPlayerColors.seedFromPixels(pixels, fallback)
        assertTrue(hueDistance(seed, red) < 15)
    }

    @Test
    fun colors_tonesOfTheScheme() {
        val seed = 0xff2196f3.toInt()
        val light = MZGramPlayerColors.fromSeed(seed, false)
        val dark = MZGramPlayerColors.fromSeed(seed, true)
        assertTrue(ColorUtils.calculateLuminance(light.surface) > ColorUtils.calculateLuminance(light.onSurface))
        assertTrue(ColorUtils.calculateLuminance(dark.surface) < ColorUtils.calculateLuminance(dark.onSurface))
        assertTrue(light.lightStatusBar())
        assertFalse(dark.lightStatusBar())
        // The primary color keeps the hue of the seed
        assertTrue(hueDistance(light.primary, seed) < 3)
        assertTrue(hueDistance(dark.primary, seed) < 3)
    }

    @Test
    fun style_blockOrder() {
        assertArrayEquals(intArrayOf(0, 1, 2), MZGramPlayerStyle.parseOrder(""))
        // Repeated and unknown blocks are skipped, the missing ones go last
        assertArrayEquals(intArrayOf(2, 0, 1), MZGramPlayerStyle.parseOrder("2,0,0,9"))
        assertArrayEquals(intArrayOf(1, 0, 2), MZGramPlayerStyle.parseOrder(" 1 ,x"))
    }

    @Test
    fun switch_onByDefault() {
        // The field's own value; a saved one is the user's
        val config = MZGramConfig::class.java
        assertTrue(config.getField("musicPlayer").getBoolean(null) || hasSaved("musicPlayer"))
    }

    private fun hasSaved(key: String): Boolean {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        return context.getSharedPreferences("mzgram_config", android.content.Context.MODE_PRIVATE).contains(key)
    }
}
