"""What MZGram shows comes from MZGram's own strings, in the MZGram language.

Telegram's strings follow Telegram's language and MZGram's follow the MZGram
language (MZGramLanguage), chosen apart. A Telegram string (Cancel, OK,
Delete...) or a text written into the code on an MZGram screen shows in
another language than the rest of it: the update bar's Cancel was always
in Telegram's language. So:

- MZGram's classes (ui/mzgram, messenger/mzgram) use only MZGram strings
  (R.string.MZGram*), never a text written into the code, and of
  LocaleController only what reads a string or formats a number or the
  direction of the text. Telegram's dates and times follow Telegram's
  language: MZGram's screens use MZGramLanguage.formatDateTime.
- In Telegram's classes, a dialog MZGram adds (one with an MZGram string)
  has only MZGram strings, and an item MZGram adds to a message menu
  (behind an MZGramConfig.show* switch) is an MZGram string.

A place that shows Telegram's text on purpose says why in a comment on that
line or in the comment just above it: "Telegram's own text: <why>".
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
MZGRAM_DIRS = [JAVA / "ui/mzgram", JAVA / "messenger/mzgram"]
ALLOWED = re.compile(r"Telegram's own text: \S")
STRING_REF = re.compile(r"\bR\.string\.(\w+)")
# What MZGram's classes may use of LocaleController
LOCALE_CONTROLLER_OK = {"getString", "formatString", "formatPluralString", "formatNumber", "isRTL"}
LOCALE_CONTROLLER_USE = re.compile(r"\bLocaleController\.(\w+)")
STATIC_IMPORT = re.compile(r"import static org\.telegram\.messenger\.LocaleController\.(\w+);")
# Calls that put a text on the screen, and a string literal with words in it
UI_CALL = re.compile(r"\b(setText|setTitle|setSubtitle|setMessage|setPositiveButton|setNegativeButton|setNeutralButton|"
                     r"createSimpleBulletin|createErrorBulletin|createCopyBulletin|makeText|asButton|asCheck|asShadow|"
                     r"asHeader|setTextAndIcon|setContentTitle|setContentText|createChooser|setItems)\s*\(")
LITERAL = re.compile(r'"(?:[^"\\\n]|\\.)*"')
WORDS = re.compile(r"[^\W\d_]{2,}")
DIALOG = re.compile(r"new\s+(?:[\w.]+\.)?AlertDialog\.Builder\(.*?\.(?:show|create)\(\)", re.S)
MENU_ITEM = re.compile(r"items\.add\(LocaleController\.getString\(R\.string\.(\w+)\)\)")


def words_literals(code):
    # String literals with a word in them (not "@", "\n" or ", ")
    return [literal for literal in LITERAL.findall(code) if WORDS.search(re.sub(r"\\.", "", literal))]


def mzgram_string(name):
    return name.startswith("MZGram") or name.startswith("mzgram_")


def allowed(lines, index):
    # The reason on that line or in the comment just above it
    return any(ALLOWED.search(line) for line in lines[max(0, index - 3):index + 1])


def mzgram_files():
    for folder in MZGRAM_DIRS:
        yield from sorted(folder.glob("*.java"))


def telegram_files():
    for path in sorted(JAVA.rglob("*.java")):
        if path.parent not in MZGRAM_DIRS:
            yield path


def code_lines(path):
    # Comments do not show anything
    lines = path.read_text(encoding="utf-8").splitlines()
    result = []
    in_block = False
    for line in lines:
        code = line
        if in_block:
            end = code.find("*/")
            if end < 0:
                result.append(("", line))
                continue
            code = code[end + 2:]
            in_block = False
        start = code.find("/*")
        if start >= 0 and "*/" not in code[start:]:
            code = code[:start]
            in_block = True
        code = re.sub(r"//.*", "", code) if '"' not in code else re.sub(r"\s//\s.*$", "", code)
        result.append((code, line))
    return result


def statement(lines, index):
    # The line and the following ones up to the end of its statement
    text = ""
    for code, _ in lines[index:index + 8]:
        text += code + "\n"
        if code.rstrip().endswith(";") or code.rstrip().endswith("{"):
            break
    return text


class MZGramClassesTest(unittest.TestCase):
    def test_only_mzgram_strings(self):
        problems = []
        for path in mzgram_files():
            lines = code_lines(path)
            raw = [line for _, line in lines]
            for i, (code, _) in enumerate(lines):
                for name in STRING_REF.findall(code):
                    if not mzgram_string(name) and not allowed(raw, i):
                        problems.append(f"{path.name}:{i + 1}: R.string.{name} is Telegram's")
        self.assertEqual([], problems)

    def test_locale_controller_only_for_strings(self):
        problems = []
        for path in mzgram_files():
            lines = code_lines(path)
            raw = [line for _, line in lines]
            for i, (code, _) in enumerate(lines):
                for member in STATIC_IMPORT.findall(code) + LOCALE_CONTROLLER_USE.findall(code):
                    if member not in LOCALE_CONTROLLER_OK and not allowed(raw, i):
                        problems.append(f"{path.name}:{i + 1}: LocaleController.{member} follows Telegram's language")
        self.assertEqual([], problems)

    def test_no_texts_written_into_the_code(self):
        problems = []
        for path in mzgram_files():
            lines = code_lines(path)
            raw = [line for _, line in lines]
            for i, (code, _) in enumerate(lines):
                if UI_CALL.search(code):
                    text = statement(lines, i)
                    after = text[UI_CALL.search(text).start():]
                    for literal in words_literals(after):
                        if not allowed(raw, i):
                            problems.append(f"{path.name}:{i + 1}: {literal} on the screen")
        self.assertEqual([], problems)

    def test_the_check_finds_what_it_looks_for(self):
        sample = 'builder.setNegativeButton(getString(R.string.Cancel), null);\nbutton.setText("Update");\n'
        self.assertEqual(["Cancel"], [n for n in STRING_REF.findall(sample) if not mzgram_string(n)])
        self.assertTrue(UI_CALL.search('button.setText("Update");'))
        self.assertEqual(['"Update"'], words_literals('button.setText("Update");'))
        self.assertEqual([], words_literals('builder.append("@").append(name).append("\\n");'))
        self.assertTrue(ALLOWED.search("// Telegram's own text: the status line is Telegram's"))


class MZGramInTelegramClassesTest(unittest.TestCase):
    def test_mzgram_dialogs_have_only_mzgram_strings(self):
        problems = []
        dialogs = 0
        for path in telegram_files():
            text = path.read_text(encoding="utf-8")
            if "R.string.MZGram" not in text:
                continue
            for match in DIALOG.finditer(text):
                names = STRING_REF.findall(match.group(0))
                if not any(name.startswith("MZGram") for name in names) or ALLOWED.search(match.group(0)):
                    continue
                dialogs += 1
                line = text.count("\n", 0, match.start()) + 1
                for name in names:
                    if not mzgram_string(name):
                        problems.append(f"{path.name}:{line}: R.string.{name} in an MZGram dialog")
        self.assertEqual([], problems)
        self.assertGreater(dialogs, 0, "MZGram's dialogs in Telegram's classes are found")

    def test_mzgram_menu_items_are_mzgram_strings(self):
        problems = []
        items = 0
        for path in telegram_files():
            lines = path.read_text(encoding="utf-8").splitlines()
            for i, line in enumerate(lines):
                if "MZGramConfig.show" not in line:
                    continue
                for j in range(i + 1, min(i + 5, len(lines))):
                    match = MENU_ITEM.search(lines[j])
                    if match:
                        items += 1
                        if not mzgram_string(match.group(1)) and not allowed(lines, j):
                            problems.append(f"{path.name}:{j + 1}: R.string.{match.group(1)} for an MZGram menu item")
                        break
        self.assertEqual([], problems)
        self.assertGreater(items, 5, "MZGram's message menu items are found")


class FeaturesOfThisClientTest(unittest.TestCase):
    # "Features of this client" is the subtitle of the MZGram row in Telegram's
    # Settings only, not a text on MZGram's own page
    def test_subtitle_only_in_settings(self):
        self.assertIn("R.string.MZGramSettingsInfo", (JAVA / "ui/SettingsActivity.java").read_text(encoding="utf-8"))
        users = [path.name for path in JAVA.rglob("*.java") if "R.string.MZGramSettingsInfo" in path.read_text(encoding="utf-8")]
        self.assertEqual(["SettingsActivity.java"], users)


if __name__ == "__main__":
    unittest.main()
