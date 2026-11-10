package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.LocaleController
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.system.MZGramPersianDate

/**
 * Tests for feature N14 (system.minor: Persian / Solar Hijri calendar).
 * Verifies accurate Gregorian <-> Jalali conversion, leap year transitions,
 * year rollovers, digit localization, boundary dates, and LocaleController integration.
 */
class MZGramPersianCalendarTest {

    @Test
    fun persianCalendar_disabledByDefault() {
        assertFalse("persianCalendar switch must be disabled by default", MZGramConfig.persianCalendar)
    }

    @Test
    fun gregorianToJalali_accurateConversionKnownDates() {
        // 2024-03-20 -> Nowruz 1403 (Farvardin 1, 1403)
        val nowruz = MZGramPersianDate.gregorianToJalali(2024, 3, 20)
        assertEquals("Year 1403", 1403, nowruz[0])
        assertEquals("Farvardin (Month 1)", 1, nowruz[1])
        assertEquals("Day 1", 1, nowruz[2])

        // 2024-08-30 -> Shahrivar 9, 1403
        val shahrivar = MZGramPersianDate.gregorianToJalali(2024, 8, 30)
        assertEquals(1403, shahrivar[0])
        assertEquals("Shahrivar (Month 6)", 6, shahrivar[1])
        assertEquals(9, shahrivar[2])

        // 2024-01-01 -> Dey 11, 1402
        val newYear2024 = MZGramPersianDate.gregorianToJalali(2024, 1, 1)
        assertEquals(1402, newYear2024[0])
        assertEquals("Dey (Month 10)", 10, newYear2024[1])
        assertEquals(11, newYear2024[2])

        // Unix Epoch: 1970-01-01 -> Dey 11, 1348
        val epoch = MZGramPersianDate.gregorianToJalali(1970, 1, 1)
        assertEquals(1348, epoch[0])
        assertEquals(10, epoch[1])
        assertEquals(11, epoch[2])
    }

    @Test
    fun roundTripConversion_jalaliAndGregorian() {
        val testDates = listOf(
            intArrayOf(1403, 1, 1),
            intArrayOf(1403, 6, 31),
            intArrayOf(1403, 7, 1),
            intArrayOf(1403, 12, 30), // Leap year 1403 has 30 days in Esfand
            intArrayOf(1402, 12, 29), // Normal year 1402 has 29 days in Esfand
            intArrayOf(1399, 1, 1)
        )
        for (jalali in testDates) {
            val grg = MZGramPersianDate.jalaliToGregorian(jalali[0], jalali[1], jalali[2])
            val backToJalali = MZGramPersianDate.gregorianToJalali(grg[0], grg[1], grg[2])
            assertEquals("Round trip year for ${jalali[0]}/${jalali[1]}/${jalali[2]}", jalali[0], backToJalali[0])
            assertEquals("Round trip month for ${jalali[0]}/${jalali[1]}/${jalali[2]}", jalali[1], backToJalali[1])
            assertEquals("Round trip day for ${jalali[0]}/${jalali[1]}/${jalali[2]}", jalali[2], backToJalali[2])
        }
    }

    @Test
    fun leapYearCalculation_correctLeapStatus() {
        // In Solar Hijri calendar, 1399 and 1403 are leap years (33-year cycle remainder rule)
        assertTrue("1403 is a Jalali leap year", MZGramPersianDate.isLeapJalaliYear(1403))
        assertTrue("1399 is a Jalali leap year", MZGramPersianDate.isLeapJalaliYear(1399))
        assertFalse("1400 is not a Jalali leap year", MZGramPersianDate.isLeapJalaliYear(1400))
        assertFalse("1401 is not a Jalali leap year", MZGramPersianDate.isLeapJalaliYear(1401))
        assertFalse("1402 is not a Jalali leap year", MZGramPersianDate.isLeapJalaliYear(1402))
    }

    @Test
    fun yearTransitionAndBoundaries() {
        // Last day of year 1402: 2024-03-19 -> Esfand 29, 1402
        val lastDay1402 = MZGramPersianDate.gregorianToJalali(2024, 3, 19)
        assertEquals(1402, lastDay1402[0])
        assertEquals(12, lastDay1402[1])
        assertEquals(29, lastDay1402[2])

        // First day of year 1403: 2024-03-20 -> Farvardin 1, 1403
        val firstDay1403 = MZGramPersianDate.gregorianToJalali(2024, 3, 20)
        assertEquals(1403, firstDay1403[0])
        assertEquals(1, firstDay1403[1])
        assertEquals(1, firstDay1403[2])
    }

    @Test
    fun formatting_persianDigitsAndMonthNames() {
        val date = MZGramPersianDate(2024, 8, 30) // Shahrivar 9, 1403
        assertEquals("شهریور", date.monthName())
        assertEquals("Shahrivar", date.monthNameLatin())

        val formattedFa = date.persianMonthDay
        assertTrue("Must contain Shahrivar in Persian", formattedFa.contains("شهریور"))
        assertTrue("Must contain Persian digit 9 (\\u06F9)", formattedFa.contains('\u06F9'))

        val formattedLatin = date.persianMonthDayLatin
        assertEquals("9 Shahrivar", formattedLatin)

        val fullFa = date.persianNormalDate
        assertTrue("Full date must contain year 1403 in Persian digits", fullFa.contains("\u06F1\u06F4\u06F0\u06F3"))
    }

    @Test
    fun persianDigitsConverter_replacesCorrectly() {
        val latin = "0123456789"
        val expected = "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9"
        assertEquals(expected, MZGramPersianDate.toPersianDigits(latin))
        assertEquals("", MZGramPersianDate.toPersianDigits(null))
    }

    @Test
    fun localeControllerIntegration_switchChangesFormatting() {
        val original = MZGramConfig.persianCalendar
        try {
            // August 30, 2024 12:00:00 UTC (1725019200L seconds)
            val timestampSec = 1725019200L

            MZGramConfig.persianCalendar = false
            val defaultFormat = LocaleController.formatDateChat(timestampSec, false)
            assertNotNull(defaultFormat)
            assertFalse("Default formatting must NOT contain Persian digits when switch is off",
                defaultFormat.contains('\u06F1') || defaultFormat.contains('\u06F4'))

            MZGramConfig.persianCalendar = true
            val persianFormat = LocaleController.formatDateChat(timestampSec, false)
            assertNotNull(persianFormat)
            assertTrue("Persian formatting must contain Persian month or Persian digits when switch is on",
                persianFormat.contains("شهریور") || persianFormat.contains('\u06F9'))
        } finally {
            MZGramConfig.persianCalendar = original
        }
    }
}
