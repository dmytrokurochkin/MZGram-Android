package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramPills
import org.telegram.ui.mzgram.pills.MZGramPillStackView

// The pill bar of the search bar: short numbers (conflict rule 5.2), the
// swipe angle (rule 1.3) and the saved choice of pills.
class MZGramPillsTest {

    @Test
    fun shortNumber_alwaysShort_alsoWithExactNumbers() {
        val saved = MZGramConfig.disableNumberRounding
        try {
            for (exact in listOf(false, true)) {
                MZGramConfig.disableNumberRounding = exact
                assertEquals("999", MZGramPills.shortNumber(999))
                assertEquals("1K", MZGramPills.shortNumber(1000))
                assertEquals("4.8K", MZGramPills.shortNumber(4800))
                assertEquals("4.7K", MZGramPills.shortNumber(4777))
                assertEquals("12K", MZGramPills.shortNumber(12_345))
                assertEquals("rounded down, never 1000K", "999K", MZGramPills.shortNumber(999_999))
                assertEquals("1M", MZGramPills.shortNumber(1_000_000))
                assertEquals("1.4M", MZGramPills.shortNumber(1_458_923))
                assertEquals("3.4B", MZGramPills.shortNumber(3_400_000_000))
                assertEquals("-4.8K", MZGramPills.shortNumber(-4800))
                assertTrue(MZGramPills.shortNumber(Long.MIN_VALUE).endsWith("T"))
            }
        } finally {
            MZGramConfig.disableNumberRounding = saved
        }
    }

    @Test
    fun swipe_onlySteeperThan25Degrees() {
        assertTrue("straight up", MZGramPillStackView.isSwipe(0f, -40f))
        assertTrue("45 degrees", MZGramPillStackView.isSwipe(30f, 30f))
        assertTrue("30 degrees", MZGramPillStackView.isSwipe(100f, 58f))
        assertFalse("20 degrees: left to the folders", MZGramPillStackView.isSwipe(100f, 36f))
        assertFalse("flat", MZGramPillStackView.isSwipe(60f, 0f))
    }

    @Test
    fun choice_unknownIdsAreDropped() {
        assertEquals(emptyList<Int>(), MZGramPills.parse("9999,abc,"))
        assertEquals("", MZGramPills.join(emptyList()))
        assertEquals(MZGramPills.ALL.toList(), MZGramPills.complete(listOf(9999)))
    }
}
