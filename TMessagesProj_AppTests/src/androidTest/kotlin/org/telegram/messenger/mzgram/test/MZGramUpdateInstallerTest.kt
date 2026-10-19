package org.telegram.messenger.mzgram.test

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramUpdateChecker
import org.telegram.messenger.mzgram.MZGramUpdateInstaller
import org.telegram.tgnet.TLRPC
import org.telegram.ui.mzgram.MZGramUpdateBanner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections

// The update from the banner: the APK of a newer MZGram release is picked for
// the device, downloaded only from this repository's releases on GitHub,
// checked against SHA256SUMS and the GitHub digest, and installed. GitHub and
// the installer are fakes: nothing is downloaded or installed for real.
class MZGramUpdateInstallerTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val download = "https://github.com/dmytrokurochkin/MZGram-Android/releases/download"
    private val apkBytes = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val events: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val downloads: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val installed: MutableList<ByteArray> = Collections.synchronizedList(ArrayList())
    private var fetched = 0
    private var savedSwitch = true

    // What the fake GitHub serves: the release answer and the files by address
    private var releaseJson = ""
    private val files = HashMap<String, ByteArray>()
    private var failAfter = -1L

    private fun sha256(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun asset(name: String, url: String = "$download/v1.0.1/$name", digest: String? = null) =
        """{"name":"$name","browser_download_url":"$url","size":1,"digest":${if (digest == null) "null" else "\"$digest\""}}"""

    // A v1.0.1 release with the universal APK and its SHA256SUMS
    private fun serve(
        apkName: String = "MZGram-Android-1.0.1.apk",
        sum: String = sha256(apkBytes),
        digest: String? = "sha256:" + sha256(apkBytes),
        apkUrl: String = "$download/v1.0.1/$apkName",
        withSums: Boolean = true,
        tag: String = "v1.0.1"
    ) {
        val sums = "$sum  $apkName\n${"0".repeat(64)}  MZGram-Android-1.0.1-rotation.lineage\n".toByteArray()
        files.clear()
        files[apkUrl] = apkBytes
        files["$download/v1.0.1/SHA256SUMS"] = sums
        val assets = mutableListOf(asset(apkName, apkUrl, digest))
        if (withSums) {
            assets.add(asset("SHA256SUMS", digest = "sha256:" + sha256(sums)))
        }
        releaseJson = """{"tag_name":"$tag","draft":false,"prerelease":false,"html_url":"https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/$tag","assets":[${assets.joinToString(",")}]}"""
    }

    private val offered get() = MZGramUpdateChecker.parse(releaseJson)!!

    private val listener = object : MZGramUpdateInstaller.Listener {
        override fun onProgress(percent: Int) {
            events.add("progress")
        }

        override fun onVerifying() {
            events.add("verifying")
        }

        override fun onInstalling() {
            events.add("installing")
        }

        override fun onFailed(failure: MZGramUpdateInstaller.Failure, detail: String?) {
            events.add("failed:$failure")
        }

        override fun onFallback(url: String) {
            events.add("fallback:$url")
        }
    }

    @Before
    fun setUp() {
        savedSwitch = MZGramConfig.checkUpdates
        MZGramConfig.checkUpdates = true
        MZGramUpdateChecker.reset()
        MZGramUpdateChecker.installedCodeOverride = MZGramUpdateChecker.stableCode("v1.0.0")
        MZGramUpdateInstaller.cleanUp()
        MZGramUpdateInstaller.abisOverride = arrayOf("arm64-v8a", "armeabi-v7a")
        MZGramUpdateChecker.fetcher = MZGramUpdateChecker.Fetcher { url, _ ->
            fetched++
            if (url == MZGramUpdateInstaller.RELEASE_BY_TAG_URL + "v1.0.1") {
                MZGramUpdateChecker.Response(200, releaseJson, null)
            } else {
                MZGramUpdateChecker.Response(404, "", null)
            }
        }
        MZGramUpdateInstaller.downloader = MZGramUpdateInstaller.Downloader { url, target, offset, maxSize, progress ->
            downloads.add("$url@$offset")
            val data = files[url] ?: throw IOException("404 $url")
            assertTrue("within the size limit", data.size <= maxSize)
            FileOutputStream(target, offset > 0).use { out ->
                var done = offset
                while (done < data.size) {
                    if (failAfter in 0..done) {
                        failAfter = -1
                        throw IOException("connection lost")
                    }
                    val end = minOf(data.size.toLong(), done + 50_000)
                    out.write(data, done.toInt(), (end - done).toInt())
                    done = end
                    progress.onBytes(done, data.size.toLong())
                }
            }
        }
        MZGramUpdateInstaller.installer = MZGramUpdateInstaller.Installer { _, apk, _ ->
            installed.add(apk.readBytes())
        }
        // The fake APKs are not real ones: they read as this app's package
        MZGramUpdateInstaller.packageReader = MZGramUpdateInstaller.PackageReader { context, _ -> context.packageName }
        MZGramUpdateBanner.permission = object : MZGramUpdateBanner.Permission {
            override fun granted() = true
            override fun ask(context: android.content.Context) {
                events.add("permission")
            }
        }
        MZGramUpdateBanner.opener = MZGramUpdateBanner.Opener { _, url -> events.add("opened:$url") }
    }

    @After
    fun tearDown() {
        MZGramConfig.checkUpdates = savedSwitch
        MZGramUpdateChecker.reset()
        MZGramUpdateChecker.installedCodeOverride = -1
        MZGramUpdateChecker.fetcher = MZGramUpdateChecker.Fetcher { _, _ -> throw IOException("no network in tests") }
        MZGramUpdateInstaller.downloader = MZGramUpdateInstaller.Downloader { _, _, _, _, _ -> throw IOException("no network in tests") }
        MZGramUpdateInstaller.installer = MZGramUpdateInstaller.Installer { _, _, _ -> throw IOException("no installs in tests") }
        MZGramUpdateInstaller.packageReader = MZGramUpdateInstaller.PackageReader { context, apk -> MZGramUpdateInstaller.apkPackage(context, apk) }
        MZGramUpdateInstaller.abisOverride = null
        MZGramUpdateInstaller.cleanUp()
        MZGramUpdateBanner.permission = MZGramUpdateBanner.Permission.SYSTEM
        MZGramUpdateBanner.opener = MZGramUpdateBanner.Opener { _, _ -> }
        instrumentation.runOnMainSync { MZGramUpdateBanner.current()?.hide() }
    }

    // Starts the update and waits until it ends (installed, failed or the page)
    private fun update(): List<String> {
        events.clear()
        assertTrue("started", MZGramUpdateInstaller.start(offered, listener))
        assertTrue("ended", MZGramScreens.waitFor(30) { !MZGramUpdateInstaller.isRunning() })
        // The listener runs on the main thread: let what it was given run
        instrumentation.runOnMainSync {}
        return ArrayList(events)
    }

    private fun leftovers() = MZGramUpdateInstaller.directory().listFiles()!!.map { it.name }.sorted()

    @Test
    fun apk_isPickedForTheDeviceArchitecture() {
        fun asset(name: String) = MZGramUpdateInstaller.Asset(name, "$download/v1.2.0/$name", null, 1)
        val universal = asset("MZGram-Android-1.2.0.apk")
        val arm64 = asset("MZGram-Android-1.2.0-arm64-v8a.apk")
        val x86 = asset("MZGram-Android-1.2.0-x86_64.apk")
        val other = listOf(asset("SHA256SUMS"), asset("MZGram-Android-1.2.0-rotation.lineage"), asset("MZGram-Android-1.1.0.apk"))

        MZGramUpdateInstaller.abisOverride = arrayOf("arm64-v8a", "armeabi-v7a")
        assertEquals("the device's own", arm64.name, MZGramUpdateInstaller.chooseApk(other + listOf(universal, x86, arm64), "1.2.0")!!.name)
        assertEquals("else the universal one", universal.name, MZGramUpdateInstaller.chooseApk(other + listOf(universal, x86), "1.2.0")!!.name)
        MZGramUpdateInstaller.abisOverride = arrayOf("x86_64", "x86")
        assertEquals(x86.name, MZGramUpdateInstaller.chooseApk(other + listOf(universal, x86, arm64), "1.2.0")!!.name)
        assertNull("no APK of that version", MZGramUpdateInstaller.chooseApk(other + listOf(arm64), "1.3.0"))
        assertNull("not another architecture's", MZGramUpdateInstaller.chooseApk(listOf(arm64), "1.2.0"))
    }

    @Test
    fun onlyThisRepositoryOnGitHub_overHttps() {
        for (ok in listOf(
            "https://github.com/dmytrokurochkin/MZGram-Android/releases/download/v1.0.1/a.apk",
            "https://objects.githubusercontent.com/github-production-release-asset/1",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1",
        )) {
            assertTrue(ok, MZGramUpdateInstaller.isAllowedUrl(ok))
        }
        for (bad in listOf(
            "http://github.com/dmytrokurochkin/MZGram-Android/releases/download/v1.0.1/a.apk",
            "https://example.com/a.apk",
            "https://github.com.example.com/a.apk",
            "https://user@github.com/a.apk",
            "ftp://github.com/a.apk",
            "not an address",
        )) {
            assertFalse(bad, MZGramUpdateInstaller.isAllowedUrl(bad))
        }
        assertTrue(MZGramUpdateInstaller.isReleaseFile("$download/v1.0.1/a.apk", "v1.0.1"))
        assertFalse("another release", MZGramUpdateInstaller.isReleaseFile("$download/v1.0.0/a.apk", "v1.0.1"))
        assertFalse("another repository", MZGramUpdateInstaller.isReleaseFile("https://github.com/someone/MZGram-Android/releases/download/v1.0.1/a.apk", "v1.0.1"))
        assertFalse("not GitHub", MZGramUpdateInstaller.isReleaseFile("https://example.com/dmytrokurochkin/MZGram-Android/releases/download/v1.0.1/a.apk", "v1.0.1"))
    }

    @Test
    fun sums_areReadAsSha256sumWritesThem() {
        val hash = "ab".repeat(32)
        val sums = MZGramUpdateInstaller.parseSums("$hash  MZGram-Android-1.0.1.apk\n${"CD".repeat(32)} *other.zip\nbroken line\n")
        assertEquals(hash, sums["MZGram-Android-1.0.1.apk"])
        assertEquals("cd".repeat(32), sums["other.zip"])
        assertEquals(2, sums.size)
    }

    @Test
    fun goodApk_isInstalled() {
        serve()
        val events = update()
        assertTrue(events.toString(), events.contains("progress"))
        assertEquals(listOf("verifying", "installing"), events.filter { it == "verifying" || it == "installing" || it.startsWith("failed") || it.startsWith("fallback") })
        assertEquals("installed once", 1, installed.size)
        assertTrue("the downloaded APK itself", installed[0].contentEquals(apkBytes))
        assertEquals("the SHA256SUMS and the APK, from the release", listOf("$download/v1.0.1/SHA256SUMS@0", "$download/v1.0.1/MZGram-Android-1.0.1.apk@0"), downloads)
    }

    @Test
    fun corruptApk_isDeleted_nothingInstalled() {
        serve(sum = "0".repeat(64), digest = null)
        assertEquals("failed:CHECKSUM", update().last())
        assertTrue("nothing installed", installed.isEmpty())
        assertEquals("the file deleted", emptyList<String>(), leftovers())
    }

    // A release APK of another package (Telegram's, or MZGram's old
    // org.telegram.messenger.web) would be a separate app, not this update.
    @Test
    fun apkOfAnotherPackage_isNotInstalled() {
        serve()
        MZGramUpdateInstaller.packageReader = MZGramUpdateInstaller.PackageReader { _, _ -> "org.telegram.messenger.web" }
        assertEquals("failed:INSTALL", update().last())
        assertTrue("nothing installed", installed.isEmpty())
        assertEquals("the file deleted", emptyList<String>(), leftovers())
    }

    @Test
    fun apkPackage_isReadFromTheFile() {
        val app = instrumentation.targetContext
        val tests = instrumentation.context
        assertEquals("org.mzgram.messenger", MZGramUpdateInstaller.apkPackage(app, File(app.packageCodePath)))
        assertEquals("another app's APK", tests.packageName, MZGramUpdateInstaller.apkPackage(app, File(tests.packageCodePath)))
        val notApk = File(app.cacheDir, "mzgram-not-an-apk.apk")
        notApk.writeBytes(apkBytes)
        try {
            assertNull("not an APK", MZGramUpdateInstaller.apkPackage(app, notApk))
        } finally {
            notApk.delete()
        }
    }

    @Test
    fun githubDigestMismatch_isRejectedToo() {
        serve(digest = "sha256:" + "1".repeat(64))
        assertEquals("failed:CHECKSUM", update().last())
        assertTrue(installed.isEmpty())
    }

    @Test
    fun fileOutsideThisRepository_isNotDownloaded_thePageOpensInstead() {
        val page = "https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v1.0.1"
        serve(apkUrl = "https://github.com/someone/MZGram-Android/releases/download/v1.0.1/MZGram-Android-1.0.1.apk")
        assertEquals(listOf("fallback:$page"), update())
        serve(apkUrl = "https://example.com/MZGram-Android-1.0.1.apk")
        assertEquals(listOf("fallback:$page"), update())
        serve(withSums = false)
        assertEquals("no SHA256SUMS, no install", listOf("fallback:$page"), update())
        assertTrue("nothing downloaded", downloads.isEmpty())
        assertTrue(installed.isEmpty())
    }

    @Test
    fun releaseNotNewer_isNotInstalled() {
        serve()
        MZGramUpdateChecker.installedCodeOverride = MZGramUpdateChecker.stableCode("v1.0.1")
        assertEquals(listOf("failed:NOT_FOUND"), update())
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun brokenDownload_resumes() {
        serve()
        failAfter = 100_000
        assertEquals("failed:NETWORK", update().last())
        assertTrue(installed.isEmpty())
        assertEquals("the part is kept", listOf("MZGram-Android-1.0.1.apk.part"), leftovers())

        downloads.clear()
        update()
        assertEquals("the APK continues where it stopped", "$download/v1.0.1/MZGram-Android-1.0.1.apk@100000", downloads.last())
        assertEquals(1, installed.size)
        assertTrue(installed[0].contentEquals(apkBytes))
    }

    // The switch turns off the automatic check, not an update the user
    // starts: "Check for updates" shows the bar with the switch off too
    @Test
    fun switchOff_updateTheUserStarts_stillInstalls() {
        serve()
        MZGramConfig.checkUpdates = false
        val events = update()
        assertTrue(events.toString(), events.contains("installing"))
        assertEquals(1, installed.size)
    }

    @Test
    fun oldDownloads_areRemoved() {
        val dir = MZGramUpdateInstaller.directory()
        for (name in listOf("MZGram-Android-0.9.0.apk", "MZGram-Android-1.0.0.apk.part", "junk", "MZGram-Android-1.0.1.apk.part")) {
            File(dir, name).writeText("x")
        }
        MZGramUpdateInstaller.removeOld()
        assertEquals("only the download of a newer version stays", listOf("MZGram-Android-1.0.1.apk.part"), leftovers())
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
        return MZGramUpdateBanner.current()!!
    }

    private fun text(banner: MZGramUpdateBanner): String {
        var result = ""
        instrumentation.runOnMainSync { result = banner.text.toString() }
        return result
    }

    @Test
    fun banner_update_downloadsChecksAndInstalls() {
        val banner = banner()
        serve()
        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        assertTrue("installed", MZGramScreens.waitFor(30) { installed.size == 1 })
        assertTrue(installed[0].contentEquals(apkBytes))
        assertFalse("no page opened", events.any { it.startsWith("opened") })
        MZGramScreens.capture("update-installing")
    }

    @Test
    fun banner_corruptFile_saysSo_andOffersRetryAndThePage() {
        val banner = banner()
        serve(sum = "0".repeat(64), digest = null)
        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        assertTrue("failed", MZGramScreens.waitFor(30) { banner.isFailed })
        assertEquals(View.VISIBLE, banner.visibility)
        assertTrue(text(banner), text(banner).contains("SHA-256"))
        MZGramScreens.capture("update-failed")
        assertTrue("nothing installed", installed.isEmpty())

        // Retry, with a good file now
        serve()
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        assertTrue("installed on retry", MZGramScreens.waitFor(30) { installed.size == 1 })

        // Failed again: the release page is there too
        serve(sum = "0".repeat(64), digest = null)
        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        assertTrue(MZGramScreens.waitFor(30) { banner.isFailed })
        instrumentation.runOnMainSync { banner.pageButton.performClick() }
        assertTrue(events.contains("opened:https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v1.0.1"))
        assertEquals(View.GONE, banner.visibility)
    }

    @Test
    fun banner_noApk_opensTheReleasePage() {
        val banner = banner()
        serve(withSums = false)
        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        assertTrue(MZGramScreens.waitFor(30) { events.contains("opened:https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v1.0.1") })
        assertEquals("hidden", View.GONE, banner.visibility)
        assertTrue(installed.isEmpty())
    }

    @Test
    fun banner_withoutTheInstallPermission_asksForIt_first() {
        val banner = banner()
        serve()
        MZGramUpdateBanner.permission = object : MZGramUpdateBanner.Permission {
            override fun granted() = false
            override fun ask(context: android.content.Context) {
                events.add("permission")
            }
        }
        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.updateButton.performClick() }
        Thread.sleep(500)
        assertEquals(listOf("permission"), ArrayList(events))
        assertTrue("nothing downloaded before the permission", downloads.isEmpty())
        assertNotNull("the banner stays", banner.release)
    }

    @Test
    fun banner_close_hidesThatVersion() {
        val banner = banner()
        serve()
        instrumentation.runOnMainSync { banner.show(offered) }
        instrumentation.runOnMainSync { banner.closeButton.performClick() }
        assertEquals(View.GONE, banner.visibility)
        assertTrue("Close starts nothing", downloads.isEmpty() && events.isEmpty())
    }
}
