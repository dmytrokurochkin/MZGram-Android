package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramMonet
import kotlin.math.abs

// Monet themes: the palette tokens of their files read from a fixed palette,
// the CIELAB math in place of Material's HCT, and the theme names.
class MZGramMonetTest {

    // Accent palettes blue-ish, neutral ones almost grey; the lightness of a
    // shade is its tone, as in Android's palette.
    private val palette = object : MZGramMonet.Palette {
        override fun shade(palette: Int, shade: Int): Int? {
            val i = MZGramMonet.SHADES.indexOf(shade)
            if (i < 0 || palette !in 0..4) return null
            val tone = MZGramMonet.SHADE_TONES[i].toDouble()
            val (a, b) = if (palette < 3) Pair(10.0, -40.0) else Pair(1.0, -3.0)
            return MZGramMonet.fromLab(tone, a, b, 0xff)
        }

        override fun role(role: String, dark: Boolean): Int? = null
    }

    private fun lightness(color: Int) = MZGramMonet.lab(color)[0]

    @Test
    fun lab_roundTrip() {
        for (color in intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff3390ec.toInt(), 0xffe53935.toInt(), 0xff4caf50.toInt(), 0xff7f7f7f.toInt())) {
            val lab = MZGramMonet.lab(color)
            val back = MZGramMonet.fromLab(lab[0], lab[1], lab[2], 0xff)
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue(Integer.toHexString(color), abs(((color shr shift) and 0xff) - ((back shr shift) and 0xff)) <= 1)
            }
        }
        assertEquals(100.0, lightness(0xffffffff.toInt()), 0.1)
        assertEquals(0.0, lightness(0xff000000.toInt()), 0.1)
    }

    @Test
    fun tokens_shadesRolesAndTones() {
        assertEquals(palette.shade(0, 600), MZGramMonet.resolve("a1_600", null, palette))
        assertEquals(palette.shade(0, 600), MZGramMonet.resolve("primary_light", null, palette))
        assertEquals(palette.shade(0, 0), MZGramMonet.resolve("on_primary_light", null, palette))
        assertEquals(palette.shade(2, 700), MZGramMonet.resolve("tertiary_30", null, palette))
        // Tones between the system's shades: surface_light is n2 at 98
        assertEquals(98.0, lightness(MZGramMonet.resolve("surface_light", null, palette)!!), 1.0)
        assertEquals(12.0, lightness(MZGramMonet.resolve("surface_container_dark", null, palette)!!), 1.0)
        assertEquals(palette.shade(0, 600), MZGramMonet.resolve("monet_a1_600", null, palette))
    }

    @Test
    fun tokens_fixedChangedAndUnknown() {
        assertEquals(0, MZGramMonet.resolve("transparent", null, palette))
        assertEquals(0xffb3261e.toInt(), MZGramMonet.resolve("error_light", null, palette))
        assertEquals(128, MZGramMonet.resolve("white(a=50)", null, palette)!! ushr 24)
        val base = MZGramMonet.resolve("a1_100", null, palette)!!
        val darker = MZGramMonet.resolve("a1_100_90", null, palette)!!
        assertTrue(lightness(darker) < lightness(base))
        assertNull(MZGramMonet.resolve("a1_601", null, palette))
        assertNull(MZGramMonet.resolve("nothing", null, palette))
        assertNull(MZGramMonet.resolve("-12345", null, palette))
        assertNull(MZGramMonet.resolve("blue_140", null, palette))
        assertTrue(MZGramMonet.resolve("blue_40", null, palette) != null)
    }

    @Test
    fun harmonize_turnsAtMostFifteenDegrees() {
        val red = 0xfff44336.toInt()
        val accent = palette.shade(0, 600)!!
        val before = MZGramMonet.lch(red)
        val after = MZGramMonet.lch(MZGramMonet.harmonize(red, accent))
        val turned = 180.0 - abs(abs(after[2] - before[2]) - 180.0)
        assertTrue("turned $turned", turned <= 15.5)
        assertTrue("turned $turned", turned > 1.0)
        assertEquals(before[0], after[0], 1.0)
    }

    @Test
    fun names_andFiles() {
        assertTrue(MZGramMonet.isMonet(MZGramMonet.LIGHT))
        assertTrue(MZGramMonet.isMonetDark(MZGramMonet.AMOLED))
        assertFalse(MZGramMonet.isMonetDark(MZGramMonet.LIGHT))
        assertFalse(MZGramMonet.isMonet("Night"))
        assertEquals("mzgram_monet_dark.attheme", MZGramMonet.assetName(1, MZGramMonet.STYLE_ANDROID))
        assertEquals("mzgram_monet_light_classic.attheme", MZGramMonet.assetName(0, MZGramMonet.STYLE_TELEGRAM))
        assertTrue(MZGramMonet.isMonetAsset("mzgram_monet_amoled.attheme"))
        assertFalse(MZGramMonet.isMonetAsset("night.attheme"))
        assertFalse(MZGramMonet.isMonetAsset(null))
        // Any other file keeps its numbers
        assertNull(MZGramMonet.color("night.attheme", "surface_light", "windowBackgroundWhite"))
    }
}
