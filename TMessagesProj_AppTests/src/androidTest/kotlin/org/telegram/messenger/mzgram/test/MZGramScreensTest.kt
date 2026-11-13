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

    // Edited icon: the pencil mark in the time gets an icon span; a time
    // without the mark stays as it was. Online dot: off, or no user, gives
    // no colour.
    @Test
    fun editedIconAndOnlineDot() {
        val time = org.telegram.ui.mzgram.MZGramEditedIcon.MARK + " 12:58"
        val drawn = org.telegram.ui.mzgram.MZGramEditedIcon.apply(time)
        assertTrue(drawn is android.text.Spanned)
        val spans = (drawn as android.text.Spanned).getSpans(0, drawn.length, org.telegram.ui.Components.ColoredImageSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(time, drawn.toString())
        assertEquals("12:58", org.telegram.ui.mzgram.MZGramEditedIcon.apply("12:58"))

        val online = MZGramConfig.showOnlineStatus
        try {
            if (MZGramConfig.showOnlineStatus) {
                MZGramConfig.toggleShowOnlineStatus()
            }
            val user = org.telegram.tgnet.TLRPC.TL_user()
            user.id = 1
            user.status = org.telegram.tgnet.TLRPC.TL_userStatusOnline()
            user.status.expires = Int.MAX_VALUE
            assertEquals(0, org.telegram.ui.mzgram.MZGramOnlineDot.color(0, user, null))
            MZGramConfig.toggleShowOnlineStatus()
            assertTrue(org.telegram.ui.mzgram.MZGramOnlineDot.color(0, user, null) != 0)
            assertEquals(0, org.telegram.ui.mzgram.MZGramOnlineDot.color(0, null, null))
        } finally {
            if (MZGramConfig.showOnlineStatus != online) {
                MZGramConfig.toggleShowOnlineStatus()
            }
        }
    }

    // Telegram AI features: shown by default; the Instant View summary block
    // is recognised by its caption and left out only when turned off. Quick
    // admin actions: only with the switch, in a chat where one is an admin.
    @Test
    fun telegramAiAndAdminShortcuts() {
        assertTrue(MZGramConfig.telegramAiEditor || org.telegram.messenger.mzgram.MZGramTelegramAi.editorHidden())
        val iv = MZGramConfig.telegramAiIvSummary
        val admin = MZGramConfig.quickAdminShortcuts
        try {
            val caption = org.telegram.tgnet.tl.TL_iv.textPlain()
            caption.text = "Cocoon AI Summary"
            val block = org.telegram.tgnet.tl.TL_iv.pageBlockBlockquote()
            block.caption = caption
            if (!MZGramConfig.telegramAiIvSummary) {
                MZGramConfig.toggleTelegramAiIvSummary()
            }
            assertFalse(org.telegram.messenger.mzgram.MZGramTelegramAi.hideIvBlock(block))
            MZGramConfig.toggleTelegramAiIvSummary()
            assertTrue(org.telegram.messenger.mzgram.MZGramTelegramAi.hideIvBlock(block))
            caption.text = "A quote"
            assertFalse(org.telegram.messenger.mzgram.MZGramTelegramAi.hideIvBlock(block))

            val chat = org.telegram.tgnet.TLRPC.TL_channel()
            chat.creator = true
            if (MZGramConfig.quickAdminShortcuts) {
                MZGramConfig.toggleQuickAdminShortcuts()
            }
            assertFalse(org.telegram.ui.mzgram.MZGramAdminShortcuts.isAvailable(chat, 0))
            MZGramConfig.toggleQuickAdminShortcuts()
            assertTrue(org.telegram.ui.mzgram.MZGramAdminShortcuts.isAvailable(chat, 0))
            chat.creator = false
            assertFalse(org.telegram.ui.mzgram.MZGramAdminShortcuts.isAvailable(chat, 0))
        } finally {
            if (MZGramConfig.telegramAiIvSummary != iv) {
                MZGramConfig.toggleTelegramAiIvSummary()
            }
            if (MZGramConfig.quickAdminShortcuts != admin) {
                MZGramConfig.toggleQuickAdminShortcuts()
            }
        }
    }

    // Units and time zones in the calculator: off gives nothing; on, units of
    // one kind convert (temperature with its offset), other kinds do not mix;
    // a time moves between zones with the day shown when it changes.
    @Test
    fun unitsAndTimeZones() {
        val units = MZGramConfig.inlineUnits
        val zones = MZGramConfig.inlineTimeZones
        try {
            if (MZGramConfig.inlineUnits) MZGramConfig.toggleInlineUnits()
            if (MZGramConfig.inlineTimeZones) MZGramConfig.toggleInlineTimeZones()
            assertEquals(null, org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("5 km in mi"))
            MZGramConfig.toggleInlineUnits()
            assertEquals("3.10686 mi", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("5 km in mi"))
            assertEquals("212 F", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("100 C to F"))
            assertEquals("1024 kb", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("1 mb in kb"))
            assertEquals("2000 г", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("2 кг в г"))
            assertEquals(null, org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("5 km in kg"))
            assertEquals(null, org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("15:00 UTC in UTC+3"))
            MZGramConfig.toggleInlineTimeZones()
            assertEquals("18:00", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("15:00 UTC in UTC+3"))
            assertEquals("01:30 (+1)", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("23:30 UTC to GMT+2"))
            assertEquals("21:00 (-1)", org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("02:00 UTC+5 in UTC"))
            assertEquals(null, org.telegram.ui.mzgram.MZGramUnitConvert.resultFor("15:00 Nowhere in UTC"))
        } finally {
            if (MZGramConfig.inlineUnits != units) MZGramConfig.toggleInlineUnits()
            if (MZGramConfig.inlineTimeZones != zones) MZGramConfig.toggleInlineTimeZones()
        }
    }

    // Camera in video messages: with Remember last camera off a switch while
    // recording changes nothing; on, it becomes the camera to start with.
    @Test
    fun rememberLastCamera() {
        val front = MZGramConfig.roundVideoFrontCamera
        val remember = MZGramConfig.rememberLastCamera
        try {
            MZGramConfig.setRoundVideoFrontCamera(true)
            if (MZGramConfig.rememberLastCamera) MZGramConfig.toggleRememberLastCamera()
            MZGramConfig.rememberRoundVideoCamera(false)
            assertTrue(MZGramConfig.roundVideoFrontCamera)
            MZGramConfig.toggleRememberLastCamera()
            MZGramConfig.rememberRoundVideoCamera(false)
            assertFalse(MZGramConfig.roundVideoFrontCamera)
        } finally {
            MZGramConfig.setRoundVideoFrontCamera(front)
            if (MZGramConfig.rememberLastCamera != remember) MZGramConfig.toggleRememberLastCamera()
        }
    }

    // Group message menu: the main actions go to the end in their order, the
    // three lists stay parallel; off, or only one part, leaves the menu as is.
    @Test
    fun groupMessageMenu() {
        val on = MZGramConfig.groupMessageMenu
        try {
            if (MZGramConfig.groupMessageMenu) MZGramConfig.toggleGroupMessageMenu()
            val options = arrayListOf(org.telegram.ui.ChatActivity.OPTION_REPLY, 104, org.telegram.ui.ChatActivity.OPTION_COPY, 29, org.telegram.ui.ChatActivity.OPTION_DELETE)
            val items = arrayListOf<CharSequence>("Reply", "Profile", "Copy", "Translate", "Delete")
            val icons = arrayListOf(1, 2, 3, 4, 5)
            assertEquals(-1, org.telegram.ui.mzgram.MZGramMenuGroup.apply(items, options, icons))
            MZGramConfig.toggleGroupMessageMenu()
            assertEquals(2, org.telegram.ui.mzgram.MZGramMenuGroup.apply(items, options, icons))
            assertEquals(listOf<CharSequence>("Profile", "Translate", "Reply", "Copy", "Delete"), items)
            assertEquals(listOf(2, 4, 1, 3, 5), icons)
            val onlyMain = arrayListOf(org.telegram.ui.ChatActivity.OPTION_REPLY)
            assertEquals(-1, org.telegram.ui.mzgram.MZGramMenuGroup.apply(arrayListOf<CharSequence>("Reply"), onlyMain, arrayListOf(1)))
        } finally {
            if (MZGramConfig.groupMessageMenu != on) MZGramConfig.toggleGroupMessageMenu()
        }
    }

    // Ghost mode parts: each part works only with ghost mode on; Send without
    // sound maps to the two switches; the header ghost only while shown.
    @Test
    fun ghostModeParts() {
        val ghost = MZGramConfig.ghostMode
        val noRead = MZGramConfig.ghostNoRead
        val noOnline = MZGramConfig.ghostNoOnline
        val inTitle = MZGramConfig.ghostStatusInTitle
        val silent = org.telegram.messenger.mzgram.MZGramGhostMode.silentSend()
        val gm = org.telegram.messenger.mzgram.MZGramGhostMode::class.java
        try {
            if (MZGramConfig.ghostMode) MZGramConfig.toggleGhostMode()
            if (!MZGramConfig.ghostNoRead) MZGramConfig.toggleGhostNoRead()
            assertFalse(org.telegram.messenger.mzgram.MZGramGhostMode.blocksRead(0, 0))
            MZGramConfig.toggleGhostMode()
            assertTrue(org.telegram.messenger.mzgram.MZGramGhostMode.blocksRead(0, 0))
            MZGramConfig.toggleGhostNoRead()
            assertFalse(org.telegram.messenger.mzgram.MZGramGhostMode.blocksRead(0, 0))
            if (!MZGramConfig.ghostNoOnline) MZGramConfig.toggleGhostNoOnline()
            assertTrue(org.telegram.messenger.mzgram.MZGramGhostMode.blocksOnline())
            assertTrue(org.telegram.messenger.mzgram.MZGramGhostMode.partsOn() in 0..org.telegram.messenger.mzgram.MZGramGhostMode.PARTS)

            org.telegram.messenger.mzgram.MZGramGhostMode.setSilentSend(org.telegram.messenger.mzgram.MZGramGhostMode.SILENT_IN_GHOST_MODE)
            assertTrue(MZGramConfig.ghostSilentSend)
            assertFalse(MZGramConfig.sendSilentByDefault)
            org.telegram.messenger.mzgram.MZGramGhostMode.setSilentSend(org.telegram.messenger.mzgram.MZGramGhostMode.SILENT_ALWAYS)
            assertFalse(MZGramConfig.ghostSilentSend)
            assertTrue(MZGramConfig.sendSilentByDefault)
            assertEquals(org.telegram.messenger.mzgram.MZGramGhostMode.SILENT_ALWAYS, org.telegram.messenger.mzgram.MZGramGhostMode.silentSend())

            if (MZGramConfig.ghostStatusInTitle) MZGramConfig.toggleGhostStatusInTitle()
            assertEquals("Title", org.telegram.ui.mzgram.MZGramGhostTitle.title("Title", 0))
            MZGramConfig.toggleGhostStatusInTitle()
            assertTrue(org.telegram.ui.mzgram.MZGramGhostTitle.title("Title", 0) is android.text.Spanned)
            assertTrue(gm.name.isNotEmpty())
        } finally {
            if (MZGramConfig.ghostMode != ghost) MZGramConfig.toggleGhostMode()
            if (MZGramConfig.ghostNoRead != noRead) MZGramConfig.toggleGhostNoRead()
            if (MZGramConfig.ghostNoOnline != noOnline) MZGramConfig.toggleGhostNoOnline()
            if (MZGramConfig.ghostStatusInTitle != inTitle) MZGramConfig.toggleGhostStatusInTitle()
            org.telegram.messenger.mzgram.MZGramGhostMode.setSilentSend(silent)
        }
    }

    // Size of the archive's files: no limit lets everything in; with a limit
    // a file that would pass it is refused and an allowed one is counted.
    // The deleted mark becomes an icon span.
    @Test
    fun archiveCapAndDeletedIcon() {
        val limit = MZGramConfig.archiveTotalLimitMb
        try {
            org.telegram.messenger.mzgram.MZGramArchiveCap.setStep(org.telegram.messenger.mzgram.MZGramArchiveCap.STEPS_MB.size - 1)
            assertEquals(-1, MZGramConfig.archiveTotalLimitMb)
            assertTrue(org.telegram.messenger.mzgram.MZGramArchiveCap.allows(Long.MAX_VALUE / 4))
            org.telegram.messenger.mzgram.MZGramArchiveCap.setStep(0)
            assertEquals(300, MZGramConfig.archiveTotalLimitMb)
            org.telegram.messenger.mzgram.MZGramArchiveCap.setKnownBytes(290L * 1024 * 1024)
            assertTrue(org.telegram.messenger.mzgram.MZGramArchiveCap.allows(5L * 1024 * 1024))
            assertFalse(org.telegram.messenger.mzgram.MZGramArchiveCap.allows(6L * 1024 * 1024))
            assertEquals(0, org.telegram.messenger.mzgram.MZGramArchiveCap.step())
        } finally {
            MZGramConfig.setArchiveTotalLimitMb(limit)
            org.telegram.messenger.mzgram.MZGramArchiveCap.reset()
        }
        val time = org.telegram.ui.mzgram.MZGramEditedIcon.DELETED + " 12:58"
        val drawn = org.telegram.ui.mzgram.MZGramEditedIcon.apply(time) as android.text.Spanned
        assertEquals(1, drawn.getSpans(0, drawn.length, org.telegram.ui.Components.ColoredImageSpan::class.java).size)
    }

    // Reset settings: a changed setting goes back to its default; a key that
    // is not a setting stays in the same file.
    @Test
    fun resetSettingsKeepsWhatIsNotASetting() {
        val prefs = context.getSharedPreferences("mzgram_config", android.content.Context.MODE_PRIVATE)
        val backup = org.telegram.messenger.mzgram.MZGramSettingsBackup.current()
        try {
            if (!MZGramConfig.hideStickerTime) MZGramConfig.toggleHideStickerTime()
            prefs.edit().putString("mzgramTestNotASetting", "kept").commit()
            assertTrue(org.telegram.messenger.mzgram.MZGramSettingsBackup.reset() > 0)
            assertFalse(MZGramConfig.hideStickerTime)
            assertEquals("kept", prefs.getString("mzgramTestNotASetting", null))
        } finally {
            prefs.edit().remove("mzgramTestNotASetting").commit()
            org.telegram.messenger.mzgram.MZGramSettingsBackup.restore(backup)
        }
    }

    // Feed: unchecked channels and the category are kept per account; posts
    // per channel reads 20 for anything but 10, 20 or 50; a feed with no
    // channels calls back at once with nothing.
    @Test
    fun feedPreferences() {
        val account = org.telegram.messenger.UserConfig.selectedAccount
        val posts = MZGramConfig.feedPostsPerChannel
        val category = org.telegram.messenger.mzgram.MZGramFeed.category(account)
        try {
            org.telegram.messenger.mzgram.MZGramFeed.setHidden(account, -1001234L, true)
            assertTrue(org.telegram.messenger.mzgram.MZGramFeed.hidden(account).contains(-1001234L))
            org.telegram.messenger.mzgram.MZGramFeed.setHidden(account, -1001234L, false)
            assertFalse(org.telegram.messenger.mzgram.MZGramFeed.hidden(account).contains(-1001234L))
            org.telegram.messenger.mzgram.MZGramFeed.setCategory(account, 7)
            assertEquals(7, org.telegram.messenger.mzgram.MZGramFeed.category(account))
            MZGramConfig.setFeedPostsPerChannel(50)
            assertEquals(50, org.telegram.messenger.mzgram.MZGramFeed.postsPerChannel())
            MZGramConfig.setFeedPostsPerChannel(33)
            assertEquals(20, org.telegram.messenger.mzgram.MZGramFeed.postsPerChannel())
        } finally {
            MZGramConfig.setFeedPostsPerChannel(posts)
            org.telegram.messenger.mzgram.MZGramFeed.setCategory(account, category)
        }
    }

    // Divider style: Segments is kept, anything unknown reads as Line; Segments and
    // None hide the lines; Segments always separates the section titles.
    @Test
    fun segmentsAndSeparateHeaders() {
        val style = MZGramConfig.dividerStyle
        val separate = MZGramConfig.separateHeaders
        try {
            if (MZGramConfig.separateHeaders) MZGramConfig.toggleSeparateHeaders()
            MZGramConfig.setDividerStyle(MZGramConfig.DIVIDER_SEGMENTS)
            assertEquals(MZGramConfig.DIVIDER_SEGMENTS, MZGramConfig.dividerStyle)
            assertTrue(MZGramConfig.hideDividers())
            assertTrue(MZGramConfig.separatedHeaders())
            MZGramConfig.setDividerStyle(MZGramConfig.DIVIDER_HIDDEN)
            assertTrue(MZGramConfig.hideDividers())
            assertFalse(MZGramConfig.separatedHeaders())
            MZGramConfig.setDividerStyle(7)
            assertEquals(MZGramConfig.DIVIDER_LINE, MZGramConfig.dividerStyle)
            assertFalse(MZGramConfig.hideDividers())
            MZGramConfig.toggleSeparateHeaders()
            assertTrue(MZGramConfig.separatedHeaders())
            assertTrue(org.telegram.ui.Components.UniversalAdapter.isHeader(org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_HEADER))
            assertFalse(org.telegram.ui.Components.UniversalAdapter.isHeader(org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_CHECK))
        } finally {
            if (MZGramConfig.separateHeaders != separate) MZGramConfig.toggleSeparateHeaders()
            MZGramConfig.setDividerStyle(style)
        }
    }
}
