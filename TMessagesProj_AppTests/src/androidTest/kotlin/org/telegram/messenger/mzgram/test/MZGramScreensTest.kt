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
import org.telegram.messenger.mzgram.MZGramHaptics

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
