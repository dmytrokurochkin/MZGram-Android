import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from mzgram_version import NO_CHANGES, VersionError, code, dev, install, notes, previous, release  # noqa: E402

SCRIPT = Path(__file__).parent / "mzgram_version.py"


class CodeTest(unittest.TestCase):
    def test_stable_and_beta_codes(self):
        self.assertEqual(code("v1.2.0"), 1020099)
        self.assertEqual(code("v1.3.0-beta.2"), 1030002)
        self.assertEqual(code("v12.99.99"), 12999999)

    def test_betas_sit_between_releases(self):
        order = ["v1.2.0", "v1.3.0-beta.1", "v1.3.0-beta.98", "v1.3.0", "v1.3.1-beta.1", "v1.3.1", "v2.0.0"]
        self.assertEqual(order, sorted(order, key=code))

    def test_bad_tags(self):
        for tag in ["1.2.0", "v1.2", "v1.100.0", "v1.2.100", "v01.2.0", "v1.2.0-beta.99", "v1.2.0-beta.0", "v1.2.0-rc.1", "v210.0.0"]:
            with self.assertRaises(VersionError, msg=tag):
                code(tag)

    def test_final_code_fits_android(self):
        self.assertLessEqual(code("v209.99.99") * 10 + 9, 2100000000)


class ReleaseTest(unittest.TestCase):
    def test_release_outputs(self):
        out = release("v1.0.0", "12.10.1", 7038, ["v1.0.0"])
        self.assertEqual(out, {"version": "1.0.0", "code": "1000099", "name": "1.0.0 (12.10.1)", "prerelease": "false"})
        self.assertEqual(release("v1.1.0-beta.1", "12.10.1", 7038, [])["prerelease"], "true")

    def test_never_below_the_current_code(self):
        # 7038 * 10 + 9 is what the builds before the first tag carry
        with self.assertRaises(VersionError):
            release("v0.0.1", "12.10.1", 7038, [])
        self.assertEqual(release("v0.1.0", "12.10.1", 7038, [])["code"], "10099")

    def test_never_below_an_existing_tag(self):
        with self.assertRaises(VersionError):
            release("v1.0.1", "12.10.1", 7038, ["v1.1.0", "v1.0.1"])
        with self.assertRaises(VersionError):
            release("v1.1.0-beta.1", "12.10.1", 7038, ["v1.1.0"])
        self.assertEqual(release("v1.1.1", "12.10.1", 7038, ["v1.1.0", "release-12.10.1"])["code"], "1010199")


class DevTest(unittest.TestCase):
    def test_no_tags_keeps_the_gradle_code(self):
        self.assertEqual(dev("12.10.1", 7038, ["release-12.10.1"]), {"code": "7038", "name": ""})

    def test_dev_build_equals_the_newest_tag(self):
        out = dev("12.10.1", 7038, ["v1.0.0", "v1.1.0-beta.2", "v0.9.0"])
        self.assertEqual(out, {"code": "1010002", "name": "1.1.0-beta.2-dev (12.10.1)"})


class PreviousTest(unittest.TestCase):
    TAGS = ["v1.0.0", "v1.1.0-beta.1", "v1.1.0-beta.2", "v1.1.0", "v1.2.0-beta.1"]

    def test_stable_since_last_stable(self):
        self.assertEqual(previous("v1.1.0", self.TAGS), "v1.0.0")

    def test_beta_since_last_tag(self):
        self.assertEqual(previous("v1.1.0-beta.2", self.TAGS), "v1.1.0-beta.1")
        self.assertEqual(previous("v1.2.0-beta.1", self.TAGS), "v1.1.0")

    def test_first_release(self):
        self.assertEqual(previous("v1.0.0", self.TAGS), "")


