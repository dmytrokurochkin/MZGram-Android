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
        self.assertIn('integer(CONFIG, "dividerStyle", 0, 1);', backup)
        page = read(PAGE)
        self.assertIn("items.add(UItem.asButton(BUTTON_DIVIDER_STYLE, ", page)
        self.assertIn("MZGramConfig.setSectionRadius(val);", page)
        rlv = read(JAVA / "ui/Components/RecyclerListView.java")
        self.assertEqual(2, rlv.count("setSections(dp(12), dp(org.telegram.messenger.mzgram.MZGramConfig.sectionRadius), "))
        theme = read(JAVA / "ui/ActionBar/Theme.java")
        self.assertEqual(2, theme.count("if (key == key_divider && org.telegram.messenger.mzgram.MZGramConfig.dividerStyle == org.telegram.messenger.mzgram.MZGramConfig.DIVIDER_HIDDEN) {"))

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
        self.assertEqual(2, media.count("MZGramConfig.recentStickersLimit(getMessagesController().maxRecentStickersCount)"))
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


if __name__ == "__main__":
    unittest.main()
