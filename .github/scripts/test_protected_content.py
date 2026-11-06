""""Forward and save protected content": the app's checks follow the switch.

MZGram decides in MessageObject.isNoforwards() and in MessagesController's
isChatNoForwards()/isUserNoForwards() whether a message or a chat is
protected; with the switch on they say no. Telegram's screens read the raw
server flag (messageOwner.noforwards) in many places, so every place MZGram
routed through these checks must stay routed after an upstream update: the
photo viewer's screenshot block (FLAG_SECURE, also for photos in rich
messages) and the chat's text selection (Copy).
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
MESSAGE = JAVA / "messenger/MessageObject.java"
CONTROLLER = JAVA / "messenger/MessagesController.java"
VIEWER = JAVA / "ui/PhotoViewer.java"
CHAT = JAVA / "ui/ChatActivity.java"

# Reads of the raw flag of a message, not assignments to it.
RAW_READ = re.compile(r"messageOwner\.noforwards(?!\s*=[^=])")


def read(path):
    return path.read_text(encoding="utf-8") if path.is_file() else ""


class ProtectedContentTest(unittest.TestCase):
    def test_message_check_follows_the_switch(self):
        text = read(MESSAGE)
        body = re.search(r"public boolean isNoforwards\(\) \{(.*?)\n    \}", text, re.S)
        self.assertIsNotNone(body, "MessageObject.isNoforwards() is gone")
        self.assertIn("MZGramProtectedContent.isEnabled()", body.group(1))

    def test_chat_checks_follow_the_switch(self):
        text = read(CONTROLLER)
        for name in ("isChatNoForwards", "isUserNoForwards"):
            self.assertRegex(
                text,
                r"!org\.telegram\.messenger\.mzgram\.MZGramProtectedContent\.isEnabled\(\) && "
                + name + r"OnServer\(",
                name + " does not follow the switch")

    def test_viewer_and_selection_use_the_app_check(self):
        for path in (VIEWER, CHAT):
            text = read(path)
            self.assertTrue(text, path.name + " not found")
            raw = [
                str(number)
                for number, line in enumerate(text.splitlines(), 1)
                if RAW_READ.search(line)
            ]
            self.assertEqual(
                raw, [],
                path.name + " reads the raw flag on lines " + ", ".join(raw)
                + ": use MessageObject.isNoforwards()")

    def test_viewer_blocks_screenshots_through_the_app_check(self):
        text = read(VIEWER)
        secure = text.find("windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;")
        self.assertGreater(secure, 0)
        condition = text[max(0, secure - 900):secure]
        self.assertIn("messageObject.isNoforwards()", condition)
        pages = re.search(r"private boolean isPageBlocksNoForwards\(.*?\n    \}", text, re.S)
        self.assertIsNotNone(pages, "isPageBlocksNoForwards() is gone")
        self.assertIn("isNoforwards()", pages.group(0))


if __name__ == "__main__":
    unittest.main()
