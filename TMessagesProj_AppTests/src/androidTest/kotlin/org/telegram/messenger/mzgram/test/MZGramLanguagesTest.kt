package org.telegram.messenger.mzgram.test

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramLanguage
import org.telegram.messenger.mzgram.MZGramUpdateChecker
import org.telegram.messenger.mzgram.MZGramUpdateInstaller
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.mzgram.MZGramSettingsActivity
import org.telegram.ui.mzgram.MZGramUnifiedPushActivity
import org.telegram.ui.mzgram.MZGramUpdateBanner
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// MZGram in each of its twelve languages, chosen as the user does it (Settings
// > MZGram > Interface), with Telegram itself in English:
//
// - Update on the update bar starts the download in every language, tapped
//   as a finger does it, and every text of the bar (the offer, Update, Close,
//   the download, the check, the installation, a failure, Retry, Release
//   page) and of the dialog asking for the install permission (Cancel too) is
//   in the chosen language;
// - every MZGram screen (the topics, each topic's page, notifications through
//   UnifiedPush) and every dialog of them shows no English text when another
//   language is chosen (names such as MZGram, GitHub, SHA-256 aside), and
//   none of Telegram's strings, which are in Telegram's language;
// - Settings > MZGram ends its topics without "Features of this client": that
//   is only the subtitle of the MZGram row in Telegram's Settings.
//
// Screenshots in Ukrainian, English and Arabic: mzgram-lang-<code>-*.png.
class MZGramLanguagesTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val download = "https://github.com/dmytrokurochkin/MZGram-Android/releases/download"
    private val apkBytes = ByteArray(200_000) { (it * 17 % 251).toByte() }
    private val downloads: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val installed: MutableList<ByteArray> = Collections.synchronizedList(ArrayList())
    private val files = HashMap<String, ByteArray>()
    private var releaseJson = ""
    private var latestJson = ""
    private var apkGate: CountDownLatch? = null
    private var savedLanguage = ""
    private var savedSwitch = true
    private val screenshotLanguages = setOf("uk", "en", "ar")

    // Names and terms that stay as they are in every language
    private val names = setOf(
        "MZGram", "Telegram", "GitHub", "SHA", "UnifiedPush", "FCM", "Google", "Play", "Services", "microG",
        "ntfy", "Sunup", "VAPID", "WebPush", "Android", "URL", "HTTP", "HTTPS", "MIME", "DC", "ID", "QR", "OK",
    ) + MZGramLanguage.NAMES + org.telegram.messenger.mzgram.MZGramAiRules.providers().map { it.name }

    private fun sha256(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun asset(name: String, digest: String?) =
        """{"name":"$name","browser_download_url":"$download/v1.0.1/$name","size":1,"digest":${if (digest == null) "null" else "\"$digest\""}}"""

    // A v1.0.1 release with the universal APK and its SHA256SUMS; a wrong sum fails the check
    private fun serve(rightSum: Boolean) {
        val apkName = "MZGram-Android-1.0.1.apk"
        val sums = "${if (rightSum) sha256(apkBytes) else "0".repeat(64)}  $apkName\n".toByteArray()
        files.clear()
        files["$download/v1.0.1/$apkName"] = apkBytes
        files["$download/v1.0.1/SHA256SUMS"] = sums
        releaseJson = """{"tag_name":"v1.0.1","draft":false,"prerelease":false,"html_url":"https://github.com/dmytrokurochkin/MZGram-Android/releases/tag/v1.0.1","assets":[${asset(apkName, null)},${asset("SHA256SUMS", null)}]}"""
        latestJson = releaseJson
    }

    private val offered get() = MZGramUpdateChecker.parse(releaseJson)!!

    @Before
    fun setUp() {
        savedLanguage = MZGramConfig.language
        savedSwitch = MZGramConfig.checkUpdates
        MZGramConfig.checkUpdates = true
        MZGramUpdateChecker.reset()
        MZGramUpdateChecker.installedCodeOverride = MZGramUpdateChecker.stableCode("v1.0.0")
        MZGramUpdateInstaller.cleanUp()
        MZGramUpdateInstaller.abisOverride = arrayOf("arm64-v8a")
        serve(true)
        MZGramUpdateChecker.fetcher = MZGramUpdateChecker.Fetcher { url, _ ->
            when (url) {
                MZGramUpdateInstaller.RELEASE_BY_TAG_URL + "v1.0.1" -> MZGramUpdateChecker.Response(200, releaseJson, null)
                MZGramUpdateChecker.LATEST_RELEASE_URL -> if (latestJson.isEmpty()) throw IOException("offline") else MZGramUpdateChecker.Response(200, latestJson, null)
                else -> MZGramUpdateChecker.Response(404, "", null)
            }
        }
        MZGramUpdateInstaller.downloader = MZGramUpdateInstaller.Downloader { url, target, offset, _, progress ->
            downloads.add(url)
            if (url.endsWith(".apk")) {
                apkGate?.await(30, TimeUnit.SECONDS)
            }
            val data = files[url] ?: throw IOException("404 $url")
            FileOutputStream(target, offset > 0).use { it.write(data) }
            progress.onBytes(data.size.toLong(), data.size.toLong())
        }
        MZGramUpdateInstaller.installer = MZGramUpdateInstaller.Installer { _, apk, _ -> installed.add(apk.readBytes()) }
        MZGramUpdateInstaller.packageReader = MZGramUpdateInstaller.PackageReader { ctx, _ -> ctx.packageName }
        MZGramUpdateBanner.permission = object : MZGramUpdateBanner.Permission {
            override fun granted() = true
            override fun ask(context: Context) {}
        }
        MZGramUpdateBanner.opener = MZGramUpdateBanner.Opener { _, _ -> }
        val ui = instrumentation.uiAutomation
        val info = ui.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        ui.serviceInfo = info
    }

    @After
    fun tearDown() {
        apkGate?.countDown()
        MZGramScreens.waitFor(30) { !MZGramUpdateInstaller.isRunning() }
        instrumentation.runOnMainSync { MZGramUpdateBanner.current()?.hide() }
        MZGramConfig.setLanguage(savedLanguage)
        MZGramConfig.checkUpdates = savedSwitch
        MZGramUpdateChecker.reset()
        MZGramUpdateChecker.installedCodeOverride = -1
        MZGramUpdateChecker.fetcher = MZGramUpdateChecker.Fetcher { _, _ -> throw IOException("no network in tests") }
        MZGramUpdateInstaller.downloader = MZGramUpdateInstaller.Downloader { _, _, _, _, _ -> throw IOException("no network in tests") }
        MZGramUpdateInstaller.installer = MZGramUpdateInstaller.Installer { _, _, _ -> throw IOException("no installs in tests") }
        MZGramUpdateInstaller.packageReader = MZGramUpdateInstaller.PackageReader { ctx, apk -> MZGramUpdateInstaller.apkPackage(ctx, apk) }
        MZGramUpdateInstaller.abisOverride = null
        MZGramUpdateInstaller.cleanUp()
        MZGramUpdateBanner.permission = MZGramUpdateBanner.Permission.SYSTEM
        MZGramUpdateBanner.opener = MZGramUpdateBanner.Opener { _, _ -> }
    }

    // --- The strings of a language, as values-xx has them ---

    private fun resourcesIn(code: String): android.content.res.Resources {
        val config = Configuration(context.resources.configuration)
        config.setLocale(code.split("-").let { if (it.size == 2) Locale(it[0], it[1]) else Locale(it[0]) })
        return context.createConfigurationContext(config).resources
    }

    // An MZGram string in a language; null when there is no such MZGram string
    private fun own(code: String, name: String, vararg args: Any): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) return null
        val text = resourcesIn(code).getString(id)
        return if (args.isEmpty()) text else String.format(Locale.US, text, *args)
    }

    private val mzgramIds by lazy {
        R.string::class.java.fields.filter { it.name.startsWith("MZGram") }.map { it.getInt(null) }
    }

    private val ownCache = HashMap<String, Set<String>>()

    private fun ownTexts(code: String) = ownCache.getOrPut(code) {
        val res = resourcesIn(code)
        mzgramIds.map { res.getString(it) }.toSet()
    }

    // Every text the app has in English: Telegram's and MZGram's
    private val englishTexts by lazy {
        val res = resourcesIn("en")
        R.string::class.java.fields.mapNotNull {
            try { res.getString(it.getInt(null)).trim() } catch (e: Exception) { null }
        }.filter { it.length >= 2 }.toSet()
    }

    // MZGram's English texts with arguments, as patterns
    private val englishPatterns by lazy {
        val res = resourcesIn("en")
        mzgramIds.map { res.getString(it) }.filter { withWords(it) }.map { pattern(it) }
    }

    // A text with arguments and words of its own ("%1$s" alone would match anything)
    private fun withWords(format: String) =
        format.contains("%") && Regex("[^\\W\\d_]{3,}").containsMatchIn(format.replace(Regex("%(\\d+\\$)?[sd]"), ""))

    private fun pattern(format: String): Regex {
        val parts = format.split(Regex("%(\\d+\\$)?[sd]"))
        return Regex(parts.joinToString(".+?") { Regex.escape(it.replace("%%", "%")) }, RegexOption.DOT_MATCHES_ALL)
    }

    private val nonLatin = setOf("uk", "ru", "be", "kk", "ar")

    // The texts of a screen that are English (or Telegram's) with another language chosen
    private fun english(code: String, texts: Collection<String>, where: String): List<String> {
        if (code == "en") return emptyList()
        val own = ownTexts(code)
        val ownWords = own.flatMap { Regex("[A-Za-z]{3,}").findAll(it).map { m -> m.value }.toList() }.toSet()
        val ownPatterns = own.filter { withWords(it) }.map { pattern(it) }
        val problems = ArrayList<String>()
        for (raw in texts) {
            val text = raw.trim()
            if (text.isEmpty() || text in own || text in names) continue
            val ownFormatted = ownPatterns.any { it.matches(text) }
            if (text in englishTexts || (!ownFormatted && englishPatterns.any { it.matches(text) })) {
                problems.add("$where: \"$text\" is English")
                continue
            }
            if (code in nonLatin) {
                // Addresses and file names aside, a Latin word must be one the translations have
                val words = Regex("[A-Za-z]{3,}").findAll(text.replace(Regex("\\S*[./:@]\\S*"), " ")).map { it.value }
                val foreign = words.filter { it !in ownWords && it !in names }.toList()
                if (foreign.isNotEmpty()) {
                    problems.add("$where: \"$text\" has English words $foreign")
                }
            }
        }
        return problems
    }

    // --- What is on the screen ---

    // The texts of the window in front (a dialog when one is open), as the
    // screen shows them
    private fun windowTexts(): List<String> {
        val result = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
        }
        walk(instrumentation.uiAutomation.rootInActiveWindow)
        return result
    }

    private fun waitForWindow(expected: String?): List<String> {
        var texts = windowTexts()
        val end = System.currentTimeMillis() + 8000
        while (expected != null && texts.none { it.contains(expected) } && System.currentTimeMillis() < end) {
            Thread.sleep(250)
            texts = windowTexts()
        }
        return texts
    }

    private fun itemsOf(page: BaseFragment): List<UItem> {
        val items = ArrayList<UItem>()
        instrumentation.runOnMainSync {
            val fill = page.javaClass.getDeclaredMethod("fillItems", ArrayList::class.java, UniversalAdapter::class.java)
            fill.isAccessible = true
            fill.invoke(page, items, null)
        }
        return items
    }

    private fun itemTexts(items: List<UItem>) =
        items.flatMap { listOfNotNull(it.text, it.subtext, it.textValue) }.map { it.toString() }

    private fun click(page: BaseFragment, buttonField: String) {
        val id = MZGramSettingsActivity::class.java.getDeclaredField(buttonField).also { it.isAccessible = true }.getInt(null)
        instrumentation.runOnMainSync {
            val onClick = page.javaClass.getDeclaredMethod("onClick", UItem::class.java, View::class.java, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            onClick.isAccessible = true
            onClick.invoke(page, UItem.asButton(id, "x"), null, 0, 0f, 0f)
        }
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        Thread.sleep(600)
    }

    private fun startApp() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramUpdateBanner.current() != null && MZGramScreens.lastFragment() != null })
        // The app checks on its own when it comes to the front: let that check end
        Thread.sleep(1500)
        instrumentation.runOnMainSync { MZGramUpdateBanner.current()!!.hide() }
    }

    private fun openPage(page: BaseFragment): Boolean {
        MZGramScreens.open(page)
        val shown = MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() === page && page.fragmentView?.isShown == true }
        Thread.sleep(800)
        return shown
    }

    private fun closePagesToTheChats() {
        for (i in 0 until 4) {
            if (MZGramScreens.lastFragment() !is MZGramSettingsActivity && MZGramScreens.lastFragment() !is MZGramUnifiedPushActivity) break
            instrumentation.runOnMainSync { MZGramScreens.lastFragment()?.finishFragment(false) }
            Thread.sleep(500)
        }
    }

    // The language chosen on Settings > MZGram > Interface, as the user does it
    private fun chooseLanguage(code: String) {
        val page = MZGramSettingsActivity(MZGramSettingsActivity.SECTION_INTERFACE)
        assertTrue("$code: Interface opened", openPage(page))
        instrumentation.runOnMainSync { page.setLanguage(code) }
        Thread.sleep(600)
        if (code in screenshotLanguages) {
            MZGramScreens.capture("lang-$code-interface")
        }
        closePagesToTheChats()
    }

    private fun bannerTexts(banner: MZGramUpdateBanner): Map<String, String> {
        val result = HashMap<String, String>()
        instrumentation.runOnMainSync {
            result["text"] = banner.text.toString()
            result["update"] = (banner.updateButton as TextView).text.toString()
            result["close"] = (banner.closeButton as TextView).text.toString()
            result["page"] = (banner.pageButton as TextView).text.toString()
        }
        return result
    }

    // --- The tests ---

    // The bug: after choosing another MZGram language, Update did nothing and
    // the bar kept texts of the language it started in. For each language:
    // the bar in that language, a real tap on Update starts the download, the
    // download and the installation are said in that language, and so is a
    // failed check with Retry and Release page.
    @Test
    fun everyLanguage_updateStartsTheDownload_andTheBarIsInThatLanguage() {
        startApp()
        val banner = MZGramUpdateBanner.current()!!
        val problems = ArrayList<String>()
        val version = "1.0.1"
        for (code in MZGramLanguage.LANGUAGES) {
            chooseLanguage(code)
            serve(true)
            downloads.clear()
            installed.clear()
            instrumentation.runOnMainSync { banner.show(offered) }
            Thread.sleep(400)
            var texts = bannerTexts(banner)
            for ((key, name) in listOf("text" to "MZGramUpdateAvailable", "update" to "MZGramUpdateOpen", "close" to "MZGramUpdateClose")) {
                val expected = if (key == "text") own(code, name, version) else own(code, name)
                if (texts[key] != expected) problems.add("$code offer $key: \"${texts[key]}\", expected \"$expected\"")
            }
            if (code in screenshotLanguages) MZGramScreens.capture("lang-$code-update-bar")

            apkGate = CountDownLatch(1)
            MZGramAppearance.tap(banner.updateButton)
            val started = MZGramScreens.waitFor(15) { downloads.any { it.endsWith(".apk") } }
            if (!started) {
                problems.add("$code: Update did not start the download (downloads $downloads)")
                apkGate?.countDown()
                MZGramScreens.waitFor(30) { !MZGramUpdateInstaller.isRunning() }
                continue
            }
            Thread.sleep(300)
            texts = bannerTexts(banner)
            val downloading = own(code, "MZGramUpdateDownloading", version, 0)
            if (texts["text"] != downloading) problems.add("$code downloading: \"${texts["text"]}\", expected \"$downloading\"")
            if (code in screenshotLanguages) MZGramScreens.capture("lang-$code-update-downloading")
            apkGate?.countDown()
            if (!MZGramScreens.waitFor(30) { installed.size == 1 }) problems.add("$code: not installed")
            MZGramScreens.waitFor(30) { !MZGramUpdateInstaller.isRunning() }
            instrumentation.runOnMainSync {}
            texts = bannerTexts(banner)
            val installing = own(code, "MZGramUpdateInstalling", version)
            if (texts["text"] != installing) problems.add("$code installing: \"${texts["text"]}\", expected \"$installing\"")
            apkGate = null

            // A file that fails the SHA-256 check
            serve(false)
            MZGramUpdateInstaller.cleanUp()
            instrumentation.runOnMainSync { banner.show(offered) }
            MZGramAppearance.tap(banner.updateButton)
            if (!MZGramScreens.waitFor(30) { banner.isFailed }) {
                problems.add("$code: the failed check is not shown")
            } else {
                texts = bannerTexts(banner)
                val expected = mapOf(
                    "text" to own(code, "MZGramUpdateFailedChecksum", version),
                    "update" to own(code, "MZGramUpdateRetry"),
                    "close" to own(code, "MZGramUpdateClose"),
                    "page" to own(code, "MZGramUpdatePage"),
                )
                for ((key, value) in expected) {
                    if (texts[key] != value) problems.add("$code failed $key: \"${texts[key]}\", expected \"$value\"")
                }
                if (code in screenshotLanguages) MZGramScreens.capture("lang-$code-update-failed")
            }
            MZGramScreens.waitFor(30) { !MZGramUpdateInstaller.isRunning() }
            instrumentation.runOnMainSync { banner.hide() }
        }
        MZGramScreens.log("update bar in ${MZGramLanguage.LANGUAGES.size} languages: ${problems.size} problems")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // Update asks for Android's install permission first: that dialog, its
    // Cancel too, is in the chosen language, not in Telegram's.
    @Test
    fun everyLanguage_installPermissionDialog_isInThatLanguage() {
        startApp()
        val banner = MZGramUpdateBanner.current()!!
        MZGramUpdateBanner.permission = object : MZGramUpdateBanner.Permission {
            override fun granted() = false
            override fun ask(context: Context) = MZGramUpdateBanner.Permission.SYSTEM.ask(context)
        }
        val problems = ArrayList<String>()
        for (code in MZGramLanguage.LANGUAGES) {
            chooseLanguage(code)
            instrumentation.runOnMainSync { banner.show(offered) }
            Thread.sleep(400)
            MZGramAppearance.tap(banner.updateButton)
            val title = own(code, "MZGramUpdatePermissionTitle")
            val texts = waitForWindow(title)
            if (code in screenshotLanguages) MZGramScreens.capture("lang-$code-install-permission")
            for (name in listOf("MZGramUpdatePermissionTitle", "MZGramUpdatePermission", "MZGramUpdatePermissionOpen", "MZGramCancel")) {
                val expected = own(code, name)
                if (expected == null) {
                    problems.add("$code: no MZGram string $name (the dialog shows ${texts.joinToString(" | ")})")
                } else if (texts.none { it == expected }) {
                    problems.add("$code: \"$expected\" ($name) not in the dialog: ${texts.joinToString(" | ")}")
                }
            }
            problems.addAll(english(code, texts, "$code install permission"))
            assertTrue("$code: nothing started without the permission", downloads.isEmpty())
            back()
            instrumentation.runOnMainSync { banner.hide() }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // "Check for updates" says what it found in the chosen language.
    @Test
    fun everyLanguage_checkForUpdates_saysItInThatLanguage() {
        val problems = ArrayList<String>()
        for (code in MZGramLanguage.LANGUAGES) {
            MZGramConfig.setLanguage(code)
            for ((json, expected) in listOf(
                releaseJson to own(code, "MZGramUpdateCheckAvailable", "1.0.1"),
                "" to own(code, "MZGramUpdateCheckFailed"),
            )) {
                latestJson = json
                MZGramUpdateChecker.reset()
                MZGramUpdateBanner.lastCheckText = null
                MZGramUpdateBanner.checkByHand(null, null)
                MZGramScreens.waitFor(15) { MZGramUpdateBanner.lastCheckText != null }
                if (MZGramUpdateBanner.lastCheckText != expected) problems.add("$code: \"${MZGramUpdateBanner.lastCheckText}\", expected \"$expected\"")
            }
            instrumentation.runOnMainSync { MZGramUpdateBanner.current()?.hide() }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // With the switch of the automatic check off, "Check for updates" still
    // shows the bar: its Update must work then too (it did nothing).
    @Test
    fun switchOff_updateFromCheckForUpdates_works() {
        startApp()
        val banner = MZGramUpdateBanner.current()!!
        MZGramConfig.checkUpdates = false
        MZGramUpdateBanner.checkByHand(null, null)
        assertTrue("the bar is shown", MZGramScreens.waitFor(15) { banner.release != null && banner.visibility == View.VISIBLE })
        MZGramAppearance.tap(banner.updateButton)
        assertTrue("Update started the download: $downloads", MZGramScreens.waitFor(15) { downloads.any { it.endsWith(".apk") } })
        assertTrue("installed", MZGramScreens.waitFor(30) { installed.size == 1 })
    }

    // Every MZGram screen and its dialogs, in every language: no English text
    // when another language is chosen, and none of Telegram's.
    @Test
    fun everyLanguage_everyScreenAndDialog_hasNoEnglish() {
        startApp()
        val problems = ArrayList<String>()
        val dialogs = mapOf(
            MZGramSettingsActivity.SECTION_INTERFACE to listOf("BUTTON_LANGUAGE" to "MZGramLanguage"),
            MZGramSettingsActivity.SECTION_ARCHIVE to listOf(
                "BUTTON_WIPE_ARCHIVE" to "MZGramWipeArchiveConfirm",
                "BUTTON_IMPORT_ARCHIVE" to "MZGramImportArchiveConfirm",
                "BUTTON_ERASE_LOCAL_DATABASE" to "MZGramEraseLocalDatabaseConfirm",
                "BUTTON_DELETED_MARK" to "MZGramDeletedMarkText",
            ),
        )
        for (code in MZGramLanguage.LANGUAGES) {
            MZGramConfig.setLanguage(code)
            val shot = code in screenshotLanguages
            val main = MZGramSettingsActivity()
            if (!openPage(main)) {
                problems.add("$code: Settings > MZGram did not open")
                continue
            }
            if (shot) MZGramScreens.capture("lang-$code-main")
            val mainItems = itemsOf(main)
            val featuresInfo = own(code, "MZGramSettingsInfo")
            if (itemTexts(mainItems).any { it == featuresInfo }) problems.add("$code: \"$featuresInfo\" on the MZGram page")
            problems.addAll(english(code, itemTexts(mainItems) + windowTexts(), "$code main"))
            for (section in 0 until MZGramSettingsActivity.SECTIONS_COUNT) {
                val page = MZGramSettingsActivity(section)
                if (!openPage(page)) {
                    problems.add("$code: section $section did not open")
                    continue
                }
                if (shot) MZGramScreens.capture("lang-$code-section-$section")
                problems.addAll(english(code, itemTexts(itemsOf(page)) + windowTexts(), "$code section $section"))
                for ((button, expected) in dialogs[section].orEmpty()) {
                    click(page, button)
                    val texts = waitForWindow(own(code, expected))
                    if (shot) MZGramScreens.capture("lang-$code-section-$section-${button.lowercase()}")
                    if (texts.none { it.contains(own(code, expected) ?: "?") }) problems.add("$code $button: the dialog did not show (${texts.joinToString(" | ")})")
                    problems.addAll(english(code, texts, "$code $button dialog"))
                    // Back may first hide the keyboard of a text field
                    for (i in 0 until 3) {
                        back()
                        if (windowTexts().none { it.contains(own(code, expected) ?: "?") }) break
                    }
                }
                instrumentation.runOnMainSync { page.finishFragment(false) }
                Thread.sleep(400)
            }
            instrumentation.runOnMainSync { main.finishFragment(false) }
            Thread.sleep(400)
            val push = MZGramUnifiedPushActivity()
            if (openPage(push)) {
                if (shot) MZGramScreens.capture("lang-$code-unifiedpush")
                problems.addAll(english(code, itemTexts(itemsOf(push)) + windowTexts(), "$code UnifiedPush"))
                instrumentation.runOnMainSync { push.finishFragment(false) }
                Thread.sleep(400)
            } else {
                problems.add("$code: UnifiedPush did not open")
            }
        }
        MZGramScreens.log("screens in ${MZGramLanguage.LANGUAGES.size} languages: ${problems.size} problems")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // The check itself finds English and Telegram's texts.
    @Test
    fun theCheck_findsEnglish() {
        assertTrue(english("uk", listOf("Cancel"), "sample").isNotEmpty())
        assertTrue(english("pl", listOf("Close"), "sample").isNotEmpty())
        assertTrue(english("de", listOf("Downloading MZGram 1.0.1: 0%"), "sample").isNotEmpty())
        assertEquals(emptyList<String>(), english("uk", listOf(own("uk", "MZGramUpdateClose")!!, "MZGram", "Українська", "GitHub"), "sample"))
        assertEquals(emptyList<String>(), english("de", listOf(own("de", "MZGramUpdateDownloading", "1.0.1", 5)!!), "sample"))
    }
}
