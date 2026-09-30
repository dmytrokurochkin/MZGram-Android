package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramZalgoFilter

class MZGramZalgoFilterTest {

    @After
    fun tearDown() {
        if (MZGramConfig.stripZalgoText) {
            MZGramConfig.toggleStripZalgoText()
        }
    }

    @Test
    fun stripDoesNothingWhenToggleIsOff() {
        MZGramConfig.stripZalgoText = false
        val zalgo = "Ź̶̡à́l̶g̵ò"
        assertEquals(zalgo, MZGramZalgoFilter.strip(zalgo))
    }

    @Test
    fun stripRemovesCombiningMarksWhenToggleIsOn() {
        MZGramConfig.stripZalgoText = true
        val zalgo = "Ź̶̡à́l̶g̵ò"
        assertEquals("Zalgo", MZGramZalgoFilter.strip(zalgo))
    }

    @Test
    fun stripLeavesPlainTextUnchanged() {
        MZGramConfig.stripZalgoText = true
        val plain = "Just a normal message with emoji 😀 and punctuation!"
        assertEquals(plain, MZGramZalgoFilter.strip(plain))
    }

    @Test
    fun stripHandlesNullAndEmpty() {
        MZGramConfig.stripZalgoText = true
        assertEquals(null, MZGramZalgoFilter.strip(null))
        assertEquals("", MZGramZalgoFilter.strip(""))
    }
}
