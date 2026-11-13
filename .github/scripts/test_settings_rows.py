"""Rows of Telegram's Settings hidden from Settings > MZGram > Interface.

Each switch hides one row of Telegram's Settings screen (SettingsActivity) and
its results in the settings search (ProfileActivity's SearchAdapter), also
the FAQ articles when Telegram FAQ is hidden. The Help header goes with the
last of its rows.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
SETTINGS = JAVA / "ui/SettingsActivity.java"
PROFILE = JAVA / "ui/ProfileActivity.java"
ROWS = JAVA / "messenger/mzgram/MZGramSettingsRows.java"
PAGE = JAVA / "ui/mzgram/MZGramSettingsActivity.java"

# The row ids of SettingsActivity: Premium, Stars, Gram Earnings, Business,
# Send a Gift, Wallet, Ask a Question, FAQ, Telegram Features, Privacy Policy
IDS = [11, 12, 13, 15, 16, 25, 17, 18, 23, 19]


def read(path):
    return path.read_text(encoding="utf-8") if path.is_file() else ""


class SettingsRowsTest(unittest.TestCase):
    def test_keep_messages_from_four_switches(self):
        settings = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/mzgram/MZGramSettingsActivity.java").read_text(encoding="utf-8")
        start = settings.index("R.string.MZGramKeepFromTitle")
        end = settings.index("R.string.MZGramKeepFromInfo", start)
        part = settings[start:end]
        for row in ("BUTTON_SAVE_FROM_USERS", "BUTTON_SAVE_FOR_BOTS", "BUTTON_SAVE_IN_GROUPS", "BUTTON_SAVE_IN_CHANNELS"):
            self.assertIn(row, part)
        controller = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/mzgram/MZGramHistoryController.java").read_text(encoding="utf-8")
        # The message decides where it is known; turning a switch off deletes nothing
        self.assertGreaterEqual(controller.count("savesMessage(dialogId, "), 6)
        self.assertIn("message.from_id instanceof TLRPC.TL_peerUser && isBotChat(message.from_id.user_id)", controller)
        self.assertIn("ChatObject.isChannelAndNotMegaGroup(chat)", controller)
        config = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/mzgram/MZGramConfig.java").read_text(encoding="utf-8")
        for name in ("saveFromUsers", "saveInGroups", "saveInChannels"):
            self.assertIn("public static boolean %s = true;" % name, config)
            self.assertIn('preferences.getBoolean("%s", true)' % name, config)
            toggle = config[config.index("public static void toggle%s%s()" % (name[0].upper(), name[1:])):]
            toggle = toggle[:toggle.index("}")]
            self.assertNotIn("delete", toggle.lower())

    def test_each_row_has_its_check(self):
        settings = read(SETTINGS)
        for row in IDS:
            self.assertIn(f"MZGramSettingsRows.hidesRow({row})", settings, row)
            # The check is on the row it hides
            lines = settings.splitlines()
            for i, line in enumerate(lines):
                if re.search(rf"SettingCell\.Factory\.of\({row},", line):
                    # The nearest condition above it
                    block = next(lines[j] for j in range(i - 1, -1, -1) if lines[j].strip().startswith("if ("))
                    self.assertIn(f"hidesRow({row})", block, f"row {row}: {block}")
        self.assertIn("if (!org.telegram.messenger.mzgram.MZGramSettingsRows.helpHidden()) {", settings)

    def test_the_search_leaves_them_out(self):
        profile = read(PROFILE)
        start = profile.find("searchArray = onCreateSearchArray(fragment);")
        self.assertGreater(start, 0)
        self.assertIn("MZGramSettingsRows.hidesSearchResult(searchArray[a].guid)", profile[start:start + 600])
        faq = profile[profile.find("public void loadFaqWebPage() {"):]
        self.assertIn('MZGramSettingsRows.hidden("faq")', faq[:400])
        rows = read(ROWS)
        for key, settings_id in zip(["premium", "stars", "earnings", "business", "gift", "wallet", "ask", "faq", "features", "policy"], IDS):
            self.assertRegex(rows, rf'new Row\("{key}", {settings_id},')
        self.assertIn('new Row("premium", 11, 800, 899, false)', rows)
        for guid, key in ((402, "ask"), (403, "faq"), (404, "policy")):
            self.assertIn(f'new Row("{key}", ', rows)
            self.assertRegex(rows, rf'new Row\("{key}", \d+, {guid}, {guid}, true\)')

    def test_switches_on_the_other_page(self):
        page = read(PAGE)
        self.assertIn("UItem.asHeader(getString(R.string.MZGramHideRowsTitle))", page)
        self.assertIn("UItem.asShadow(getString(R.string.MZGramHideRowsInfo))", page)
        interface = page[page.find("private void fillOther("):]
        interface = interface[:interface.find("\n    }\n")]
        self.assertIn("MZGramSettingsRows.rows()", interface)


if __name__ == "__main__":
    unittest.main()
