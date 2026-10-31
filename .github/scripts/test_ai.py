""""Ask AI": where the message menu has it and what the window may do.

The item stands next to Copy and has Copy's condition: no item where copying
is not allowed (a chat with "Restrict saving content" while "Forward and
save protected content" is off). The window gets a WebView profile of its
own, gives the site no JavaScript interface of the app, opens no file or
content address, and puts the text in by script only: none of the sites'
address parameters (they send at once or are not documented).
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
CHAT = JAVA / "ui/ChatActivity.java"
WINDOW = JAVA / "ui/mzgram/MZGramAiActivity.java"
RULES = JAVA / "messenger/mzgram/MZGramAiRules.java"


def read(path):
    return path.read_text(encoding="utf-8") if path.is_file() else ""


class AskAiTest(unittest.TestCase):
    def test_menu_item_only_where_copying_is_allowed(self):
        chat = read(CHAT)
        lines = chat.splitlines()
        found = [i for i, line in enumerate(lines) if "MZGramConfig.showAskAi" in line and line.strip().startswith("if (")]
        self.assertEqual(1, len(found), "one Ask AI item in the message menu")
        condition = lines[found[0]]
        self.assertIn("!noforwardsOrPaidMedia", condition)
        self.assertIn("!selectedObject.isSponsored()", condition)
        self.assertIn("options.add(OPTION_MZGRAM_ASK_AI);", "\n".join(lines[found[0]:found[0] + 5]))
        # Right after Copy, which has the same restriction
        before = "\n".join(lines[max(0, found[0] - 8):found[0]])
        self.assertIn("options.add(OPTION_COPY);", before)
        self.assertRegex(chat, r"case OPTION_MZGRAM_ASK_AI: \{[^}]*MZGramAiActivity\.ask\(this,")

    def test_window_has_its_own_profile_and_no_bridge(self):
        window = read(WINDOW)
        self.assertIn("WebViewCompat.setProfile(view, MZGramAiRules.PROFILE)", window)
        self.assertIn("WebViewFeature.MULTI_PROFILE", window)
        self.assertNotIn("addJavascriptInterface", window)
        self.assertIn("setAllowFileAccess(false)", window)
        self.assertIn("setAllowContentAccess(false)", window)
        self.assertIn("MZGramAiRules.insertScript(", window)
        self.assertIn("MZGramAiRules.allowedNavigation(", window)
        self.assertIn("MZGramAiRules.newWindowTarget(", window)
        self.assertIn('"mzgram-ai"', read(RULES))

    def test_no_address_parameters_and_no_sending(self):
        for path in (WINDOW, RULES):
            text = read(path)
            self.assertTrue(text, path.name)
            self.assertIsNone(re.search(r"[?&](q|prompt|model)=", text), path.name)
        rules = read(RULES)
        script = rules[rules.find("public static String insertScript"):rules.find("public static int parseStatus")]
        self.assertTrue(script)
        for word in ("click(", "submit", "keydown", "KeyboardEvent", "Enter"):
            self.assertNotIn(word, script)


if __name__ == "__main__":
    unittest.main()
