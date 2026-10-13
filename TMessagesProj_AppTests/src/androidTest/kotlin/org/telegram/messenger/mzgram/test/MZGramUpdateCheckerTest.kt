package org.telegram.messenger.mzgram.test

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramUpdateChecker
import org.telegram.tgnet.TLRPC
import org.telegram.ui.mzgram.MZGramUpdateBanner
import java.io.IOException

// The MZGram update check: the latest stable GitHub release against the
// installed version, the banner, closing it, the switch, and that
// Telegram's own update check no longer runs. GitHub is replaced by a fake.
class MZGramUpdateCheckerTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val requests = ArrayList<String?>()
    private var answer: () -> MZGramUpdateChecker.Response = { MZGramUpdateChecker.Response(404, "", null) }
    private var savedSwitch = true
    private var now = 1_800_000_000_000L

    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false, url: String? = null) =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"html_url":"${url ?: "https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/$tag"}"}"""

    private fun ok(body: String, etag: String = "\"e1\"") = MZGramUpdateChecker.Response(200, body, etag)

    @Before
    fun setUp() {
        savedSwitch = MZGramConfig.checkUpdates
        MZGramConfig.checkUpdates = true
        MZGramUpdateChecker.reset()
        MZGramUpdateChecker.installedCodeOverride = MZGramUpdateChecker.stableCode("v1.0.0")
        MZGramUpdateChecker.nowOverride = now
        MZGramUpdateChecker.fetcher = MZGramUpdateChecker.Fetcher { _, etag ->
            requests.add(etag)
            answer()
        }
    }

    @After
    fun tearDown() {
        MZGramConfig.checkUpdates = savedSwitch
        MZGramUpdateChecker.reset()
        MZGramUpdateChecker.installedCodeOverride = -1
        MZGramUpdateChecker.nowOverride = -1
        MZGramUpdateChecker.fetcher = MZGramUpdateChecker.Fetcher { _, _ -> throw IOException("no network in tests") }
        MZGramUpdateBanner.opener = MZGramUpdateBanner.Opener { _, _ -> }
        instrumentation.runOnMainSync { MZGramUpdateBanner.current()?.hide() }
    }

    private fun later() {
        now += MZGramUpdateChecker.CHECK_INTERVAL_MS + 1
        MZGramUpdateChecker.nowOverride = now
    }

    @Test
    fun versions_compareByTheReleaseCode() {
        assertEquals(1000099L, MZGramUpdateChecker.stableCode("v1.0.0"))
        assertEquals(1020399L, MZGramUpdateChecker.stableCode("v1.2.3"))
        assertTrue(MZGramUpdateChecker.stableCode("v1.10.0") > MZGramUpdateChecker.stableCode("v1.9.99"))
        assertEquals("a beta is not offered", -1L, MZGramUpdateChecker.stableCode("v1.1.0-beta.1"))
        for (tag in listOf("1.0.0", "v1.0", "v1.100.0", "release-12.10.1", "", "v01.0.0")) {
            assertEquals(tag, -1L, MZGramUpdateChecker.stableCode(tag))
        }
    }

    @Test
    fun newerStableRelease_isOffered_sameOrOlderIsNot() {
        answer = { ok(release("v1.0.1")) }
        val offered = MZGramUpdateChecker.checkNow(false)
        assertNotNull(offered)
        assertEquals("1.0.1", offered!!.version())
        assertEquals("https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v1.0.1", offered.url)

        later()
        answer = { ok(release("v1.0.0"), "\"e2\"") }
        assertNull("the installed version", MZGramUpdateChecker.checkNow(false))
        later()
        answer = { ok(release("v0.9.0"), "\"e3\"") }
        assertNull("an older version", MZGramUpdateChecker.checkNow(false))
    }

    @Test
    fun draftPrereleaseAndBeta_areIgnored() {
        assertNull(MZGramUpdateChecker.parse(release("v2.0.0", draft = true)))
        assertNull(MZGramUpdateChecker.parse(release("v2.0.0", prerelease = true)))
        assertNull(MZGramUpdateChecker.parse(release("v2.0.0-beta.1")))
        assertNull(MZGramUpdateChecker.parse("not json"))
        // A page link outside the MZGram releases is not followed
        assertEquals("https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v2.0.0",
            MZGramUpdateChecker.parse(release("v2.0.0", url = "https://example.com/x"))!!.url)
        answer = { ok(release("v2.0.0", prerelease = true)) }
        assertNull(MZGramUpdateChecker.checkNow(false))
    }

    @Test
    fun noReleasesAndErrors_areSilent() {
        answer = { MZGramUpdateChecker.Response(404, """{"message":"Not Found"}""", null) }
        assertNull("no release published yet", MZGramUpdateChecker.checkNow(false))
        later()
        answer = { MZGramUpdateChecker.Response(500, "", null) }
        assertNull("server error", MZGramUpdateChecker.checkNow(false))
        later()
        answer = { throw IOException("offline") }
        assertNull("no network", MZGramUpdateChecker.checkNow(false))
        later()
        answer = { MZGramUpdateChecker.Response(403, """{"message":"API rate limit exceeded"}""", null) }
        assertNull("rate limit", MZGramUpdateChecker.checkNow(false))
        assertEquals("every answer was asked for", 4, requests.size)
    }

    @Test
    fun checks_atMostEveryFewHours_withTheETag() {
        answer = { ok(release("v1.0.1"), "\"abc\"") }
        assertNotNull(MZGramUpdateChecker.checkNow(false))
        assertNotNull("the saved answer within the interval", MZGramUpdateChecker.checkNow(false))
        assertEquals("one request within the interval", 1, requests.size)

        later()
        answer = { MZGramUpdateChecker.Response(304, "", "\"abc\"") }
        val again = MZGramUpdateChecker.checkNow(false)
        assertEquals("the ETag of the last answer sent", listOf(null, "\"abc\""), requests)
        assertEquals("304 keeps the saved release", "v1.0.1", again?.tag)
    }

    @Test
    fun switchOff_asksNothing() {
        MZGramConfig.checkUpdates = false
        answer = { ok(release("v9.0.0")) }
        assertNull(MZGramUpdateChecker.checkNow(false))
        assertTrue("no request with the switch off", requests.isEmpty())
    }

    @Test
    fun closedVersion_isNotOfferedAgain_aNewerOneIs() {
        answer = { ok(release("v1.0.1")) }
        val offered = MZGramUpdateChecker.checkNow(false)!!
        MZGramUpdateChecker.dismiss(offered)
        assertNull("closed for v1.0.1", MZGramUpdateChecker.checkNow(false))
        later()
        answer = { ok(release("v1.0.2"), "\"e2\"") }
        assertEquals("v1.0.2", MZGramUpdateChecker.checkNow(false)?.tag)
    }

    private fun banner(): MZGramUpdateBanner {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramUpdateBanner.current() != null })
        // The app checks on its own when it comes to the front: let that check end
        Thread.sleep(1500)
        instrumentation.runOnMainSync { MZGramUpdateBanner.current()!!.hide() }
        requests.clear()
        return MZGramUpdateBanner.current()!!
    }

    // Telegram's check is replaced: a saved Telegram update is dropped, and
    // the check asks GitHub; a newer release shows the banner.
    @Test
    fun appUpdateCheck_isMZGrams_andShowsTheBanner() {
        val banner = banner()
        val telegramUpdate = TLRPC.TL_help_appUpdate()
        telegramUpdate.version = "99.0"
        telegramUpdate.can_not_skip = true
        SharedConfig.pendingAppUpdate = telegramUpdate
        answer = { ok(release("v1.0.1")) }
        later()
        instrumentation.runOnMainSync { MZGramUpdateBanner.checkFromApp(false) }
        assertTrue("banner shown", MZGramScreens.waitFor(15) { banner.visibility == View.VISIBLE })
        assertNull("Telegram's saved update dropped", SharedConfig.pendingAppUpdate)
        assertEquals("GitHub asked once", 1, requests.size)
        Thread.sleep(500)
        MZGramScreens.capture("update-banner")
        assertEquals("v1.0.1", banner.release?.tag)
    }

    @Test
    fun banner_update_opensTheReleasePage_close_hidesThatVersion() {
        val banner = banner()
        val opened = ArrayList<String>()
        MZGramUpdateBanner.opener = MZGramUpdateBanner.Opener { _, url -> opened.add(url) }
        answer = { ok(release("v1.0.1")) }
        // Past the check the app made on its own when it opened
        later()
        val offered = MZGramUpdateChecker.checkNow(false)!!

        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        assertEquals(listOf("https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v1.0.1"), opened)
        assertEquals("hidden after Update", View.GONE, banner.visibility)
        assertNotNull("Update does not close the version for good", MZGramUpdateChecker.checkNow(false))

        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.closeButton.performClick() }
        assertEquals("hidden after Close", View.GONE, banner.visibility)
        assertNull("closed for this version", MZGramUpdateChecker.checkNow(false))
        assertEquals("Close opens nothing", 1, opened.size)
    }
}