class NotesTest(unittest.TestCase):
    def test_internal_commits_are_left_out(self):
        subjects = [
            "[mzgram] CI: stop the build daemons before the emulator starts",
            "[mzgram] Test: the color theme row follows the list",
            "[mzgram] Release workflow: a dry run can sign with a throwaway key",
            "[mzgram] Docs: versions, signing key setup and releases",
            "[mzgram] Spec: I3",
            "[mzgram] Color theme list: keep the whole list while the chat themes load again",
            "[mzgram] Hide camera tile: the gallery reads the switch",
            "Merge upstream 12.10.1",
            "update to 12.10.1 (7038)",
        ]
        self.assertEqual(notes(subjects), [
            "- Color theme list: keep the whole list while the chat themes load again",
            "- Hide camera tile: the gallery reads the switch",
        ])

    def test_build_ci_and_test_work_under_other_subjects_is_left_out(self):
        subjects = [
            "[mzgram] Release notes: leave out the CI, Test, Release workflow, Docs and Spec commits",
            "[mzgram] Fix Windows CI: msys2 PATH shadows timeout.exe after pacman self-update",
            "[mzgram] Fix broken Windows CI: msys2 pacman database sync race",
            "[mzgram] Topic pages test: read the settings source with CRLF line ends",
            "[mzgram] Build Linux x64 in CI",
            "[mzgram] Add Windows x64 and ARM64 build workflow",
            "[mzgram] Install ATL so the Release breakpad build links",
            "[mzgram] Build Release dependencies and target Qt6",
            "[mzgram] Route Qt's zlib dependency to the zlib tdesktop builds on Windows",
            "[mzgram] Cancel superseded Windows runs",
            "[mzgram] Add headless unit tests for MZGram Zalgo filter",
            "[mzgram] README about MZGram",
            "[mzgram] Link the Windows DNS API, which Qt Network needs for the update check",
            "[mzgram] Describe the fork and how it tracks upstream",
            "[mzgram] Remove mentions of other Telegram clients from comments",
            "[mzgram] Pinned chats: past the server's limit, pin in MZGram only, up to ten in all",
            "[mzgram] Rename the built binary itself from Telegram to MZGram",
        ]
        self.assertEqual(notes(subjects), [
            "- Pinned chats: past the server's limit, pin in MZGram only, up to ten in all",
            "- Rename the built binary itself from Telegram to MZGram",
        ])

    def test_a_word_that_only_starts_like_a_prefix_stays(self):
        self.assertEqual(notes(["[mzgram] Testing mode for the archive", "[mzgram] CIrcle videos: keep the sound"]),
                         ["- Testing mode for the archive", "- CIrcle videos: keep the sound"])

    def test_lines_naming_other_clients_are_left_out(self):
        # The names are written reversed, as in the script
        first, second, third = ("UYA", "oken", "yrucreM")
        subjects = [
            f"[mzgram] Archive deleted messages via single {first[::-1]}GRAM-aligned path",
            f"[mzgram] Port the {second[::-1]}gram helper",
            f"[mzgram] {first[::-1].title()} style switch",
            f"[mzgram] From {third[::-1]}Gram: read receipts",
            f"[mzgram] {third[::-1]} icons",
            "[mzgram] Keep the archive across restarts",
        ]
        self.assertEqual(notes(subjects), ["- Keep the archive across restarts"])

    def test_features_md_lines_are_left_out(self):
        subjects = [
            "[mzgram] FEATURES.md: self-update on Desktop, same file as in MZGram-Desktop",
            "[mzgram] English and Ukrainian: a check of every string, FEATURES.md names the languages",
            "[mzgram] Accounts in the chat list menu",
        ]
        self.assertEqual(notes(subjects), ["- Accounts in the chat list menu"])

    def test_string_and_manifest_work_is_left_out(self):
        subjects = [
            "[mzgram] Escape apostrophes in the new strings, no long dashes, manifest test for own authorities and permissions",
            "[mzgram] MZGram in 12 languages; test switch removed",
            "[mzgram] Notification diagnostics, test push",
        ]
        self.assertEqual(notes(subjects), ["- MZGram in 12 languages; test switch removed", "- Notification diagnostics, test push"])

    def test_commit_hashes_are_removed(self):
        self.assertEqual(notes(["[mzgram] Revert own-deletion archiving (0b331573e)",
                                "[mzgram] Hide statuses on both platforms (1caf86c27, ec74fd73e8)",
                                "[mzgram] Keep (beta) in titles"]),
                         ["- Revert own-deletion archiving", "- Hide statuses on both platforms", "- Keep (beta) in titles"])

    def test_nothing_left_gives_one_line(self):
        self.assertEqual(notes(["[mzgram] CI: x", "[mzgram] Test: y", "upstream commit"]), [NO_CHANGES])
        self.assertEqual(notes([]), [NO_CHANGES])

    # A commit can read as a change users see when it is not one (a fix of
    # something no release had): the release then gives its own list.
    def test_a_release_can_give_its_own_list(self):
        subjects = ["[mzgram] Telegram 13.0: MZGram's strings found again"]
        own = ["- Based on Telegram 13.0.3", "", "Original quality on media3", "  "]
        self.assertEqual(notes(subjects, own), ["- Based on Telegram 13.0.3", "- Original quality on media3"])
        self.assertEqual(notes(subjects, []), ["- Telegram 13.0: MZGram's strings found again"])
        self.assertEqual(notes(subjects, ["", " "]), ["- Telegram 13.0: MZGram's strings found again"])


