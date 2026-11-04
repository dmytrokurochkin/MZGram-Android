"""The archive's three levels, Show ID and Reliable delivery, as wired.

Archive: every message from someone else is kept as it comes (level 1, the
received table), its file in advance up to the limit of its kind of chat
(level 2), the file when deleted up to 100 MB more (level 3); a deletion
this device had no copy of counts as deleted without content; a failed
file reference counts as "link expired".

Show ID: a row of the profile, hidden by default.

Reliable delivery: a new foreground service of the specialUse type (not
Telegram's Keep-Alive, which has no notification), off by default, one
switch for every account, started by the switch, at the process start,
after a reboot or an update and by a watch job; its notification has no
buttons and opens the switch; no MIUI or Xiaomi in its texts.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "TMessagesProj/src/main"
JAVA = MAIN / "java/org/telegram"
MZ = JAVA / "messenger/mzgram"


def read(path):
    return path.read_text(encoding="utf-8") if path.is_file() else ""


class ArchiveLevelsTest(unittest.TestCase):
    def test_limits(self):
        limits = read(MZ / "MZGramArchiveLimits.java")
        self.assertIn("DEFAULT_MB = {300, 5, 20, 5}", limits)
        self.assertIn("DELETION_EXTRA_MB = 100", limits)
        self.assertIn("NO_LIMIT", limits)

    def test_levels_are_wired(self):
        controller = read(MZ / "MZGramHistoryController.java")
        self.assertIn("receive(accountId, messages);", controller)
        self.assertIn("db.putReceived(", controller)
        self.assertIn("MZGramArchiveLimits.fetches(fileSize(message), limit, false)", controller)
        self.assertIn("MZGramArchiveLimits.fetches(fileSize(message), limit, true)", controller)
        self.assertIn("MZGramArchiveLimits.count(MZGramArchiveLimits.DELETED_WITHOUT_CONTENT)", controller)
        self.assertIn("db.deleteReceived(accountUserId, dialogId, message.id);", controller)
        storage = read(JAVA / "messenger/MessagesStorage.java")
        self.assertIn("MZGramHistoryController.getInstance().onDeletedNotInCache(currentAccount, dialogId,", storage)
        refs = read(JAVA / "messenger/FileRefController.java")
        self.assertIn("MZGramArchiveLimits.noteExpired(fileLoadOperation.getFileName());", refs)
        database = read(MZ / "MZGramHistoryDatabase.java")
        self.assertIn("DB_VERSION = 5", database)
        self.assertIn('DELETE FROM " + TABLE_RECEIVED', database)

    def test_settings_and_diagnostics(self):
        page = read(JAVA / "ui/mzgram/MZGramSettingsActivity.java")
        for name in ("R.string.MZGramArchiveLimits", "R.string.MZGramArchiveCounters", "BUTTON_ARCHIVE_COUNTERS_RESET", "countersText()"):
            self.assertIn(name, page)
        push = read(JAVA / "ui/mzgram/MZGramUnifiedPushActivity.java")
        self.assertIn("MZGramArchiveLimits.DELETED_WITHOUT_CONTENT", push)


class PeerIdTest(unittest.TestCase):
    def test_profile_row(self):
        profile = read(JAVA / "ui/ProfileActivity.java")
        self.assertGreaterEqual(profile.count("mzgramIdRow = rowCount++;"), 2)
        self.assertIn("position == mzgramIdRow) {", profile)
        self.assertIn("AndroidUtilities.addToClipboard(id);", profile)
        self.assertIn("put(++pointer, mzgramIdRow, sparseIntArray);", profile)
        config = read(MZ / "MZGramConfig.java")
        self.assertIn('preferences.getInt("peerIdMode", MZGramPeerId.HIDDEN)', config)


class ReliableTest(unittest.TestCase):
    def test_a_foreground_service_of_its_own(self):
        manifest = read(MAIN / "AndroidManifest.xml")
        self.assertIn("android.permission.FOREGROUND_SERVICE_SPECIAL_USE", manifest)
        service = manifest[manifest.index('android:name="org.telegram.messenger.mzgram.MZGramReliableService"'):]
        service = service[:service.index("</service>")]
        self.assertIn('android:foregroundServiceType="specialUse"', service)
        self.assertIn("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", service)
        self.assertIn('android:name="org.telegram.messenger.mzgram.MZGramReliableWatch"', manifest)
        self.assertIn("android.intent.action.MY_PACKAGE_REPLACED", manifest)
        code = read(MZ / "MZGramReliableService.java")
        self.assertIn("ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE", code)
        self.assertIn("startForegroundService(intent)", code)
        self.assertIn('MZGramReliableJournal.event("start refused', code)
        self.assertNotIn("addAction(", code)
        self.assertIn("NotificationManager.IMPORTANCE_LOW", code)
        self.assertIn('SETTINGS_LINK = "tg://settings/mzgram_reliable"', code)
        self.assertNotIn("NotificationsService", re.sub(r"//.*|/\*.*?\*/", "", code, flags=re.S))

    def test_started_where_it_should(self):
        self.assertIn("MZGramReliableService.REASON_BOOT", read(JAVA / "messenger/AppStartReceiver.java"))
        self.assertIn("MZGramReliableService.REASON_UPDATE", read(JAVA / "messenger/AppStartReceiver.java"))
        self.assertIn("MZGramReliableService.REASON_PROCESS", read(JAVA / "messenger/ApplicationLoader.java"))
        self.assertIn("MZGramReliableService.REASON_SWITCH", read(JAVA / "ui/NotificationsSettingsActivity.java"))
        launch = read(JAVA / "ui/LaunchActivity.java")
        self.assertIn('url.contains("mzgram_reliable")', launch)
        self.assertIn("open_settings == 40", launch)

    def test_one_switch_off_by_default(self):
        config = read(MZ / "MZGramConfig.java")
        self.assertIn("public static boolean reliableDelivery = false;", config)
        self.assertIn('preferences.getBoolean("reliableDelivery", false)', config)
        page = read(JAVA / "ui/NotificationsSettingsActivity.java")
        self.assertIn("MZGramConfig.setReliableDelivery(!enabled);", page)

    def test_no_miui_in_its_texts(self):
        for folder in sorted((MAIN / "res").glob("values*")):
            strings = read(folder / "mzgram_strings.xml")
            if not strings:
                continue
            for name in ("MZGramReliable", "MZGramReliableInfo", "MZGramReliableNotification", "MZGramReliableSuggest"):
                match = re.search(r'<string name="%s">(.*?)</string>' % name, strings)
                self.assertIsNotNone(match, (folder.name, name))
                self.assertNotRegex(match.group(1), r"(?i)miui|xiaomi", (folder.name, name))

    def test_journal_in_the_push_log(self):
        check = read(MZ / "MZGramDeviceCheck.java")
        self.assertIn("MZGramReliableJournal.log(", check)
        journal = read(MZ / "MZGramReliableJournal.java")
        for word in ("pushString", "token", "p256dh", "auth"):
            self.assertNotIn(word, re.sub(r"//.*|/\*.*?\*/", "", journal, flags=re.S))


if __name__ == "__main__":
    unittest.main()
