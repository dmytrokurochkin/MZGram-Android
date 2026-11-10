package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramFonts
import org.telegram.ui.mzgram.MZGramAppFont

class MZGramEmbeddedFontsTest {

    @Test
    fun embeddedFontModes_definedAndRecognized() {
        assertEquals(3, MZGramFonts.INTER)
        assertEquals(4, MZGramFonts.FIRA_SANS)

        val prevFont = MZGramConfig.appFont
        try {
            MZGramConfig.setAppFont(MZGramFonts.INTER, null)
            assertEquals(MZGramFonts.INTER, MZGramFonts.mode())
            assertEquals("Inter", MZGramAppFont.title())

            MZGramConfig.setAppFont(MZGramFonts.FIRA_SANS, null)
            assertEquals(MZGramFonts.FIRA_SANS, MZGramFonts.mode())
            assertEquals("Fira Sans", MZGramAppFont.title())
        } finally {
            MZGramConfig.setAppFont(prevFont, null)
        }
    }
}
