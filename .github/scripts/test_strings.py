"""MZGram's own strings: complete in each of its twelve languages.

Every MZGram string is in values/mzgram_strings.xml (English, also shown for
any language MZGram does not have) and in values-<language>/mzgram_strings.xml
for the other eleven, so a screen is never part in one language, part in
another. The MZGram language is chosen apart from Telegram's
(MZGramLanguage).
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "TMessagesProj/src/main/res"
JAVA = ROOT / "TMessagesProj/src/main/java"
STRING = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")
CYRILLIC = re.compile("[Ѐ-ӿ]")
ARABIC = re.compile("[؀-ۿ]")
# The resource folder of each language besides English, as MZGramLanguage names them
LANGUAGES = {
    "uk": "values-uk", "ru": "values-ru", "pl": "values-pl", "es": "values-es",
    "de": "values-de", "fr": "values-fr", "pt-BR": "values-pt-rBR", "tr": "values-tr",
    "ar": "values-ar", "be": "values-be", "kk": "values-kk",
}
LATIN = {"pl", "es", "de", "fr", "pt-BR", "tr"}
# Letters that tell each language from the others, in the language as a whole
OWN_LETTERS = {
    "uk": "[іїєґ]", "ru": "[ыэъ]", "be": "[ў]", "kk": "[әғқңөұүһ]",
    "pl": "[ąęłśżźćń]", "de": "[äöüß]", "fr": "[éèàçê]", "es": "[ñáéíóú]",
    "pt-BR": "[ãõçáéê]", "tr": "[ığşçöü]", "ar": "[؀-ۿ]",
}
# Letters a string of that language never has
FOREIGN_LETTERS = {
    "uk": "[ыэъёЫЭЪЁўЎ]",
    "ru": "[іїєґІЇЄҐўЎ]",
    "be": "[иИщЩъЪїЇєЄґҐ]",
    "kk": "[їЇєЄґҐўЎ]",
}
# Words a string may share with English: names, technical terms and words
# that are the same in some of the languages (French "Message", "Date",
# German "Details"). A string made only of them may equal the English one.
SHARED_WORDS = {
    "MZGram", "ID", "UnifiedPush", "DC", "MIME", "FCM", "Google", "GitHub", "SHA",
    "VAPID", "Bot", "Miami", "Amsterdam", "Singapore", "Zalgo", "Unicode",
    "Telegram", "Interface", "Media", "Details", "Message", "Date", "OK",
}


def strings(path):
    return dict(STRING.findall(path.read_text(encoding="utf-8")))


def shared(text):
    words = re.findall(r"[^\W\d_]+", text)
    return all(word in SHARED_WORDS for word in words)


class StringsTest(unittest.TestCase):
    def setUp(self):
        self.en = strings(RES / "values/mzgram_strings.xml")
        self.other = {code: strings(RES / folder / "mzgram_strings.xml") for code, folder in LANGUAGES.items()}

    def test_exactly_the_twelve_languages(self):
        folders = sorted(path.parent.name for path in RES.glob("values*/mzgram_strings.xml"))
        self.assertEqual(sorted(["values"] + list(LANGUAGES.values())), folders)

    def test_every_key_in_every_language(self):
        self.assertGreater(len(self.en), 100)
        for code, values in self.other.items():
            self.assertEqual([], sorted(set(self.en) - set(values)), "missing in " + code)
            self.assertEqual([], sorted(set(values) - set(self.en)), "not in English: " + code)

    def test_each_is_a_translation(self):
        for code, values in self.other.items():
            for key, value in values.items():
                self.assertTrue(value.strip(), code + " " + key)
                if value == self.en[key]:
                    self.assertTrue(shared(value), code + " " + key + ": an English copy")

    def test_the_script_and_letters_of_each_language(self):
        for key, value in self.en.items():
            self.assertIsNone(CYRILLIC.search(value), "en " + key)
        for code, values in self.other.items():
            for key, value in values.items():
                if code in LATIN or code == "ar":
                    self.assertIsNone(CYRILLIC.search(value), code + " " + key + ": Cyrillic")
                if code in FOREIGN_LETTERS:
                    self.assertIsNone(re.search(FOREIGN_LETTERS[code], value), code + " " + key + ": letters of another language")
                if code in ("uk", "ru", "be", "kk") and not shared(value):
                    self.assertRegex(value, CYRILLIC, code + " " + key + ": not Cyrillic")
                if code == "ar" and not shared(value):
                    self.assertRegex(value, ARABIC, code + " " + key + ": not Arabic")
            self.assertRegex("".join(values.values()), OWN_LETTERS[code], code + ": not that language")

    def test_apostrophes_escaped_and_no_long_dashes(self):
        # aapt refuses a bare apostrophe and fails the whole build
        for code, values in [("en", self.en)] + list(self.other.items()):
            for key, value in values.items():
                self.assertIsNone(re.search(r"(?<!\\)'", value), code + " " + key + ": apostrophe without \\")
                self.assertNotIn("—", value, code + " " + key + ": long dash")

    def test_same_placeholders(self):
        for code, values in self.other.items():
            for key, value in self.en.items():
                self.assertEqual(sorted(PLACEHOLDER.findall(value)), sorted(PLACEHOLDER.findall(values[key])), code + " " + key)

    def test_mzgram_keys_only_in_the_mzgram_files(self):
        for path in RES.glob("values*/*.xml"):
            if path.name == "mzgram_strings.xml":
                continue
            for key in strings(path):
                self.assertFalse(key.startswith("MZGram"), "%s in %s" % (key, path.relative_to(RES)))

    def test_every_key_used_in_the_code_exists(self):
        used = set()
        for path in JAVA.rglob("*.java"):
            used.update(re.findall(r"R\.string\.(MZGram\w*)", path.read_text(encoding="utf-8", errors="ignore")))
        self.assertEqual([], sorted(used - set(self.en)))

    def test_the_languages_are_in_every_app_build(self):
        # localeFilters drops every other resource language from the APK; one
        # list, MZGRAM_LOCALES in gradle.properties, serves every app build
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        match = re.search(r"^MZGRAM_LOCALES=(.+)$", properties, re.M)
        self.assertIsNotNone(match, "no MZGRAM_LOCALES in gradle.properties")
        kept = set(match.group(1).strip().split(","))
        for folder in LANGUAGES.values():
            self.assertIn(folder[len("values-"):], kept)
        for build in ROOT.glob("TMessagesProj_App*/build.gradle"):
            text = build.read_text(encoding="utf-8")
            self.assertNotRegex(text, r"localeFilters \+= \[", build.parent.name + " has a list of its own")
            self.assertIn('localeFilters += MZGRAM_LOCALES.split(",").toList()', text, build.parent.name)

    def test_features_names_the_languages(self):
        features = (ROOT / "FEATURES.md").read_text(encoding="utf-8")
        self.assertIn("MZGram interface is available in 12 languages", features, "FEATURES.md does not name the languages")


if __name__ == "__main__":
    unittest.main()
