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
import org.telegram.messenger.mzgram.MZGramHidePhone
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

    // Hide phone number: only the own number, only while on
    @Test
    fun hidePhoneNumber_onlyTheOwn() {
        val self = org.telegram.tgnet.TLRPC.TL_user().apply { self = true; phone = "380000000000" }
        val other = org.telegram.tgnet.TLRPC.TL_user().apply { phone = "380000000001" }
        val before = MZGramConfig.hidePhoneNumber
        try {
            if (MZGramConfig.hidePhoneNumber) MZGramConfig.toggleHidePhoneNumber()
            assertFalse(MZGramHidePhone.hides(self, true))
            MZGramConfig.toggleHidePhoneNumber()
            assertTrue(MZGramHidePhone.hides(self, false))
            assertTrue(MZGramHidePhone.hides(other, true))
            assertFalse("someone else's number", MZGramHidePhone.hides(other, false))
        } finally {
            if (MZGramConfig.hidePhoneNumber != before) MZGramConfig.toggleHidePhoneNumber()
        }
    }

    // Hide archive from the chat list: the archive row leaves the main list
    // of every account and nothing counts as a hidden archive; back off, it
    // returns where the archive has chats
    @Test
    fun hideArchiveFolder_rowLeavesTheList() {
        val before = MZGramConfig.hideArchiveFolder
        val controller = org.telegram.messenger.MessagesController.getInstance(0)
        try {
            instrumentation.runOnMainSync {
                if (!MZGramConfig.hideArchiveFolder) MZGramConfig.toggleHideArchiveFolder()
                org.telegram.messenger.mzgram.MZGramArchiveRow.apply()
            }
            assertTrue("no archive row", controller.getDialogs(0).none { it is org.telegram.tgnet.TLRPC.TL_dialogFolder })
            assertFalse("no hidden archive to pull out", controller.hasHiddenArchive())
        } finally {
            instrumentation.runOnMainSync {
                if (MZGramConfig.hideArchiveFolder != before) MZGramConfig.toggleHideArchiveFolder()
                org.telegram.messenger.mzgram.MZGramArchiveRow.apply()
            }
        }
    }

    // Header text: the logo by default (no text), MZGram, "Chats", a text of
    // one's own (cut to 32 characters; an empty one falls back to MZGram)
    @Test
    fun headerText_modes() {
        val mode = MZGramConfig.titleText
        val custom = MZGramConfig.titleTextCustom
        try {
            MZGramConfig.setTitleText(org.telegram.ui.mzgram.MZGramTitleText.LOGO)
            assertEquals(null, org.telegram.ui.mzgram.MZGramTitleText.text(0))
            assertEquals(null, org.telegram.ui.mzgram.MZGramTitleText.drawable(0))
            MZGramConfig.setTitleText(org.telegram.ui.mzgram.MZGramTitleText.APP_NAME)
            assertEquals("MZGram", org.telegram.ui.mzgram.MZGramTitleText.text(0))
            assertTrue(org.telegram.ui.mzgram.MZGramTitleText.drawable(0)!!.intrinsicWidth > 0)
            MZGramConfig.setTitleText(org.telegram.ui.mzgram.MZGramTitleText.CHATS)
            assertEquals(LocaleController.getString(R.string.FilterChats), org.telegram.ui.mzgram.MZGramTitleText.text(0))
            MZGramConfig.setTitleText(org.telegram.ui.mzgram.MZGramTitleText.CUSTOM)
            MZGramConfig.setTitleTextCustom("x".repeat(40))
            assertEquals(32, org.telegram.ui.mzgram.MZGramTitleText.text(0)!!.length)
            MZGramConfig.setTitleTextCustom("  ")
            assertEquals("MZGram", org.telegram.ui.mzgram.MZGramTitleText.text(0))
            MZGramConfig.setTitleText(9)
            assertEquals(org.telegram.ui.mzgram.MZGramTitleText.CUSTOM, MZGramConfig.titleText)
        } finally {
            MZGramConfig.setTitleText(mode)
            MZGramConfig.setTitleTextCustom(custom)
        }
    }

    // Single corner radius: a forum's rounded square counts as a circle, so
    // forums get the shape of the chats; off, Telegram's radius is kept
    @Test
    fun singleCornerRadius_forumsLikeChats() {
        val single = MZGramConfig.singleCornerRadius
        val corners = MZGramConfig.avatarCorners
        try {
            MZGramConfig.setAvatarCorners(org.telegram.messenger.mzgram.MZGramAvatarShape.MAX)
            if (MZGramConfig.singleCornerRadius) MZGramConfig.toggleSingleCornerRadius()
            val forum = intArrayOf(16, 16, 16, 16)
            assertTrue("off: Telegram's", org.telegram.messenger.mzgram.MZGramAvatarShape.apply(forum, 56f, IntArray(4)) === forum)
            MZGramConfig.toggleSingleCornerRadius()
            assertEquals(28, org.telegram.messenger.mzgram.MZGramAvatarShape.apply(forum, 56f, IntArray(4))[0])
            assertEquals(28, org.telegram.messenger.mzgram.MZGramAvatarShape.forumRadius(16, 28, 0f))
        } finally {
            if (MZGramConfig.singleCornerRadius != single) MZGramConfig.toggleSingleCornerRadius()
            MZGramConfig.setAvatarCorners(corners)
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

    // Sticker shape: Default leaves stickers square, Rounded gives a small
    // radius, Message lets the bubble corners apply; anything else reads as
    // Default. Unlimited recent stickers raises the limit to 200 and never
    // lowers a bigger limit of Telegram.
    @Test
    fun stickerShapeAndRecentStickersLimit() {
        val shape = MZGramConfig.stickerShape
        val unlimited = MZGramConfig.unlimitedRecentStickers
        try {
            MZGramConfig.setStickerShape(MZGramConfig.STICKER_SHAPE_DEFAULT)
            assertEquals(0, org.telegram.ui.mzgram.MZGramStickers.roundRadius())
            assertFalse(org.telegram.ui.mzgram.MZGramStickers.messageShape())
            MZGramConfig.setStickerShape(MZGramConfig.STICKER_SHAPE_ROUNDED)
            assertTrue(org.telegram.ui.mzgram.MZGramStickers.roundRadius() > 0)
            MZGramConfig.setStickerShape(MZGramConfig.STICKER_SHAPE_MESSAGE)
            assertTrue(org.telegram.ui.mzgram.MZGramStickers.messageShape())
            assertEquals(0, org.telegram.ui.mzgram.MZGramStickers.roundRadius())
            MZGramConfig.setStickerShape(7)
            assertEquals(MZGramConfig.STICKER_SHAPE_DEFAULT, MZGramConfig.stickerShape)

            if (MZGramConfig.unlimitedRecentStickers) {
                MZGramConfig.toggleUnlimitedRecentStickers()
            }
            assertEquals(30, MZGramConfig.recentStickersLimit(30))
            MZGramConfig.toggleUnlimitedRecentStickers()
            assertEquals(MZGramConfig.UNLIMITED_RECENT_STICKERS, MZGramConfig.recentStickersLimit(30))
            assertEquals(500, MZGramConfig.recentStickersLimit(500))
        } finally {
            MZGramConfig.setStickerShape(shape)
            if (MZGramConfig.unlimitedRecentStickers != unlimited) {
                MZGramConfig.toggleUnlimitedRecentStickers()
            }
        }
    }

    // Bottom button and swipe action: out-of-range values read as Telegram's
    // (Mute, Reply); one title per swipe action.
    @Test
    fun channelBottomButtonAndSwipeAction() {
        val bottom = MZGramConfig.channelBottomButton
        val swipe = MZGramConfig.swipeAction
        try {
            MZGramConfig.setChannelBottomButton(MZGramConfig.CHANNEL_BOTTOM_DISCUSS)
            assertEquals(MZGramConfig.CHANNEL_BOTTOM_DISCUSS, MZGramConfig.channelBottomButton)
            MZGramConfig.setChannelBottomButton(9)
            assertEquals(MZGramConfig.CHANNEL_BOTTOM_MUTE, MZGramConfig.channelBottomButton)
            MZGramConfig.setSwipeAction(org.telegram.ui.mzgram.MZGramSwipeAction.DELETE)
            assertEquals(org.telegram.ui.mzgram.MZGramSwipeAction.DELETE, MZGramConfig.swipeAction)
            MZGramConfig.setSwipeAction(-1)
            assertEquals(org.telegram.ui.mzgram.MZGramSwipeAction.REPLY, MZGramConfig.swipeAction)
            assertEquals(6, org.telegram.ui.mzgram.MZGramSwipeAction.TITLES.size)
        } finally {
            MZGramConfig.setChannelBottomButton(bottom)
            MZGramConfig.setSwipeAction(swipe)
        }
    }
}
