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
        self.assertIn(f"items.add(check({button}, ", page)
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


if __name__ == "__main__":
    unittest.main()
