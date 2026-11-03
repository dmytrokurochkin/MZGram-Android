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
        found = [i for i, line in enumerate(lines) if "MZGramConfig.showAskAi" in line and line.strip().startswith("if (") and "selectedObject" in line]
        self.assertEqual(1, len(found), "one Ask AI item in the message menu")
        condition = lines[found[0]]
        self.assertIn("!noforwardsOrPaidMedia", condition)
        self.assertIn("!selectedObject.isSponsored()", condition)
        # Also for a message with only a photo or a file
        self.assertIn("MZGramAiActivity.hasAttachment(selectedObject)", condition)
        self.assertIn("options.add(OPTION_MZGRAM_ASK_AI);", "\n".join(lines[found[0]:found[0] + 5]))
        # Right after Copy, which has the same restriction
        before = "\n".join(lines[max(0, found[0] - 8):found[0]])
        self.assertIn("options.add(OPTION_COPY);", before)
        # The message with all of its album
        case = chat[chat.find("case OPTION_MZGRAM_ASK_AI: {"):]
        case = case[:case.find("break;")]
        self.assertIn("MZGramAiActivity.ask(this,", case)
        self.assertIn("selectedObjectGroup.messages", case)
        self.assertIn("askMessages", case)

    def test_selected_messages_have_ask_ai(self):
        chat = read(CHAT)
        self.assertIn("private final static int mzgram_ask_ai = ", chat)
        self.assertIn("actionMode.addItemWithWidth(mzgram_ask_ai, R.drawable.msg_bot", chat)
        # Shown where copying is allowed, for text, photos and files
        visible = chat[chat.find("ActionBarMenuItem mzgramAskAiItem"):]
        visible = visible[:visible.find("mzgramAskAiItem.setVisibility(")]
        self.assertIn("MZGramConfig.showAskAi && !noforwards", visible)
        self.assertIn("MZGramAiActivity.hasAttachment(", visible)
        handler = chat[chat.find("} else if (id == mzgram_ask_ai) {"):]
        handler = handler[:handler.find("} else if (id == copy) {")]
        self.assertTrue(handler)
        self.assertIn("selectedMessagesIds[a]", handler)
        self.assertIn("getMessageContent(", handler)
        self.assertIn("MZGramAiActivity.ask(ChatActivity.this, str.toString(), askMessages)", handler)

    def test_chat_list_menu_has_the_site_buttons(self):
        dialogs = read(JAVA / "ui/DialogsActivity.java")
        self.assertIn("MZGramAiActivity.addMenuButtons(io, this);", dialogs)
        window = read(WINDOW)
        buttons = window[window.find("public static void addMenuButtons("):]
        buttons = buttons[:buttons.find("\n    }\n")]
        self.assertIn("MZGramAiRules.menuButton(item)", buttons)
        self.assertIn("openSite(fragment, item.key)", buttons)
        # The site alone: no text leaves Telegram, so no warning
        self.assertIn("MZGramConfig.aiWarned || window.emptyRequest()", window)

    def test_attachments_go_by_the_sites_own_chooser(self):
        window = read(WINDOW)
        # The site's own add-files button: MZGram's chooser, every checked
        # file at once; nothing is put into the page by itself
        for part in ("onShowFileChooser", "new MZGramAiFilesSheet(", "MZGramAiRules.give(checked, multiple)",
                     "MZGramAiRules.accepts(accept, ", "MZGramAiRules.chooseAttachments(",
                     "params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE",
                     "giveFiles(uris.isEmpty() ? null : uris.toArray(new Uri[0]))"):
            self.assertIn(part, window)
        for part in ("attachScript", "fileStartScript", "fileChunkScript", "__mzgramAiFiles"):
            self.assertNotIn(part, window)
            self.assertNotIn(part, read(RULES))
        self.assertNotIn("ClipboardEvent", window)
        # The warning says how the files are added; no permanent note
        self.assertIn("getString(R.string.MZGramAiWarningFiles)", window)
        self.assertNotIn("MZGramAiPrivacyNote", window)
        self.assertNotIn("R.string.MZGramAiModel)", window)
        sheet = read(JAVA / "ui/mzgram/MZGramAiFilesSheet.java")
        for part in ("R.string.MZGramAiFilesTitle", "R.string.MZGramAiFilesAdd", "R.string.MZGramAiFilesOther",
                     "new CheckBox2(", "FileLoader.getClosestPhotoSizeWithSize(", "AndroidUtilities.getThumbForNameOrMime("):
            self.assertIn(part, sheet)
        # Small thumbnails only: nothing big is decoded
        self.assertNotIn("BitmapFactory", sheet)

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
