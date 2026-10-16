"""MZGram's package and manifests: nothing that clashes with another Telegram app.

MZGram has its own package, org.mzgram.messenger: with Telegram's
(org.telegram.messenger from Google Play, org.telegram.messenger.web from
telegram.org) Android takes MZGram for an update of that Telegram and refuses
it. Android also refuses an app whose content provider authority or declared
permission is already taken by another app, an app with the same
sharedUserId shares that app's data, and only one app may own an account
type. So every authority and permission MZGram declares is under its own
package (${applicationId}), the contacts account type is the package too,
there is no sharedUserId, and the launcher icon is not Telegram's.
"""

import hashlib
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OWN = "${applicationId}"
PACKAGE = "org.mzgram.messenger"
TELEGRAM_PACKAGES = {"org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta"}
# The modules that build MZGram: the release APK and the APK the tests run on
MODULES = ["TMessagesProj_AppStandalone", "TMessagesProj_AppTests"]
RES = ROOT / "TMessagesProj/src/main/res"
# Telegram's launcher icon (ic_launcher_sa.png in each density) and the
# colours of its adaptive background
TELEGRAM_ICON_SHA256 = {
    "18f849a88754603d5811a0a9c36b14088dbaf9835ff5180d0a0b2f87ce6fcbf2",
    "5733898e671d5e0f52fc0a1e9bcbf03e65eef620910561a11f074db17a75faa6",
    "0fb8cfa934dfdd6f86cf9208c62e6b06ea86b2bfa25224c3685db6c6fa6a9a14",
    "6d5f79f5d45cb0377315b7c4bd8b03618bb95a4c8716458f44cddfc87b7e911b",
    "73db468d936f7852ff87c9af45c60215d60baf9aa1a9790d6253a1f5eb691f9b",
}
TELEGRAM_ICON_COLOURS = {"#289AD1", "#38AEEB"}


def manifests():
    found = list(ROOT.glob("*/src/*/AndroidManifest.xml")) + list(ROOT.glob("TMessagesProj/config/**/AndroidManifest*.xml"))
    return [path for path in found if "build" not in path.relative_to(ROOT).parts]


def properties():
    result = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def application_ids(module):
    """The applicationId of each build type of a module, as Gradle makes it."""
    text = (ROOT / module / "build.gradle").read_text(encoding="utf-8").replace("\r\n", "\n")
    base = re.search(r"defaultConfig\.applicationId\s*=\s*(\w+)", text).group(1)
    base = properties().get(base, base)
    build_types = text[text.index("buildTypes {"):]
    result = {}
    for name in ("debug", "standalone"):
        block = re.search(r"\n        %s \{(.*?)\n        \}" % name, build_types, re.S)
        suffix = re.search(r'applicationIdSuffix\s+"([^"]*)"', block.group(1)) if block else None
        result[name] = base + (suffix.group(1) if suffix else "")
    return result


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

    def test_the_config_manifests_are_checked(self):
        self.assertIn("TMessagesProj/config/release/AndroidManifest.xml", self.files)
        self.assertIn("TMessagesProj/config/release/AndroidManifest_standalone.xml", self.files)

    def test_no_shared_user_id(self):
        for name, text in self.files.items():
            self.assertNotIn("android:sharedUserId", text, name)

    def test_the_package_is_mzgram_s_own(self):
        for module in MODULES:
            for build_type, application_id in application_ids(module).items():
                where = "%s %s" % (module, build_type)
                self.assertNotIn(application_id, TELEGRAM_PACKAGES, where + ": Telegram's package")
                self.assertEqual(PACKAGE, application_id, where)

    def test_the_contacts_account_type_is_the_package(self):
        # Only one app owns an account type: with Telegram's, contact sync
        # in MZGram fails beside Telegram.
        xml = RES / "xml"
        for name, attribute in (("auth.xml", "accountType"), ("sync_contacts.xml", "accountType"), ("auth_menu.xml", "targetPackage")):
            values = re.findall(r'android:%s="([^"]+)"' % attribute, (xml / name).read_text(encoding="utf-8"))
            self.assertEqual(["@string/mzgram_account_type"], values, name)
        contacts = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/ContactsController.java").read_text(encoding="utf-8")
        self.assertNotIn('"org.telegram.messenger"', contacts)
        for module in MODULES:
            text = (ROOT / module / "build.gradle").read_text(encoding="utf-8")
            self.assertRegex(text, r'resValue\s+"string",\s*"mzgram_account_type",\s*MZGRAM_PACKAGE', module)

    def test_the_launcher_icon_is_not_telegram_s(self):
        res = ROOT / "TMessagesProj_AppStandalone/src/main/res"
        icons = sorted(res.glob("mipmap-*/ic_launcher_sa.png"))
        self.assertEqual(5, len(icons))
        for icon in icons:
            self.assertNotIn(hashlib.sha256(icon.read_bytes()).hexdigest(), TELEGRAM_ICON_SHA256, icon.parent.name)
        background = (res / "drawable/icon_background_sa.xml").read_text(encoding="utf-8").upper()
        for colour in TELEGRAM_ICON_COLOURS:
            self.assertNotIn(colour, background)

    def test_the_built_apk_is_checked_for_the_package(self):
        for workflow in ("mzgram-android.yml", "mzgram-android-release.yml"):
            text = (ROOT / ".github/workflows" / workflow).read_text(encoding="utf-8")
            self.assertTrue("name='%s'" % PACKAGE in text, workflow + ": the APK's package is not checked")
            self.assertFalse("name='org.telegram.messenger.web'" in text, workflow + ": checks Telegram's package")
        for script in ("mzgram_push_cold.sh", "mzgram_appearance_restart.sh", "mzgram_rotation_check.sh"):
            text = (ROOT / ".github/scripts" / script).read_text(encoding="utf-8")
            self.assertNotIn("org.telegram.messenger.web", text, script)


if __name__ == "__main__":
    unittest.main()
