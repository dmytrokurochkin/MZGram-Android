"""Features of the reference screenshots (2026-11-13): their switches and
the places in Telegram's code that follow them."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
CONFIG = JAVA / "messenger/mzgram/MZGramConfig.java"
PAGE = JAVA / "ui/mzgram/MZGramSettingsActivity.java"
BACKUP = JAVA / "messenger/mzgram/MZGramSettingsBackup.java"


def read(path):
    return path.read_text(encoding="utf-8")


class ScreensTest(unittest.TestCase):
    def switch(self, name, default, button):
        """A switch: its default, loading, backup key, row and click."""
        config = read(CONFIG)
        self.assertIn(f"public static boolean {name} = {default};", config)
        self.assertIn(f'{name} = preferences.getBoolean("{name}", {default});', config)
        self.assertIn(f'"{name}"', read(BACKUP))
        page = read(PAGE)
        self.assertRegex(page, rf"private static final int {button} = \d+;")
        self.assertTrue(f"items.add(check({button}, " in page or f"items.add(UItem.asCheck({button}, " in page, button)
        self.assertIn(f"item.id == {button})", page)

    def test_in_app_vibration(self):
        self.switch("inAppVibration", "true", "BUTTON_IN_APP_VIBRATION")
        self.assertIn("MZGramHaptics.follow(getWindow().getDecorView());", read(JAVA / "ui/LaunchActivity.java"))
        self.assertIn("MZGramHaptics.follow(container);", read(JAVA / "ui/ActionBar/BottomSheet.java"))
        self.assertIn("MZGramHaptics.follow(rootView);", read(JAVA / "ui/ActionBar/AlertDialog.java"))
        self.assertIn("MZGramHaptics.follow(windowView);", read(JAVA / "ui/PhotoViewer.java"))
        effect = read(JAVA / "messenger/BotWebViewVibrationEffect.java")
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramHaptics.allowed()) {", effect)
        # Every "regardless of the view" tap asks MZGramHaptics
        for path in JAVA.rglob("*.java"):
            if path.name == "MZGramHaptics.java":
                continue
            self.assertNotIn("FLAG_IGNORE_VIEW_SETTING", read(path), path.name)

    def test_disable_notification_delay(self):
        self.switch("disableNotificationDelay", "false", "BUTTON_DISABLE_NOTIFICATION_DELAY")
        notifications = read(JAVA / "messenger/NotificationsController.java")
        self.assertIn("MZGramConfig.disableNotificationDelay ? 0 : (onlineReason ? 3 * 1000 : 1000)", notifications)

    def test_download_and_upload_boost(self):
        self.switch("uploadSpeedBoost", "false", "BUTTON_UPLOAD_SPEED_BOOST")
        config = read(CONFIG)
        self.assertIn("public static int downloadSpeedBoost = 0;", config)
        self.assertIn('integer(CONFIG, "downloadSpeedBoost", 0, 2);', read(BACKUP))
        self.assertIn("MZGramConfig.downloadSpeedBoost, MZGramConfig::setDownloadSpeedBoost", read(PAGE))
        load = read(JAVA / "messenger/FileLoadOperation.java")
        params = load[load.index("private void updateParams() {"):]
        params = params[:params.index("maxCdnParts =")]
        self.assertIn("if (boost == 2) {\n            downloadChunkSizeBig = 1024 * 1024;\n            maxDownloadRequests = 12;", params.replace("\r\n", "\n"))
        self.assertIn("getfileExperimentalParams || boost == 1) && !forceSmallChunk", params)
        upload = read(JAVA / "messenger/FileUploadOperation.java")
        self.assertIn("MZGramConfig.uploadSpeedBoost ? 512 : minUploadChunkSize", upload)

    def test_save_folder(self):
        config = read(CONFIG)
        self.assertIn('public static String saveFolder = "Telegram";', config)
        self.assertIn('"saveFolder"', read(BACKUP))
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_SAVE_FOLDER, ", page)
        self.assertIn("item.id == BUTTON_SAVE_FOLDER)", page)
        media = read(JAVA / "messenger/MediaController.java")
        # Every folder Telegram saves into takes the chosen name
        self.assertNotIn('"Telegram")', media)
        self.assertEqual(11, media.count("MZGramSaveFolder.name())"))

    def test_relative_last_seen(self):
        self.switch("relativeLastSeen", "false", "BUTTON_RELATIVE_LAST_SEEN")
        locale = read(JAVA / "messenger/LocaleController.java")
        start = locale.index("public static String formatDateOnline(long date, boolean[] madeShorter) {")
        self.assertIn("MZGramRelativeLastSeen.format(date);", locale[start:start + 600])
        header = read(JAVA / "ui/Components/ChatAvatarContainer.java")
        self.assertIn("AndroidUtilities.runOnUIThread(mzgramRelativeRefresh, 60_000);", header)
        self.assertIn("AndroidUtilities.cancelRunOnUIThread(mzgramRelativeRefresh);", header)

    def test_hide_phone_number(self):
        self.switch("hidePhoneNumber", "false", "BUTTON_HIDE_PHONE_NUMBER")
        settings = read(JAVA / "ui/SettingsActivity.java")
        self.assertIn("if (user != null && !mzgramHidePhone) {", settings)
        profile = read(JAVA / "ui/ProfileActivity.java")
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramHidePhone.hides(user, myProfile)) {", profile)
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramHidePhone.hidesOwn()) {", profile)

    def test_archive_row_and_swipe(self):
        self.switch("hideArchiveFolder", "false", "BUTTON_HIDE_ARCHIVE_FOLDER")
        self.switch("disableUnarchiveSwipe", "false", "BUTTON_DISABLE_UNARCHIVE_SWIPE")
        controller = read(JAVA / "messenger/MessagesController.java")
        add = controller[controller.index("private void addDialogToItsFolder(int index, TLRPC.Dialog dialog) {"):]
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.hideArchiveFolder) {\n                return;", add[:600].replace("\r\n", "\n"))
        hidden = controller[controller.index("public boolean hasHiddenArchive() {"):]
        self.assertIn("MZGramConfig.hideArchiveFolder", hidden[:300])
        dialogs = read(JAVA / "ui/DialogsActivity.java")
        self.assertIn("if (folderId != 0 && org.telegram.messenger.mzgram.MZGramConfig.disableUnarchiveSwipe) {", dialogs)
        menu = dialogs[dialogs.index("getString(R.string.SavedMessages), () -> {"):]
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.hideArchiveFolder) {", menu[:800])
        self.assertIn('args.putInt("folderId", 1);', menu[:800])

    def test_chat_list_title_buttons_and_search(self):
        for name, button in (("centerTitle", "BUTTON_CENTER_TITLE"), ("hideActionBarStatus", "BUTTON_HIDE_ACTION_BAR_STATUS"),
                             ("hideFloatingButton", "BUTTON_HIDE_FLOATING_BUTTON"), ("hideDialogsSearchBar", "BUTTON_HIDE_DIALOGS_SEARCH_BAR")):
            self.switch(name, "false", button)
        bar = read(JAVA / "ui/ActionBar/ActionBar.java")
        self.assertIn("return isCenterTitle || org.telegram.messenger.mzgram.MZGramConfig.centerTitle;", bar)
        self.assertIn("titleTextView[i].setGravity(mzgramCentered() ?", bar)
        self.assertIn("final int titleLeft = mzgramCentered() ? (getMeasuredWidth() - titleTextView[i].getMeasuredWidth()) / 2 : textLeft;", bar)
        dialogs = read(JAVA / "ui/DialogsActivity.java")
        self.assertIn("|| org.telegram.messenger.mzgram.MZGramConfig.hideFloatingButton && !onlySelect);", dialogs)
        self.assertEqual(2, dialogs.count("if (org.telegram.messenger.mzgram.MZGramConfig.hideActionBarStatus) {") + read(JAVA / "ui/Stories/DialogStoriesCell.java").count("if (org.telegram.messenger.mzgram.MZGramConfig.hideActionBarStatus) {"))
        # The idle search field takes no room while hidden; search mode keeps its full height
        self.assertEqual(20, dialogs.count("mzgramIdleSearchFieldHeight()") - 2)
        self.assertIn("return -lerp(dp(4), dp(SEARCH_FIELD_HEIGHT), animatorSearchVisible.getFloatValue());", dialogs)
        self.assertIn("mzgramShowsIdleSearchField() ? 1f - MathUtils.clamp(", dialogs)

    def test_force_snow(self):
        self.switch("forceSnow", "false", "BUTTON_FORCE_SNOW")
        theme = read(JAVA / "ui/ActionBar/Theme.java")
        self.assertIn("return canStartHolidayAnimation || org.telegram.messenger.mzgram.MZGramConfig.forceSnow;", theme)

    def test_sender_mini_avatars(self):
        self.switch("senderMiniAvatars", "false", "BUTTON_SENDER_MINI_AVATARS")
        cell = read(JAVA / "ui/Cells/DialogCell.java")
        self.assertEqual(2, cell.count("mzgramAddSenderAvatar(") - 1)
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramConfig.senderMiniAvatars || messageObject == null", cell)
        self.assertIn("fromId == UserConfig.getInstance(currentAccount).getClientUserId()", cell)

    def test_header_text(self):
        config = read(CONFIG)
        self.assertIn("public static int titleText = 0;", config)
        self.assertIn('integer(CONFIG, "titleText", 0, 5);', read(BACKUP))
        self.assertIn('"titleTextCustom");', read(BACKUP))
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_TITLE_TEXT, ", page)
        self.assertIn("item.id == BUTTON_TITLE_TEXT)", page)
        self.assertIn("org.telegram.ui.mzgram.MZGramTitleText.drawable(currentAccount);", read(JAVA / "ui/DialogsActivity.java"))
        self.assertIn("org.telegram.ui.mzgram.MZGramTitleText.drawable(currentAccount);", read(JAVA / "ui/Stories/DialogStoriesCell.java"))

    def test_custom_chat_themes(self):
        self.switch("customChatThemes", "true", "BUTTON_CUSTOM_CHAT_THEMES")
        controller = read(JAVA / "messenger/ChatThemeController.java")
        start = controller.index("public TLRPC.WallPaper getDialogWallpaper(long dialogId) {")
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramConfig.customChatThemes) {", controller[start:start + 400])
        chat = read(JAVA / "ui/ChatActivity.java")
        start = chat.index("private void setupChatTheme(EmojiThemes chatTheme, TLRPC.WallPaper wallPaper, boolean withAnimation, boolean createNewResources) {")
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramConfig.customChatThemes) return;", chat[start:start + 500])

    def test_single_corner_radius(self):
        config = read(CONFIG)
        self.assertIn("public static boolean singleCornerRadius = false;", config)
        toggle = config[config.index("public static void toggleSingleCornerRadius()"):]
        self.assertIn("MZGramAvatarShape.changed();", toggle[:300])
        shape = read(JAVA / "messenger/mzgram/MZGramAvatarShape.java")
        self.assertIn("int r = single && asked[a] > 0 ? half : Math.min(asked[a], half);", shape)
        self.assertIn("MZGramAvatarShape.forumRadius(dp(needInsetForStories() ? 24 : 38), dp(50), mzgramShapeCircle);", read(JAVA / "ui/ProfileActivity.java"))
        self.assertIn("items.add(UItem.asCheck(BUTTON_SINGLE_CORNER_RADIUS, ", read(PAGE))

    def test_folder_tabs_counter_and_all_chats(self):
        self.switch("folderTabCounters", "true", "BUTTON_FOLDER_TAB_COUNTERS")
        self.switch("hideAllChatsTab", "false", "BUTTON_HIDE_ALL_CHATS_TAB")
        dialogs = read(JAVA / "ui/DialogsActivity.java")
        self.assertIn("|| !org.telegram.messenger.mzgram.MZGramConfig.folderTabCounters) {", dialogs)
        self.assertIn("final boolean mzgramHideAll = org.telegram.messenger.mzgram.MZGramConfig.hideAllChatsTab && filters.size() > 1;", dialogs)
        self.assertIn("if (mzgramHideAll && filters.get(a).isDefault()) {", dialogs)
        self.assertIn("final int mzgramDefaultTab = filterTabsView.getDefaultTabId() >= 0 ? filterTabsView.getDefaultTabId() : 0;", dialogs)
        tabs = read(JAVA / "ui/Components/FilterTabsView.java")
        flags = tabs[tabs.index("public int getMovementFlags("):]
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.hideAllChatsTab) {\n                return makeMovementFlags(0, 0);", flags[:500].replace("\r\n", "\n"))

    def test_sections_and_dividers(self):
        config = read(CONFIG)
        self.assertIn("public static int sectionRadius = 16;", config)
        self.assertIn("public static int dividerStyle = DIVIDER_LINE;", config)
        backup = read(BACKUP)
        self.assertIn('integer(CONFIG, "sectionRadius", 0, 28);', backup)
        self.assertIn('integer(CONFIG, "dividerStyle", 0, 2);', backup)
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_DIVIDER_STYLE, ", page)
        self.assertIn("MZGramConfig.setSectionRadius(val);", page)
        rlv = read(JAVA / "ui/Components/RecyclerListView.java")
        self.assertEqual(2, rlv.count("setSections(dp(12), dp(org.telegram.messenger.mzgram.MZGramConfig.sectionRadius), "))
        theme = read(JAVA / "ui/ActionBar/Theme.java")
        self.assertEqual(2, theme.count("if (key == key_divider && org.telegram.messenger.mzgram.MZGramConfig.hideDividers()) {"))
        # UniversalFragment screens (MZGram's own settings among them) take the radius too
        urv = read(JAVA / "ui/Components/UniversalRecyclerView.java")
        self.assertEqual(2, urv.count("setSections(dp(12), dp(org.telegram.messenger.mzgram.MZGramConfig.sectionRadius), "))
        self.assertNotIn("dp(16)", urv)

    def test_segments_and_separate_headers(self):
        config = read(CONFIG)
        self.assertIn("public static final int DIVIDER_SEGMENTS = 2;", config)
        self.assertIn("return value == DIVIDER_HIDDEN || value == DIVIDER_SEGMENTS ? value : DIVIDER_LINE;", config)
        self.assertIn("return dividerStyle != DIVIDER_LINE;", config)
        self.assertIn("return separateHeaders || dividerStyle == DIVIDER_SEGMENTS;", config)
        self.switch("separateHeaders", "false", "BUTTON_SEPARATE_HEADERS")
        page = read(PAGE)
        self.assertIn("R.string.MZGramDividerLine, R.string.MZGramDividerHidden, R.string.MZGramDividerSegments", page)
        self.assertIn(".setEnabled(MZGramConfig.dividerStyle != MZGramConfig.DIVIDER_SEGMENTS)", page)
        rlv = read(JAVA / "ui/Components/RecyclerListView.java")
        # Segments: a gap between rows, one card per row, every row clipped to its card
        self.assertIn("outRect.top += dp(2);", rlv)
        self.assertIn("drawSegmentedSectionsBackgrounds(canvas);", rlv)
        self.assertIn("clipChildWithSegmentedSection(canvas, child);", rlv)
        self.assertIn("return Math.min(sectionRadius, dp(4));", rlv)
        # Separate headers: out of the card, no background, a little inside the cards
        self.assertIn("&& !(view instanceof org.telegram.ui.Cells.HeaderCell && org.telegram.messenger.mzgram.MZGramConfig.separatedHeaders()),", rlv)
        self.assertIn("separateHeader(child);", rlv)
        self.assertIn("outRect.left = outRect.right = dp(3);", rlv)
        urv = read(JAVA / "ui/Components/UniversalRecyclerView.java")
        self.assertIn("UniversalRecyclerView::isSectionViewType,", urv)
        self.assertIn("&& !(UniversalAdapter.isHeader(viewType) && org.telegram.messenger.mzgram.MZGramConfig.separatedHeaders());", urv)
        adapter = read(JAVA / "ui/Components/UniversalAdapter.java")
        self.assertIn("if (nextItem != null && isHeader(nextItem.viewType) && org.telegram.messenger.mzgram.MZGramConfig.separatedHeaders()) {", adapter)

    def test_force_blur(self):
        self.switch("forceBlur", "false", "BUTTON_FORCE_BLUR")
        impl = read(JAVA / "ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java")
        self.assertIn("if (blurEnabled && org.telegram.messenger.mzgram.MZGramConfig.forceBlur) {\n            return true;", impl.replace("\r\n", "\n"))

    def test_remove_message_tail(self):
        self.switch("removeMessageTail", "false", "BUTTON_REMOVE_MESSAGE_TAIL")
        drawable = read(JAVA / "ui/ActionBar/MessageDrawable.java")
        self.assertIn("return org.telegram.messenger.mzgram.MZGramConfig.removeMessageTail;", drawable)
        self.assertEqual(2, drawable.count("if (mzgramNoTail) {"))
        self.assertEqual(2, drawable.count("|| mzgramNoTail) {"))
        self.assertIn("boolean invalidate = lastNoTail != noTail ||", drawable)
        cell = read(JAVA / "ui/Cells/ChatMessageCell.java")
        self.assertEqual(3, cell.count("org.telegram.messenger.mzgram.MZGramConfig.removeMessageTail"))

    def test_share_button_and_poll_results(self):
        self.switch("hideShareButton", "false", "BUTTON_HIDE_SHARE_BUTTON")
        self.switch("showResultsBeforeVoting", "false", "BUTTON_SHOW_RESULTS_BEFORE_VOTING")
        cell = read(JAVA / "ui/Cells/ChatMessageCell.java")
        self.assertIn("pollResultsPreview = messageObject.forceShowPollResults || mzgramPollResultsPreview(media);", cell)
        self.assertIn("return !media.poll.hide_results_until_close || media.poll.creator;", cell)
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.hideShareButton && !messageObject.isSaved) return false;", cell)

    def test_video_seek_pip_volume_voice(self):
        self.switch("swipeToPip", "false", "BUTTON_SWIPE_TO_PIP")
        self.switch("unmuteWithVolumeButtons", "true", "BUTTON_UNMUTE_WITH_VOLUME_BUTTONS")
        self.switch("pauseVoiceOnMinimize", "false", "BUTTON_PAUSE_VOICE_ON_MINIMIZE")
        config = read(CONFIG)
        self.assertIn("public static int doubleTapSeekSeconds = 10;", config)
        self.assertIn('integer(CONFIG, "doubleTapSeekSeconds", 5, 30);', read(BACKUP))
        self.assertIn("items.add(UItem.asButton(BUTTON_DOUBLE_TAP_SEEK, ", read(PAGE))
        viewer = read(JAVA / "ui/PhotoViewer.java")
        self.assertIn("return org.telegram.messenger.mzgram.MZGramConfig.doubleTapSeekSeconds * 1000L;", viewer)
        self.assertIn("total - current > mzgramSeekMillis()", viewer)
        self.assertIn("videoForwardDrawable.addTime(seekMs);", viewer)
        self.assertIn("return org.telegram.messenger.mzgram.MZGramConfig.swipeToPip", viewer)
        launch = read(JAVA / "ui/LaunchActivity.java")
        self.assertIn("} else if (org.telegram.messenger.mzgram.MZGramConfig.unmuteWithVolumeButtons", launch)
        self.assertIn("org.telegram.messenger.mzgram.MZGramRoundPause.onAppMinimized();", launch)
        self.assertIn("if (!MZGramConfig.pauseVoiceOnMinimize) {", read(JAVA / "messenger/mzgram/MZGramRoundPause.java"))

    def test_replies_sticker_shape_recent_stickers(self):
        self.switch("replyColors", "true", "BUTTON_REPLY_COLORS")
        self.switch("replyEmoji", "true", "BUTTON_REPLY_EMOJI")
        self.switch("replyBackground", "true", "BUTTON_REPLY_BACKGROUND")
        self.switch("unlimitedRecentStickers", "false", "BUTTON_UNLIMITED_RECENT_STICKERS")
        line = read(JAVA / "ui/Components/ReplyMessageLine.java")
        self.assertIn("if (type == TYPE_REPLY && !org.telegram.messenger.mzgram.MZGramConfig.replyColors) {", line)
        self.assertIn("if (type == TYPE_REPLY && !org.telegram.messenger.mzgram.MZGramConfig.replyEmoji) {", line)
        self.assertIn("if (type == TYPE_REPLY && !org.telegram.messenger.mzgram.MZGramConfig.replyBackground) {", line)
        config = read(CONFIG)
        self.assertIn("public static int stickerShape = STICKER_SHAPE_DEFAULT;", config)
        self.assertNotIn("stickerRoundCorners =", config)
        self.assertIn('preferences.getBoolean("stickerRoundCorners", false) ? STICKER_SHAPE_ROUNDED', config)
        self.assertIn('integer(CONFIG, "stickerShape", 0, 2);', read(BACKUP))
        cell = read(JAVA / "ui/Cells/ChatMessageCell.java")
        self.assertIn("canChangeRadius = org.telegram.ui.mzgram.MZGramStickers.messageShape();", cell)
        self.assertIn("documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER && org.telegram.ui.mzgram.MZGramStickers.messageShape()", cell)
        self.assertIn("items.add(UItem.asButton(BUTTON_STICKER_SHAPE, ", read(PAGE))
        media = read(JAVA / "messenger/MediaDataController.java")
        self.assertEqual(3, media.count("MZGramConfig.recentStickersLimit(getMessagesController().maxRecentStickersCount)"), "adding, saving and keeping after a sync")
        self.assertNotIn("= getMessagesController().maxRecentStickersCount;", media)

    def test_swipe_bottom_button_keyboard_comma_send_as(self):
        self.switch("hideKeyboardOnScroll", "false", "BUTTON_HIDE_KEYBOARD_ON_SCROLL")
        self.switch("addCommaAfterMention", "false", "BUTTON_ADD_COMMA_AFTER_MENTION")
        self.switch("hideSendAsPeer", "false", "BUTTON_HIDE_SEND_AS_PEER")
        config = read(CONFIG)
        self.assertNotIn("hideChannelBottomButtons =", config)
        self.assertIn('preferences.getBoolean("hideChannelBottomButtons", false) ? CHANNEL_BOTTOM_HIDE : CHANNEL_BOTTOM_MUTE', config)
        self.assertIn("public static int swipeAction = 0;", config)
        backup = read(BACKUP)
        self.assertIn('integer(CONFIG, "channelBottomButton", 0, 2);', backup)
        self.assertIn('integer(CONFIG, "swipeAction", 0, 5);', backup)
        self.assertNotIn('"hideChannelBottomButtons"', backup)
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_SWIPE_ACTION, ", page)
        self.assertIn("items.add(UItem.asButton(BUTTON_CHANNEL_BOTTOM_BUTTON, ", page)
        chat = read(JAVA / "ui/ChatActivity.java")
        self.assertIn("&& !mzgramSwipeAction(getSlidingMessageObject())) { // MZGram: Swipe action", chat)
        self.assertIn("} else if (mzgramDiscussButton()) { // MZGram: Bottom button Discuss", chat)
        self.assertIn("if (mzgramDiscussButton()) { // MZGram: Bottom button Discuss", chat)
        self.assertIn("MZGramConfig.channelBottomButton == org.telegram.messenger.mzgram.MZGramConfig.CHANNEL_BOTTOM_HIDE", chat)
        self.assertIn("chatActivityEnterView.closeKeyboard();", chat)
        self.assertEqual(2, chat.count("// MZGram: Comma after mention"))
        mentions = read(JAVA / "ui/Components/MentionsContainerView.java")
        self.assertEqual(3, mentions.count("// MZGram: Comma after mention"))
        self.assertIn("!org.telegram.messenger.mzgram.MZGramConfig.hideSendAsPeer && defPeer != null", read(JAVA / "ui/Components/ChatActivityEnterView.java"))
        swipe = read(JAVA / "ui/mzgram/MZGramSwipeAction.java")
        self.assertIn("public static final int DELETE = 5;", swipe)

    def test_edited_icon_online_dot_forwards(self):
        self.switch("replaceEditedWithIcon", "false", "BUTTON_REPLACE_EDITED_WITH_ICON")
        self.switch("showOnlineStatus", "false", "BUTTON_SHOW_ONLINE_STATUS")
        self.switch("showForwardsCount", "false", "BUTTON_SHOW_FORWARDS_COUNT")
        cell = read(JAVA / "ui/Cells/ChatMessageCell.java")
        self.assertIn("timeString = org.telegram.ui.mzgram.MZGramEditedIcon.MARK + ", cell)
        self.assertIn("currentTimeString = org.telegram.ui.mzgram.MZGramEditedIcon.apply(currentTimeString);", cell)
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.showForwardsCount && messageObject.messageOwner.forwards > 0) {", cell)
        chat = read(JAVA / "ui/ChatActivity.java")
        self.assertIn("org.telegram.ui.mzgram.MZGramOnlineDot.draw(canvas, imageReceiver, currentAccount,", chat)
        dot = read(JAVA / "ui/mzgram/MZGramOnlineDot.java")
        self.assertIn("if (!MZGramConfig.showOnlineStatus || user == null", dot)
        icon = read(JAVA / "ui/mzgram/MZGramEditedIcon.java")
        self.assertIn('public static final String MARK = "\\u270E";', icon)

    def test_telegram_ai_admin_actions_quick_transition(self):
        self.switch("quickAdminShortcuts", "false", "BUTTON_QUICK_ADMIN_SHORTCUTS")
        self.switch("telegramAiEditor", "true", "BUTTON_TELEGRAM_AI_EDITOR")
        self.switch("telegramAiSummary", "true", "BUTTON_TELEGRAM_AI_SUMMARY")
        self.switch("telegramAiIvSummary", "true", "BUTTON_TELEGRAM_AI_IV_SUMMARY")
        self.switch("quickTransitionChannels", "true", "BUTTON_QUICK_TRANSITION_CHANNELS")
        self.switch("quickTransitionTopics", "true", "BUTTON_QUICK_TRANSITION_TOPICS")
        ai = "org.telegram.messenger.mzgram.MZGramTelegramAi"
        self.assertIn(f"!{ai}.summaryHidden() && TranslateController.isSummarizable(messageObject)", read(JAVA / "ui/Cells/ChatMessageCell.java"))
        for name in ("CaptionPhotoViewer", "ChatAttachAlert", "ChatActivityEnterView"):
            self.assertIn(f"&& !{ai}.editorHidden(); // MZGram: Telegram AI features", read(JAVA / f"ui/Components/{name}.java"), name)
        self.assertEqual(1, read(JAVA / "ui/iv/RichEditor.java").count(f"if ({ai}.editorHidden())"))
        self.assertEqual(2, read(JAVA / "ui/iv/RichEditorToolbar.java").count(f"if ({ai}.editorHidden())"))
        self.assertIn(f"if ({ai}.hideIvBlock(block))", read(JAVA / "ui/ArticleViewer.java"))
        chat = read(JAVA / "ui/ChatActivity.java")
        self.assertIn("org.telegram.ui.mzgram.MZGramAdminShortcuts.addItems(headerItem, currentChat, chatMode);", chat)
        self.assertIn("} else if (org.telegram.ui.mzgram.MZGramAdminShortcuts.onItem(ChatActivity.this, id)) {", chat)
        self.assertIn("!currentChat.megagroup && org.telegram.messenger.mzgram.MZGramConfig.quickTransitionChannels", chat)
        self.assertIn("&& org.telegram.messenger.mzgram.MZGramConfig.quickTransitionTopics) && chatMode != MODE_SAVED", chat)
        self.assertNotIn("= 1104;", chat)
        self.assertIn("MZGramConfig.quickAdminShortcuts && chat != null", read(JAVA / "ui/mzgram/MZGramAdminShortcuts.java"))

    def test_camera_and_calculation_results(self):
        self.switch("rememberLastCamera", "false", "BUTTON_REMEMBER_LAST_CAMERA")
        self.switch("staticZoom", "false", "BUTTON_STATIC_ZOOM")
        self.switch("inlineUnits", "false", "BUTTON_INLINE_UNITS")
        self.switch("inlineTimeZones", "false", "BUTTON_INLINE_TIME_ZONES")
        config = read(CONFIG)
        self.assertIn("public static boolean roundVideoFrontCamera = true;", config)
        self.assertIn('"roundVideoFrontCamera"', read(BACKUP))
        self.assertIn("items.add(UItem.asButton(BUTTON_ROUND_VIDEO_CAMERA, ", read(PAGE))
        camera = read(JAVA / "ui/Components/InstantCameraView.java")
        self.assertEqual(2, camera.count("MZGramConfig.rememberRoundVideoCamera(isFrontface); // MZGram: Remember last camera"))
        self.assertIn("isFrontface = org.telegram.messenger.mzgram.MZGramConfig.roundVideoFrontCamera; // MZGram: Camera in video messages", camera)
        self.assertIn("if (finishZoomTransition != null || org.telegram.messenger.mzgram.MZGramConfig.staticZoom) {", camera)
        math = read(JAVA / "ui/mzgram/MZGramInlineMath.java")
        self.assertIn("result = MZGramUnitConvert.resultFor(text);", math)
        convert = read(JAVA / "ui/mzgram/MZGramUnitConvert.java")
        self.assertNotIn("http", convert)

    def test_group_message_menu(self):
        self.switch("groupMessageMenu", "false", "BUTTON_GROUP_MESSAGE_MENU")
        chat = read(JAVA / "ui/ChatActivity.java")
        self.assertIn("final int mzgramMainStart = org.telegram.ui.mzgram.MZGramMenuGroup.apply(items, options, icons);", chat)
        self.assertIn("if (option == OPTION_REMOVE_ADS || a == mzgramMainStart) {", chat)
        group = read(JAVA / "ui/mzgram/MZGramMenuGroup.java")
        for option in ("REPLY", "COPY", "FORWARD", "EDIT", "PIN", "UNPIN", "DELETE"):
            self.assertIn(f"ChatActivity.OPTION_{option},", group)

    def test_ghost_mode_parts(self):
        for name, default, button in (("ghostNoRead", "true", "BUTTON_GHOST_NO_READ"),
                                      ("ghostNoReadStories", "true", "BUTTON_GHOST_NO_READ_STORIES"),
                                      ("ghostNoOnline", "true", "BUTTON_GHOST_NO_ONLINE"),
                                      ("ghostNoTyping", "true", "BUTTON_GHOST_NO_TYPING"),
                                      ("ghostOfflineAuto", "false", "BUTTON_GHOST_OFFLINE_AUTO"),
                                      ("ghostReadOnInteraction", "true", "BUTTON_GHOST_READ_ON_INTERACTION"),
                                      ("ghostStatusInTitle", "false", "BUTTON_GHOST_STATUS_IN_TITLE")):
            self.switch(name, default, button)
        gm = "org.telegram.messenger.mzgram.MZGramGhostMode"
        mc = read(JAVA / "messenger/MessagesController.java")
        self.assertIn(f"if ({gm}.blocksTyping(currentAccount, dialogId)) {{", mc)
        self.assertIn(f"if ({gm}.blocksRead(currentAccount, task.dialogId)) {{", mc)
        self.assertIn("!org.telegram.messenger.mzgram.MZGramConfig.ghostReadOnInteraction", mc)
        self.assertIn(f"!ApplicationLoader.mainInterfacePausedStageQueue && !{gm}.blocksOnline()) {{", mc)
        self.assertIn("public void mzgramGoOffline() {", mc)
        self.assertIn(f"{gm}.afterSent(currentAccount);", read(JAVA / "messenger/SendMessagesHelper.java"))
        stories = read(JAVA / "ui/Stories/StoriesController.java")
        self.assertEqual(2, stories.count(f"if (!{gm}.blocksStories(currentAccount, dialogId)) {{"))
        self.assertIn("NIGHT_MODE, GHOST_MODE, FEED, SETTINGS", read(JAVA / "messenger/mzgram/MZGramDrawerItems.java"))
        self.assertIn("case MZGramDrawerItems.GHOST_MODE:", read(JAVA / "ui/mzgram/MZGramDrawer.java"))
        dialogs = read(JAVA / "ui/DialogsActivity.java")
        self.assertIn("mzgramUpdateGhostTitle(); // MZGram: Ghost in the header", dialogs)
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_GHOST_SILENT_SEND, ", page)
        self.assertNotIn("MZGramConfig.toggleGhostSilentSend();", page)

    def test_spy_limit_read_dates_last_online_deleted_icon(self):
        self.switch("saveReadDates", "true", "BUTTON_SAVE_READ_DATES")
        self.switch("saveLastOnline", "true", "BUTTON_SAVE_LAST_ONLINE")
        self.switch("deletedMarkIcon", "false", "BUTTON_DELETED_MARK_ICON")
        config = read(CONFIG)
        self.assertIn("public static int archiveTotalLimitMb = -1;", config)
        backup = read(BACKUP)
        self.assertIn('integer(CONFIG, "archiveTotalLimitMb", -1, 16384);', backup)
        self.assertIn('integer(CONFIG, "deletedMarkIconColor", 0, 1);', backup)
        history = read(JAVA / "messenger/mzgram/MZGramHistoryController.java")
        self.assertEqual(2, history.count("|| !MZGramArchiveCap.allows(fileSize(message))"))
        self.assertIn("!MZGramConfig.saveReadDates", read(JAVA / "messenger/mzgram/MZGramReadDates.java"))
        self.assertIn("!MZGramConfig.saveLastOnline", read(JAVA / "messenger/mzgram/MZGramLastSeen.java"))

    def test_approximate_last_seen_takes_real_times_only(self):
        last = read(JAVA / "messenger/mzgram/MZGramLastSeen.java")
        self.assertEqual(2, last.count("!MZGramConfig.saveLastOnline"), "noting and showing")
        self.assertIn("if (date > latest) {", last)
        self.assertIn("known <= latest && known >= date - SAME_TIME", last)
        self.assertIn("return LocaleController.formatDateOnline(Math.min(at, now), madeShorter);", last)
        controller = read(JAVA / "messenger/MessagesController.java")
        self.assertIn("if (update.peer.user_id != 0 && !fromGetDifference && Math.abs(getConnectionsManager().getCurrentTime() - date) < 30) {", controller)
        self.assertIn("onlinePrivacy.put(userId, date);\n                        // MZGram: typing is a sign of being online.", controller)
        self.assertEqual(2, controller.count("MZGramLastSeen.recordStatus("), "live update and loaded user")
        # The support users go through putUser from the constructor (addSupportUser) with no
        # status: no ConnectionsManager there, it would build a MessagesController again
        self.assertIn("if (user.status != null) {\n                    org.telegram.messenger.mzgram.MZGramLastSeen.recordStatus(user.id, user.status, getConnectionsManager().getCurrentTime());", controller)
        constructor = controller[controller.index("addSupportUser();"):]
        constructor = constructor[:constructor.index("\n    }\n")]
        self.assertNotIn("getConnectionsManager()", constructor, "after addSupportUser in the constructor")
        self.assertIn("MZGramLastSeen.recordStatus(status.user_id, status.status,", read(JAVA / "messenger/ContactsController.java"))
        self.assertIn("approximateStatus(user, currentTime, madeShorter)", read(JAVA / "messenger/LocaleController.java"))
        self.assertIn("seenAt < ? OR seenAt > ?", read(JAVA / "messenger/mzgram/MZGramHistoryDatabase.java"))
        self.assertIn("at /= 1000; // kept in milliseconds", read(JAVA / "messenger/mzgram/MZGramForeignArchiveImport.java"))

    def test_approximate_last_seen_signs_with_a_real_time(self):
        # As in the reference: a reaction counts at the time it was set; a
        # "recently" status (also sent on a privacy change), away, greeting and
        # scheduled messages do not
        last = read(JAVA / "messenger/mzgram/MZGramLastSeen.java")
        self.assertIn("public static void recordReactions(TLRPC.MessageReactions reactions, long selfId) {", last)
        controller = read(JAVA / "messenger/MessagesController.java")
        self.assertNotIn("// MZGram: a hidden status changing now.", controller)
        self.assertIn("MZGramLastSeen.recordReactions(update.reactions, getUserConfig().getClientUserId());", controller)
        history = read(JAVA / "messenger/mzgram/MZGramHistoryController.java")
        self.assertIn("MZGramLastSeen.recordReactions(message.reactions, selfId);", history)
        self.assertIn("if (message.out || message.offline || message.from_scheduled || message.via_business_bot_id != 0", history)
        self.assertIn("Reactions count at the time they were set", read(ROOT / "FEATURES.md"))
        cell = read(JAVA / "ui/Cells/ChatMessageCell.java")
        self.assertIn("timeString = org.telegram.ui.mzgram.MZGramEditedIcon.DELETED + ", cell)
        self.assertIn("items.add(UItem.asIntSlideView(1, 0, org.telegram.messenger.mzgram.MZGramArchiveCap.step()", read(PAGE))

    def test_reset_settings_and_delete_account(self):
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_RESET_SETTINGS, getString(R.string.MZGramResetSettings)));", page)
        self.assertIn("items.add(UItem.asButton(BUTTON_DELETE_ACCOUNT, getString(R.string.MZGramDeleteAccount)).red());", page)
        self.assertIn('private static final String DELETE_ACCOUNT_URL = "https://my.telegram.org/deactivate";', page)
        self.assertIn("removed = org.telegram.messenger.mzgram.MZGramSettingsBackup.reset();", page)
        backup = read(BACKUP)
        self.assertIn("public static int reset() {", backup)
        self.assertIn("if (accepted(file, entry.getKey(), entry.getValue()) != null) {", backup)

    def test_feed(self):
        self.switch("feedTab", "false", "BUTTON_FEED_TAB")
        self.switch("feedUnreadCounter", "true", "BUTTON_FEED_UNREAD_COUNTER")
        self.switch("feedWidePosts", "true", "BUTTON_FEED_WIDE_POSTS")
        self.switch("feedIncludeArchived", "false", "BUTTON_FEED_INCLUDE_ARCHIVED")
        self.assertIn('integer(CONFIG, "feedPostsPerChannel", 10, 50);', read(BACKUP))
        page = read(PAGE)
        self.assertIn("public static final int SECTION_FEED = 10;", page)
        self.assertIn("case SECTION_FEED: fillFeed(items); break;", page)
        self.assertIn("items.add(UItem.asButton(BUTTON_FEED_PAGE, getString(R.string.MZGramFeed)));", page)
        tabs = read(JAVA / "ui/MainTabsActivity.java")
        self.assertIn("return new org.telegram.ui.mzgram.MZGramFeedActivity(args);", tabs)
        self.assertIn("GlassTabView.TabAnimation.FILES, R.string.MZGramFeed", tabs)
        self.assertIn("message.mzgramFeed && MZGramConfig.feedWidePosts", read(JAVA / "ui/mzgram/MZGramWidePosts.java"))
        feed = read(JAVA / "messenger/mzgram/MZGramFeed.java")
        self.assertIn("new TLRPC.TL_messages_getHistory()", feed)
        self.assertNotIn("http", feed)
        self.assertIn("case MZGramDrawerItems.FEED:", read(JAVA / "ui/mzgram/MZGramDrawer.java"))


    def test_glass_message_menu(self):
        self.switch("glassMessageMenu", "true", "BUTTON_GLASS_MESSAGE_MENU")
        blur = read(JAVA / "ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java")
        self.assertIn("if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) || messageMenu && !org.telegram.messenger.mzgram.MZGramConfig.glassMessageMenu) {", blur)
        self.assertIn(".setColorProvider(BlurredBackgroundProviderImpl.messageMenuPopupBackground(resourceProvider))", read(JAVA / "ui/ChatActivity.java"))

    def test_settings_count_matches_rows(self):
        # The emulator test counts the rows it sees: every BUTTON_ constant, less
        # those under a switch that is off by default and the three a build
        # without Python never shows (the two reports and Python for plugins).
        # Kept equal here, so the count is checked before CI (run 43517725611
        # had 247 for 246 rows).
        page = read(PAGE)
        buttons = set(re.findall(r"private static final int BUTTON_\w+ = (\d+);", page))
        self.assertEqual(len(buttons), len(re.findall(r"private static final int BUTTON_\w+ = \d+;", page)), "each BUTTON_ value once")
        kotlin = read(ROOT / "TMessagesProj_AppTests/src/androidTest/kotlin/org/telegram/messenger/mzgram/test/MZGramSettingsTest.kt")
        under = kotlin.split("private val underSwitch = listOf(", 1)[1].split("\n    )", 1)[0]
        hidden = len(re.findall(r'"BUTTON_\w+"', under))
        expected = int(re.search(r"val expectedCount = (\d+) \+", kotlin).group(1))
        self.assertEqual(len(buttons) - hidden - 3, expected)

    def test_material3_player_on_by_default(self):
        self.switch("musicPlayer", "true", "BUTTON_MUSIC_PLAYER")
        kotlin = read(ROOT / "TMessagesProj_AppTests/src/androidTest/kotlin/org/telegram/messenger/mzgram/test/MZGramSettingsTest.kt")
        under = kotlin.split("private val underSwitch = listOf(", 1)[1].split(")", 1)[0]
        for row in ("BUTTON_MUSIC_PLAYER_BAR", "BUTTON_MUSIC_PLAYER_MINI", "BUTTON_MUSIC_PLAYER_STYLE"):
            self.assertNotIn(row, under)
        self.assertIn("On by default; off, music opens in Telegram's player.", read(ROOT / "FEATURES.md"))

    def test_player_extras_on_by_default(self):
        # Everything of the new player is on by default (owner, 2026-11-14)
        self.switch("musicPlayerBar", "true", "BUTTON_MUSIC_PLAYER_BAR")
        self.switch("profileMusicCard", "true", "BUTTON_PROFILE_MUSIC_CARD")
        self.assertIn("On by default; off, the profile keeps Telegram's music row.", read(ROOT / "FEATURES.md"))

    def test_exteraless_chat_switches(self):
        # From exteraless (audit-exteraless.md, batch 1); all off by default
        for name, button in [("disableGlobalSearch", "BUTTON_DISABLE_GLOBAL_SEARCH"), ("hideChannelGiftButton", "BUTTON_HIDE_CHANNEL_GIFT"),
                             ("hideChannelSearchButton", "BUTTON_HIDE_CHANNEL_SEARCH"), ("hideCallButton", "BUTTON_HIDE_CALL_BUTTON"),
                             ("hideAttachMoney", "BUTTON_HIDE_ATTACH_MONEY"), ("forwardedDate", "BUTTON_FORWARDED_DATE"),
                             ("smallGifs", "BUTTON_SMALL_GIFS"), ("rememberAllReplies", "BUTTON_REMEMBER_ALL_REPLIES"),
                             ("disableTrending", "BUTTON_DISABLE_TRENDING"), ("hideGroupSticker", "BUTTON_HIDE_GROUP_STICKER"),
                             ("disableAvatarBlur", "BUTTON_DISABLE_AVATAR_BLUR"), ("sortByUnread", "BUTTON_SORT_BY_UNREAD"),
                             ("disableNotificationBubbles", "BUTTON_DISABLE_NOTIFICATION_BUBBLES"), ("dontAutoPlayNextVoice", "BUTTON_DONT_AUTOPLAY_NEXT_VOICE"),
                             ("disableProximity", "BUTTON_DISABLE_PROXIMITY"), ("confirmRepeat", "BUTTON_CONFIRM_REPEAT"),
                             ("confirmBotCommands", "BUTTON_CONFIRM_BOT_COMMANDS")]:
            self.switch(name, "false", button)
        self.switch("contentCapture", "true", "BUTTON_CONTENT_CAPTURE")
        chat = read(JAVA / "ui/ChatActivity.java")
        self.assertIn("!org.telegram.messenger.mzgram.MZGramConfig.hideCallButton) {", chat)
        self.assertIn("&& !org.telegram.messenger.mzgram.MZGramConfig.hideCallButton; // MZGram: Hide Call button", chat)
        self.assertIn("isChannelOrGiga(currentChat) && !org.telegram.messenger.mzgram.MZGramConfig.hideChannelSearchButton;", chat)
        self.assertIn("showGiftButton && !org.telegram.messenger.mzgram.MZGramConfig.hideChannelGiftButton &&", chat)
        self.assertIn("returnToMessageId = returnToMessageIdsStack.pop();", chat)
        self.assertIn("returnToMessageIdsStack.push(fromMessageId);", chat)
        self.assertIn("returnToMessageIdsStack.clear();", chat)
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.confirmRepeat && !mzgramRepeatConfirmed) {", chat)
        self.assertIn("return allowGlobalSearch && !org.telegram.messenger.mzgram.MZGramConfig.disableGlobalSearch;", read(JAVA / "ui/Adapters/DialogsSearchAdapter.java"))
        self.assertIn("!org.telegram.messenger.mzgram.MZGramConfig.hideAttachMoney &&", read(JAVA / "ui/Components/ChatAttachAlert.java"))
        emoji = read(JAVA / "ui/Components/EmojiView.java")
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramConfig.disableTrending && !featured.isEmpty()", emoji)
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramConfig.disableTrending) { // MZGram: Disable trending", emoji)
        self.assertIn("if (info != null && !org.telegram.messenger.mzgram.MZGramConfig.hideGroupSticker) {", emoji)
        self.assertIn("|| org.telegram.messenger.mzgram.MZGramConfig.disableTrending) { // MZGram: Disable trending", read(JAVA / "messenger/MediaDataController.java"))
        cell = read(JAVA / "ui/Cells/ChatMessageCell.java")
        self.assertIn("MZGramForwardDate.withDate(messageObject, forwardedString)", cell)
        self.assertIn("MZGramConfig.smallGifs && messageObject.type == MessageObject.TYPE_GIF", cell)
        profile = read(JAVA / "ui/ProfileActivity.java")
        self.assertIn("this.blurEnabled = !org.telegram.messenger.mzgram.MZGramConfig.disableAvatarBlur;", profile)
        self.assertIn("avatarsBlurView.setSize(org.telegram.messenger.mzgram.MZGramConfig.disableAvatarBlur ? 0 : getActionsExtraHeight());", profile)
        self.assertEqual(2, read(JAVA / "ui/Components/ProfileGalleryBlurView.java").count("return; // MZGram: Disable avatar blur"))
        self.assertEqual(2, read(JAVA / "messenger/MessagesController.java").count("MZGramUnreadSort.isPriority(currentAccount, dialog1)"), "both chat list orders")
        self.assertIn("supportsBubble && !org.telegram.messenger.mzgram.MZGramConfig.disableNotificationBubbles", read(JAVA / "messenger/NotificationsController.java"))
        media = read(JAVA / "messenger/MediaController.java")
        self.assertIn("final boolean stopAtVoiceEnd = messageObject.isVoice() && org.telegram.messenger.mzgram.MZGramConfig.dontAutoPlayNextVoice;", media)
        self.assertIn("return !org.telegram.messenger.mzgram.MZGramConfig.disableProximity && value < 5.0f", media)
        self.assertIn("return !org.telegram.messenger.mzgram.MZGramConfig.disableProximity && value < 5.0f", read(JAVA / "ui/Components/EarListener.java"))
        self.assertIn("boolean newIsNear = !org.telegram.messenger.mzgram.MZGramConfig.disableProximity &&", read(JAVA / "messenger/voip/VoIPService.java"))
        enter = read(JAVA / "ui/Components/ChatActivityEnterView.java")
        self.assertIn("if (org.telegram.messenger.mzgram.MZGramConfig.confirmBotCommands && parentActivity != null) {", enter)
        self.assertIn("private void sendBotCommand(MessageObject messageObject, String command, boolean username) {", enter)
        self.assertIn("org.telegram.messenger.mzgram.MZGramContentCapture.apply(this); // MZGram", read(JAVA / "ui/LaunchActivity.java"))
        features = read(ROOT / "FEATURES.md")
        for name in ["Hide Gift and Search buttons in channels", "Hide Call button", "Hide the money tab in the attach menu", "Date on forwarded messages",
                     "Remember all opened replies", "Confirm repeating messages", "Confirm bot commands", "Disable trending", "Hide the group sticker set",
                     "Do not play the next voice message", "Ignore the proximity sensor", "Small GIFs", "Unread chats first", "Disable global search",
                     "Disable notification bubbles", "Disable avatar blur", "Share screen content with Android"]:
            self.assertIn("| " + name + " |", features)

    def test_chat_menu_items(self):
        # Quick admin actions: each item can be turned off, plus Members and
        # Statistics; "To the beginning" for every chat (exteraless)
        self.switch("chatMenuToBeginning", "false", "BUTTON_CHAT_MENU_TO_BEGINNING")
        config = read(CONFIG)
        for key in ["adminShortcutRecentActions", "adminShortcutAdministrators", "adminShortcutPermissions", "adminShortcutMembers", "adminShortcutStatistics"]:
            self.assertIn(f"public static boolean {key} = true;", config)
            self.assertIn(f'"{key}"', read(BACKUP))
        page = read(PAGE)
        block = page.split("if (MZGramConfig.quickAdminShortcuts) {", 1)[1].split("        }", 1)[0]
        for button in ["BUTTON_ADMIN_SHORTCUT_RECENT", "BUTTON_ADMIN_SHORTCUT_ADMINS", "BUTTON_ADMIN_SHORTCUT_PERMISSIONS", "BUTTON_ADMIN_SHORTCUT_MEMBERS", "BUTTON_ADMIN_SHORTCUT_STATISTICS"]:
            self.assertIn(button, block)
            self.assertIn(f"item.id == {button})", page)
        shortcuts = read(JAVA / "ui/mzgram/MZGramAdminShortcuts.java")
        self.assertIn("fragment.presentFragment(StatisticActivity.create(chat, false));", shortcuts)
        self.assertIn('args.putInt("type", ChatUsersActivity.TYPE_USERS);', shortcuts)
        self.assertIn("fragment.scrollToMessageId(1, 0, false, 0, true, 0);", shortcuts)
        self.assertIn("MZGramAdminShortcuts.addToBeginning(headerItem, chatMode, isThreadChat());", read(JAVA / "ui/ChatActivity.java"))
        self.assertIn('| "To the beginning" in the chat menu |', read(ROOT / "FEATURES.md"))

    def test_unlimited_saved_gifs(self):
        # exteraless: a sync with the server keeps what only this device has
        self.switch("unlimitedSavedGifs", "false", "BUTTON_UNLIMITED_SAVED_GIFS")
        media = read(JAVA / "messenger/MediaDataController.java")
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramConfig.unlimitedSavedGifs && ((recentGifs.size() > getMessagesController().savedGifsLimitDefault", media)
        self.assertIn("maxCount = org.telegram.messenger.mzgram.MZGramConfig.unlimitedSavedGifs ? Integer.MAX_VALUE : getMessagesController().maxRecentGifsCount;", media)
        self.assertIn("if (replace && documents != null && org.telegram.messenger.mzgram.MZGramRecentKeep.keeps(gif, type)) {", media)
        self.assertIn("processLoadedRecentDocumentsKept(type, documents, gif, date, replace);", media)
        features = read(ROOT / "FEATURES.md")
        self.assertIn("| Unlimited saved GIFs |", features)
        self.assertIn("stickers past Telegram's list stay on this device after a sync too", features)

    def test_floating_mini_player(self):
        self.switch("musicPlayerMini", "true", "BUTTON_MUSIC_PLAYER_MINI")
        tabs = read(JAVA / "ui/MainTabsActivity.java")
        self.assertIn("miniPlayer = new org.telegram.ui.mzgram.player.MZGramPlayerMiniView(context, this, resourceProvider);", tabs)
        self.assertIn("return prepareTabFragment(createTabFragment(position));", tabs)
        self.assertIn("miniPlayer.setHostPosition(", tabs)
        for page in ("DialogsActivity", "ContactsActivity", "CallLogActivity", "SettingsActivity", "ProfileActivity", "mzgram/MZGramFeedActivity"):
            self.assertIn("public void setMiniPlayerInset(float inset, int padding) {", read(JAVA / ("ui/" + page + ".java")), page)
        player = read(JAVA / "ui/mzgram/player/MZGramMusicPlayer.java")
        self.assertIn("return MZGramConfig.musicPlayer && MZGramConfig.musicPlayerMini;", player)
        self.assertIn("org.telegram.ui.mzgram.player.MZGramMusicPlayer.hidesContextPlayer(fragment, messageObject)", read(JAVA / "ui/Components/FragmentContextView.java"))
        mini = read(JAVA / "ui/mzgram/player/MZGramPlayerMiniView.java")
        self.assertIn("implements NotificationCenter.NotificationCenterDelegate, MZGramPlayerSheet.TransitionSource", mini)
        self.assertIn("Port of exteraless PlayerMiniView (GPL-3.0)", mini)
        self.assertIn("sections[2]", read(JAVA / "ui/mzgram/player/MZGramPlayerStyleEditor.java"))

    def test_material3_group(self):
        self.switch("m3Loading", "false", "BUTTON_M3_LOADING")
        self.switch("m3Slider", "false", "BUTTON_M3_SLIDER")
        page = read(PAGE)
        header = page.index("items.add(UItem.asHeader(getString(R.string.MZGramHeaderMaterial3)));")
        for row in ("BUTTON_M3_SWITCHES", "BUTTON_CARDS_DESIGN", "BUTTON_M3_LOADING", "BUTTON_M3_SLIDER"):
            self.assertGreater(page.index(f"items.add(check({row}, "), header, row)
        radial = read(JAVA / "ui/Components/RadialProgressView.java")
        self.assertEqual(2, radial.count("org.telegram.messenger.mzgram.appearance.MZGramM3Loading.drawArc(canvas, cicleRect, radOffset, "))
        self.assertNotIn("canvas.drawArc(", radial)
        self.assertIn("MZGramM3Loading.drawArc(", read(JAVA / "ui/Components/CircularProgressDrawable.java"))
        seek = read(JAVA / "ui/Components/SeekBarView.java")
        self.assertIn("MZGramM3Slider.applies(twoSided, minProgress, bufferedProgress, lineWidthDp, timestamps != null && !timestamps.isEmpty())", seek)
        loading = read(JAVA / "messenger/mzgram/appearance/MZGramM3Loading.java")
        self.assertIn("return MZGramConfig.m3Loading;", loading)
        # Off: Telegram's own arc
        self.assertIn("            canvas.drawArc(oval, startAngle, sweepAngle, false, paint);\n            return;", loading)
        slider = read(JAVA / "messenger/mzgram/appearance/MZGramM3Slider.java")
        self.assertIn("return MZGramConfig.m3Slider && !twoSided && minProgress <= 0 && bufferedProgress <= 0 && lineWidthDp == 3 && !hasTimestamps;", slider)


if __name__ == "__main__":
    unittest.main()
