package org.telegram.messenger.mzgram.test

import android.graphics.RectF
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.appearance.MZGramM3SwitchHelper
import org.telegram.ui.mzgram.MZGramSettingsActivity

class MZGramM3CardsTest {

    private var initialM3 = false
    private var initialCards = false

    @Before
    fun setUp() {
        initialM3 = MZGramConfig.m3Switches
        initialCards = MZGramConfig.cardsDesign
        MZGramConfig.m3Switches = false
        MZGramConfig.cardsDesign = false
    }

    @After
    fun tearDown() {
        MZGramConfig.m3Switches = initialM3
        MZGramConfig.cardsDesign = initialCards
    }

    @Test
    fun testConfigToggles() {
        assertFalse(MZGramConfig.m3Switches)
        MZGramConfig.toggleM3Switches()
        assertTrue(MZGramConfig.m3Switches)
        MZGramConfig.toggleM3Switches()
        assertFalse(MZGramConfig.m3Switches)

        assertFalse(MZGramConfig.cardsDesign)
        MZGramConfig.toggleCardsDesign()
        assertTrue(MZGramConfig.cardsDesign)
        MZGramConfig.toggleCardsDesign()
        assertFalse(MZGramConfig.cardsDesign)
    }

    @Test
    fun testM3SwitchHelperMetrics() {
        assertTrue(MZGramM3SwitchHelper.stateLayerRadius() > 0)
        assertTrue(MZGramM3SwitchHelper.trackOutlineWidth() > 0f)
        assertTrue(MZGramM3SwitchHelper.iconStrokeWidth() > 0f)
        assertTrue(MZGramM3SwitchHelper.clockHandLength(true) > 0f)

        val trackRect = RectF()
        MZGramM3SwitchHelper.setTrackBounds(trackRect, 100, 50)
        assertTrue(trackRect.width() > 0f)
        assertTrue(trackRect.height() > 0f)
        assertEquals(50f, trackRect.centerX(), 0.01f)
        assertEquals(25f, trackRect.centerY(), 0.01f)

        val thumbRect = RectF()
        MZGramM3SwitchHelper.setThumbBounds(thumbRect, 50f, 25f, 0f, false, 0f, 0f)
        assertTrue(thumbRect.width() > 0f)
        assertTrue(thumbRect.height() > 0f)
    }

    @Test
    fun testSettingsAppearanceSectionExists() {
        assertEquals(1, MZGramSettingsActivity.SECTION_APPEARANCE)
        assertEquals(10, MZGramSettingsActivity.SECTIONS_COUNT)
    }
}