class InstallTest(unittest.TestCase):
    CERT = "0b6801b8606f1d2dd6d7511600d834cf6cf5d3791f78601b216a49c3777f1560"

    # Since 1.0.4 MZGram has its own package (org.mzgram.messenger): a
    # release installs over 1.0.4 and newer, not over the older builds,
    # which had another package and are a separate app.
    def test_signed_release_installs_over_the_own_package(self):
        text = chr(10).join(install("1.0.5", True, self.CERT))
        self.assertIn("Download `MZGram-Android-1.0.5.apk` and open it.", text)
        self.assertIn("It installs over MZGram 1.0.4 and newer and keeps all data.", text)
        self.assertIn("Signing certificate SHA-256: `%s`" % self.CERT, text)
        self.assertNotIn("earlier MZGram builds", text)
        self.assertNotIn("moves the app to the MZGram signing key", text)

    def test_debug_key_build_is_not_for_installing_over(self):
        text = chr(10).join(install("1.0.5", False, ""))
        self.assertIn("public development key", text)
        self.assertNotIn("keeps all data", text)

    def test_cli(self):
        result = subprocess.run([sys.executable, str(SCRIPT), "install", "1.0.5", "--signed", "--cert", self.CERT], capture_output=True, text=True)
        self.assertIn("It installs over MZGram 1.0.4 and newer and keeps all data.", result.stdout)


class CliTest(unittest.TestCase):
    def run_cli(self, *args, stdin=""):
        return subprocess.run([sys.executable, str(SCRIPT), *args], input=stdin, capture_output=True, text=True)

    def test_ls_remote_lines(self):
        stdin = "abc\trefs/tags/v1.0.0\nabc\trefs/tags/v1.0.0^{}\ndef\trefs/tags/release-12.10.1\n"
        result = self.run_cli("dev", "--upstream", "12.10.1", "--floor", "7038", stdin=stdin)
        self.assertEqual(result.stdout.split(), ["code=1000099", "name=1.0.0-dev", "(12.10.1)"])

    def test_notes_from_stdin(self):
        result = self.run_cli("notes", stdin="[mzgram] CI: x\n[mzgram] Feature: y\n")
        self.assertEqual(result.stdout.strip(), "- Feature: y")

    def test_notes_own_list_from_a_file(self):
        with tempfile.TemporaryDirectory() as folder:
            own = Path(folder) / "1.0.7.changes.md"
            missing = Path(folder) / "1.0.8.changes.md"
            own.write_text("- Links stay with Telegram Desktop\n", encoding="utf-8")
            result = self.run_cli("notes", "--own", str(own), stdin="[mzgram] Feature: y\n")
            self.assertEqual(result.stdout.strip(), "- Links stay with Telegram Desktop")
            result = self.run_cli("notes", "--own", str(missing), stdin="[mzgram] Feature: y\n")
            self.assertEqual(result.stdout.strip(), "- Feature: y")

    def test_bad_tag_fails(self):
        result = self.run_cli("release", "v1.2", "--upstream", "12.10.1", "--floor", "7038")
        self.assertEqual(result.returncode, 1)
        self.assertIn("::error::", result.stdout)


if __name__ == "__main__":
    unittest.main()
