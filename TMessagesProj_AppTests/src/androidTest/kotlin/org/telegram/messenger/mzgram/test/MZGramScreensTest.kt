package org.telegram.messenger.mzgram.test

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramHaptics
import org.telegram.messenger.mzgram.MZGramRelativeLastSeen
import org.telegram.messenger.mzgram.MZGramSaveFolder

// The features of the reference screenshots (2026-11-13): each test checks
// what its switch changes, with the switch back as it was afterwards.
class MZGramScreensTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var vibration = true

    @Before
    fun setUp() {
        vibration = MZGramConfig.inAppVibration
    }

    @After
    fun tearDown() {
        if (MZGramConfig.inAppVibration != vibration) {
            MZGramConfig.toggleInAppVibration()
        }
        instrumentation.runOnMainSync { MZGramHaptics.apply() }
    }

    // Save folder: Telegram by default; a name of one's own loses what a
    // folder name cannot have, and an empty one is Telegram again
    @Test
    fun saveFolder_cleanName() {
        val before = MZGramConfig.saveFolder
        try {
            assertEquals("Telegram", MZGramSaveFolder.clean(" Telegram "))
            assertEquals("Myfiles", MZGramSaveFolder.clean("../My/fi:les*"))
            assertEquals("", MZGramSaveFolder.clean(" / . "))
            assertEquals(MZGramSaveFolder.MAX_LENGTH, MZGramSaveFolder.clean("x".repeat(100)).length)
            MZGramConfig.setSaveFolder("MZGram")
            assertEquals("MZGram", MZGramSaveFolder.name())
            MZGramConfig.setSaveFolder("///")
            assertEquals("Telegram", MZGramSaveFolder.name())
        } finally {
            MZGramConfig.setSaveFolder(before)
        }
    }

    // Relative last seen: within a day "last seen ... ago" in Telegram's
    // words, older or future times stay Telegram's
    @Test
    fun relativeLastSeen_withinADay() {
        val now = 2_000_000_000L
        val seen = { s: String -> LocaleController.formatString(R.string.LastSeenDateFormatted, s) }
        assertEquals(seen(LocaleController.getString(R.string.LessMinuteAgo)), MZGramRelativeLastSeen.format(now - 30, now))
        assertEquals(seen(LocaleController.getString(R.string.MinuteAgo)), MZGramRelativeLastSeen.format(now - 90, now))
        assertEquals(seen(LocaleController.formatPluralString("MinutesAgo", 5)), MZGramRelativeLastSeen.format(now - 5 * 60, now))
        assertEquals(seen(LocaleController.getString(R.string.HourAgo)), MZGramRelativeLastSeen.format(now - 61 * 60, now))
        assertEquals(seen(LocaleController.formatPluralString("HoursAgo", 23)), MZGramRelativeLastSeen.format(now - 23 * 3600 - 59, now))
        assertEquals(null, MZGramRelativeLastSeen.format(now - MZGramRelativeLastSeen.DAY, now))
        assertEquals(null, MZGramRelativeLastSeen.format(now + 10, now))
        val before = MZGramConfig.relativeLastSeen
        try {
            if (MZGramConfig.relativeLastSeen) MZGramConfig.toggleRelativeLastSeen()
            assertEquals("off: Telegram's", null, MZGramRelativeLastSeen.format(System.currentTimeMillis() / 1000 - 60))
            MZGramConfig.toggleRelativeLastSeen()
            assertEquals(seen(LocaleController.getString(R.string.MinuteAgo)), LocaleController.formatDateOnline(System.currentTimeMillis() / 1000 - 70, null))
        } finally {
            if (MZGramConfig.relativeLastSeen != before) MZGramConfig.toggleRelativeLastSeen()
        }
    }

    // Download boost keeps to its three levels, whatever is set
    @Test
    fun downloadBoost_keepsToItsLevels() {
        val before = MZGramConfig.downloadSpeedBoost
        try {
            MZGramConfig.setDownloadSpeedBoost(5)
            assertEquals(2, MZGramConfig.downloadSpeedBoost)
            MZGramConfig.setDownloadSpeedBoost(-1)
            assertEquals(0, MZGramConfig.downloadSpeedBoost)
            MZGramConfig.setDownloadSpeedBoost(1)
            assertEquals(1, MZGramConfig.downloadSpeedBoost)
        } finally {
            MZGramConfig.setDownloadSpeedBoost(before)
        }
    }

    // In-app vibration: on by default; off, the views of a followed window
    // lose haptic feedback and Telegram's "regardless of the view" flag is
    // dropped, so they stay quiet; back on, all of it returns.
    @Test
    fun inAppVibration_offSilencesFollowedViews() {
        if (!MZGramConfig.inAppVibration) {
            MZGramConfig.toggleInAppVibration()
        }
        assertEquals(HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING, MZGramHaptics.viewSetting())
        lateinit var root: FrameLayout
        lateinit var child: View
        instrumentation.runOnMainSync {
            root = FrameLayout(context)
            val group = LinearLayout(context)
            child = View(context)
            group.addView(child)
            root.addView(group)
            MZGramHaptics.follow(root)
            assertTrue("untouched while on", child.isHapticFeedbackEnabled)
            MZGramConfig.toggleInAppVibration()
            MZGramHaptics.apply()
        }
        assertFalse(MZGramConfig.inAppVibration)
        assertEquals("the flag is dropped", 0, MZGramHaptics.viewSetting())
        assertFalse("child off", child.isHapticFeedbackEnabled)
        assertFalse("root off", root.isHapticFeedbackEnabled)
        instrumentation.runOnMainSync {
            MZGramConfig.toggleInAppVibration()
            MZGramHaptics.apply()
        }
        assertTrue("back on", child.isHapticFeedbackEnabled)
    }
}
