"""MZGram's manifests: nothing that clashes with another Telegram app.

Android refuses to install an app whose content provider authority or
declared permission is already taken by another app, and an app with the
same sharedUserId shares that app's data. So every authority and every
permission MZGram declares is under its own package (${applicationId}), and
there is no sharedUserId.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OWN = "${applicationId}"


def manifests():
    return [path for path in ROOT.glob("*/src/*/AndroidManifest.xml") if "build" not in path.parts]


def attributes(text, tag, name):
    result = []
    for element in re.findall(r"<%s\b[^>]*>" % tag, re.sub(r"<!--.*?-->", "", text, flags=re.S), re.S):
        match = re.search(r'%s="([^"]+)"' % name, element)
        if match:
            result.append(match.group(1))
    return result


class ManifestTest(unittest.TestCase):
    def setUp(self):
        self.files = {path.relative_to(ROOT).as_posix(): path.read_text(encoding="utf-8") for path in manifests()}

    def test_the_main_manifest_is_checked(self):
        self.assertIn("TMessagesProj/src/main/AndroidManifest.xml", self.files)

    def test_every_authority_is_under_the_own_package(self):
        found = 0
        for name, text in self.files.items():
            for value in attributes(text, "provider", "android:authorities"):
                found += 1
                for authority in value.split(";"):
                    self.assertTrue(authority.startswith(OWN + "."), name + ": " + authority)
        self.assertGreater(found, 0)

    def test_every_declared_permission_is_under_the_own_package(self):
        for name, text in self.files.items():
            for tag in ("permission", "permission-group", "permission-tree"):
                for value in attributes(text, tag, "android:name"):
                    self.assertTrue(value.startswith(OWN + "."), name + ": " + value)

    def test_no_shared_user_id(self):
        for name, text in self.files.items():
            self.assertNotIn("android:sharedUserId", text, name)


if __name__ == "__main__":
    unittest.main()
