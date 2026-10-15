"""FEATURES.md: the list of what MZGram adds, kept in both repositories.

Every change to a user-facing feature updates FEATURES.md in MZGram-Android
and MZGram-Desktop in the same commit. This only checks that the file is
there, in shape, and linked from the README.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# Names of other clients never go into public texts. Kept reversed so the
# names themselves do not appear in this repository.
FORBIDDEN = re.compile("|".join(word[::-1] for word in ("uya", "oken", "yrucrem")), re.IGNORECASE)
HASH = re.compile(r"\b[0-9a-f]{9,40}\b")


class FeaturesTest(unittest.TestCase):
    def setUp(self):
        path = ROOT / "FEATURES.md"
        self.assertTrue(path.is_file(), "FEATURES.md is missing")
        self.text = path.read_text(encoding="utf-8")

    def test_has_both_platforms_and_the_comparison(self):
        for heading in ("## Android", "## Desktop", "## Android and Desktop compared"):
            self.assertRegex(self.text, rf"(?m)^{re.escape(heading)}$", heading)

    def test_every_feature_has_a_version(self):
        rows = [line for line in self.text.splitlines() if line.startswith("| ") and not line.startswith("| Feature")]
        feature_rows = [row for row in rows if row.count("|") == 4 and not row.startswith("|---")]
        self.assertGreater(len(feature_rows), 40)
        for row in feature_rows:
            since = row.strip().strip("|").split("|")[-1].strip()
            if since in ("Yes", "No") or since.startswith(("Yes", "No", "Next release", "Windows")):
                continue  # the comparison table
            self.assertRegex(since, r"^(\d+\.\d+\.\d+|Next release)$", row)

    def test_no_other_clients_and_no_commit_hashes(self):
        self.assertIsNone(FORBIDDEN.search(self.text))
        self.assertIsNone(HASH.search(self.text))

    def test_linked_from_the_readme(self):
        self.assertIn("(FEATURES.md)", (ROOT / "README.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
