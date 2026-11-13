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


if __name__ == "__main__":
    unittest.main()
