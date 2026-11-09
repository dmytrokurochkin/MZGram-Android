package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramIconPacks

// Icon packs: reading metadata.json, safe archive paths, tinting
// (isTintable) and the SVG fix-up for Telegram's SvgHelper.
class MZGramIconPacksTest {

    @Test
    fun metadata_readsThePackAndDropsBadNames() {
        val pack = MZGramIconPacks.parseMetadata(
            """{"schemaVersion":1,"packId":"custom.abc-1","packName":"Pack","author":"me","version":"2.0",
               "isTintable":false,"icons":{"msg_settings":"a.svg","Bad-Name":"b.png","input_mic":""}}""",
            null, null
        )
        assertNotNull(pack)
        pack!!
        assertEquals("custom.abc-1", pack.key)
        assertEquals("Pack", pack.name)
        assertEquals(false, pack.declaredTintable)
        assertEquals(1, pack.count())
        assertTrue(pack.has("msg_settings"))
        assertFalse(pack.has("Bad-Name"))
        assertNull("no icons object", MZGramIconPacks.parseMetadata("""{"packId":"x"}""", null, null))
        assertNull("not JSON", MZGramIconPacks.parseMetadata("PK", null, null))
        val silent = MZGramIconPacks.parseMetadata("""{"packName":"Only name","icons":{}}""", null, null)
        assertNull("says nothing about tinting", silent!!.declaredTintable)
    }

    @Test
    fun key_unsafeIdsAreHashed() {
        assertEquals("custom.abc-1", MZGramIconPacks.keyFor("custom.abc-1"))
        val hashed = MZGramIconPacks.keyFor("../../evil")
        assertTrue(hashed.startsWith("pack_"))
        assertFalse(hashed.contains("/"))
        assertTrue(MZGramIconPacks.keyFor(".hidden").startsWith("pack_"))
        assertEquals(listOf("a", "b"), MZGramIconPacks.parseKeys("a,,b,a,../x"))
    }

    @Test
    fun archivePaths_noWayOut() {
        assertEquals("icons/a.png", MZGramIconPacks.safeEntry("icons/a.png"))
        assertNull(MZGramIconPacks.safeEntry("../a.png"))
        assertNull(MZGramIconPacks.safeEntry("icons/../../a.png"))
        assertNull(MZGramIconPacks.safeEntry("/data/a.png"))
        assertNull(MZGramIconPacks.safeEntry("icons\\a.png"))
        assertNull(MZGramIconPacks.safeEntry("C:a.png"))
        assertNull(MZGramIconPacks.safeEntry("icons//a.png"))
        assertEquals("svg", MZGramIconPacks.pictureType("A.SVG"))
        assertEquals("jpg", MZGramIconPacks.pictureType("a.jpeg"))
        assertNull(MZGramIconPacks.pictureType("a.xml"))
    }

    @Test
    fun tinting_greyIsTintedColourIsNot() {
        val white = 0xffffffff.toInt()
        val grey = 0xff808080.toInt()
        val red = 0xffff2020.toInt()
        val clear = 0x00ff0000
        assertFalse("white and grey", MZGramIconPacks.isColorful(intArrayOf(white, grey, grey, clear)))
        assertTrue("mostly red", MZGramIconPacks.isColorful(intArrayOf(red, red, grey, white)))
        assertFalse("a transparent red does not count", MZGramIconPacks.isColorful(intArrayOf(clear, clear, white)))
        assertFalse("nothing visible", MZGramIconPacks.isColorful(intArrayOf(clear)))
    }

    @Test
    fun svg_viewBoxMovedToZeroAndCurrentColorWhite() {
        val svg = MZGramIconPacks.normalizeSvg(
            """<svg xmlns="http://www.w3.org/2000/svg" height="48px" viewBox="0 -960 960 960" width="48px" fill="#e3e3e3"><path d="M0 0"/></svg>"""
        )!!
        assertTrue(svg, svg.startsWith("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 960 960">"""))
        assertTrue(svg, svg.contains("""<g transform="translate(0,960)" fill="#e3e3e3">"""))
        assertTrue(svg, svg.endsWith("""<path d="M0 0"/></g></svg>"""))
        val sized = MZGramIconPacks.normalizeSvg("""<svg width="32px" height="32"><path fill="currentColor" d="M1 1"/></svg>""")!!
        assertTrue(sized, sized.contains("""viewBox="0 0 32 32""""))
        assertTrue(sized, sized.contains("#ffffff"))
        assertFalse(sized, sized.contains("currentColor"))
        assertNull(MZGramIconPacks.normalizeSvg("<html></html>"))
    }
}
