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


if __name__ == "__main__":
    unittest.main()
