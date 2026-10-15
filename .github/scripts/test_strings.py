"""MZGram's own strings: English and Ukrainian, complete in both.

MZGram has exactly two interface languages. Every MZGram string is in
values/mzgram_strings.xml (English, also shown for every other language) and
in values-uk/mzgram_strings.xml (Ukrainian), so a screen is never part English,
part Ukrainian.
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
# The same in both languages: names and terms that are not translated
SHARED = {"MZGram", "ID", "UnifiedPush"}


def strings(path):
    return dict(STRING.findall(path.read_text(encoding="utf-8")))


class StringsTest(unittest.TestCase):
    def setUp(self):
        self.en = strings(RES / "values/mzgram_strings.xml")
        self.uk = strings(RES / "values-uk/mzgram_strings.xml")

    def test_only_english_and_ukrainian(self):
        folders = sorted(path.parent.name for path in RES.glob("values*/mzgram_strings.xml"))
        self.assertEqual(["values", "values-uk"], folders)

    def test_every_key_in_both(self):
        self.assertGreater(len(self.en), 100)
        self.assertEqual([], sorted(set(self.en) - set(self.uk)), "missing in Ukrainian")
        self.assertEqual([], sorted(set(self.uk) - set(self.en)), "missing in English")

    def test_ukrainian_is_a_translation(self):
        for key, value in self.uk.items():
            self.assertTrue(value.strip(), key)
            if value in SHARED:
                continue
            self.assertNotEqual(self.en[key], value, key + ": an English copy")
            self.assertRegex(value, CYRILLIC, key + ": not Ukrainian")

    def test_english_has_no_cyrillic(self):
        for key, value in self.en.items():
            self.assertIsNone(CYRILLIC.search(value), key)

    def test_same_placeholders(self):
        for key, value in self.en.items():
            self.assertEqual(sorted(PLACEHOLDER.findall(value)), sorted(PLACEHOLDER.findall(self.uk[key])), key)

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

    def test_features_names_the_languages(self):
        features = (ROOT / "FEATURES.md").read_text(encoding="utf-8")
        self.assertTrue("MZGram interface is available in English and Ukrainian" in features, "FEATURES.md does not name the languages")


if __name__ == "__main__":
    unittest.main()
