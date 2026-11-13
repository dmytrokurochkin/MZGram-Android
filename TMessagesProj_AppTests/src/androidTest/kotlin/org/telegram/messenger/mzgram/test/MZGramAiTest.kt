package org.telegram.messenger.mzgram.test

import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramAiRules
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramLanguage
import java.util.Locale

// "Ask AI": the five sites, the text that goes into the site's input field,
// the script that puts it there, where the window may go, the default site
// and the texts in the twelve languages. The window itself:
// MZGramAiWindowTest.
class MZGramAiTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preferences() = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)

    @Test
    fun providers_areTheFiveSites() {
        val providers = MZGramAiRules.providers()
        assertEquals(listOf("gemini", "claude", "chatgpt", "deepseek", "grok"), providers.map { it.key })
        assertEquals(listOf("Gemini", "Claude", "ChatGPT", "DeepSeek", "Grok"), providers.map { it.name })
        for (provider in providers) {
            assertTrue(provider.startUrl, provider.startUrl.startsWith("https://"))
            val host = android.net.Uri.parse(provider.startUrl).host!!
            assertTrue("${provider.key}: $host is its own", provider.hosts.any { host == it || host.endsWith(".$it") })
            assertTrue("${provider.key}: an input field to look for", provider.inputSelectors.isNotEmpty())
            assertTrue(MZGramAiRules.provider(provider.key) === provider)
        }
        assertNull(MZGramAiRules.provider("bard"))
        assertNull(MZGramAiRules.provider(null))
        assertEquals("gemini", MZGramAiRules.providerOrDefault("bard").key)
        assertEquals("grok", MZGramAiRules.providerOrDefault("grok").key)
    }

    // Settings > MZGram > AI > Default AI: kept across restarts, and a value
    // that is not one of the five sites means Gemini.
    @Test
    fun defaultProvider_isKept() {
        val saved = preferences().getString("aiProvider", null)
        try {
            preferences().edit().remove("aiProvider").commit()
            MZGramConfig.loadConfig(true)
            assertEquals("gemini", MZGramAiRules.defaultProvider().key)

            MZGramAiRules.setDefaultProvider("deepseek")
            assertEquals("deepseek", preferences().getString("aiProvider", null))
            MZGramConfig.loadConfig(true)
            assertEquals("deepseek", MZGramAiRules.defaultProvider().key)

            MZGramAiRules.setDefaultProvider("bard")
            assertEquals("gemini", preferences().getString("aiProvider", null))

            preferences().edit().putString("aiProvider", "bard").commit()
            MZGramConfig.loadConfig(true)
            assertEquals("gemini", MZGramAiRules.defaultProvider().key)
        } finally {
            preferences().edit().apply { if (saved == null) remove("aiProvider") else putString("aiProvider", saved) }.commit()
            MZGramConfig.loadConfig(true)
        }
    }

    // The prompt, an empty line, the message.
    @Test
    fun composeText_promptThenMessage() {
        val cases = listOf(
            Triple("", "Hello", "Hello"),
            Triple("Translate to English", "Привіт", "Translate to English\n\nПривіт"),
            Triple("  Summarize \r\n", "\r\nline1\r\nline2  ", "Summarize\n\nline1\nline2"),
            Triple("Only the prompt", "   ", "Only the prompt"),
            Triple(null, null, ""),
        )
        val wrong = cases.filter { (prompt, message, expected) -> MZGramAiRules.composeText(prompt, message) != expected }
            .map { (prompt, message, expected) -> "\"$prompt\" + \"$message\" = \"${MZGramAiRules.composeText(prompt, message)}\", not \"$expected\"" }
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    // Any text is one string of the script: only printable ASCII, no quote,
    // backslash or HTML character left as it is, and it reads back the same.
    @Test
    fun scriptString_isOneEscapedLiteral() {
        val samples = listOf(
            "He said \"hi\" \\ 'yes'",
            "</script><script>alert(1)</script>",
            "line1\nline2\r\n\ttab",
            "Привіт 👋 مرحبا",
            "   & <b>",
            "",
        )
        for (sample in samples) {
            val literal = MZGramAiRules.scriptString(sample)
            assertTrue("$sample: $literal", Regex("^\"[\\x20-\\x7e]*\"$").matches(literal))
            val inside = literal.substring(1, literal.length - 1)
            assertFalse("$sample: $literal", Regex("[\"'<>&]").containsMatchIn(inside.replace("\\\\", "")))
            assertEquals(sample, JSONArray("[$literal]").getString(0))
        }
    }

    // The script looks for the site's own fields, then any field; it fills
    // the field and never presses a key or a button.
    @Test
    fun insertScript_fillsTheFieldAndNeverSends() {
        val text = "Translate:\n\n\"Привіт\" </script>"
        for (provider in MZGramAiRules.providers()) {
            val script = MZGramAiRules.insertScript(provider, text)
            assertTrue("${provider.key}: the text", script.contains(MZGramAiRules.scriptString(text)))
            assertFalse("${provider.key}: the text as it is", script.contains("</script>"))
            for (selector in provider.inputSelectors) {
                assertTrue("${provider.key}: $selector", script.contains(MZGramAiRules.scriptString(selector)))
            }
            assertTrue(provider.key, script.contains(MZGramAiRules.scriptString("textarea:not([readonly]):not([disabled])")))
            for (word in listOf("click(", "submit", "keydown", "keypress", "KeyboardEvent", "Enter", "location", "fetch(", "XMLHttpRequest")) {
                assertFalse("${provider.key}: $word", script.contains(word))
            }
            for (status in listOf("'waiting'", "'inserted'", "'no_field'")) {
                assertTrue("${provider.key}: $status", script.contains(status))
            }
        }
    }

    @Test
    fun parseStatus_readsWhatTheScriptGives() {
        assertEquals(MZGramAiRules.STATUS_INSERTED, MZGramAiRules.parseStatus("\"inserted\""))
        assertEquals(MZGramAiRules.STATUS_NO_FIELD, MZGramAiRules.parseStatus("\"no_field\""))
        assertEquals(MZGramAiRules.STATUS_WAITING, MZGramAiRules.parseStatus("\"waiting\""))
        for (other in listOf(null, "null", "", "inserted", "\"other\"", "{\"mzgram_ai\":\"inserted\"}")) {
            assertEquals("$other", MZGramAiRules.STATUS_NONE, MZGramAiRules.parseStatus(other))
        }
    }

    // Only https in the window; a link to another site goes to the browser,
    // the site and its sign-in pages stay.
    @Test
    fun navigation_onlyHttpsAndTheSiteStays() {
        for (url in listOf("https://claude.ai/new", "https://accounts.google.com/signin", "about:blank")) {
            assertTrue(url, MZGramAiRules.allowedNavigation(url))
        }
        for (url in listOf("http://claude.ai/", "javascript:alert(1)", "file:///sdcard/a", "data:text/html,x", "", "intent://x#Intent;end", "content://x/y", "https://")) {
            assertFalse(url, MZGramAiRules.allowedNavigation(url))
        }
        val here = MZGramAiRules.NEW_WINDOW_HERE
        val browser = MZGramAiRules.NEW_WINDOW_BROWSER
        val refuse = MZGramAiRules.NEW_WINDOW_REFUSE
        val cases = listOf(
            Triple("claude", "https://claude.ai/chat/1", here),
            Triple("claude", "https://accounts.google.com/o/oauth2", here),
            Triple("claude", "https://en.wikipedia.org/wiki/Kyiv", browser),
            Triple("claude", "https://claude.ai@example.org/", browser),
            Triple("chatgpt", "https://auth.openai.com/log-in", here),
            Triple("chatgpt", "https://chatgpt.com.example.org/", browser),
            Triple("chatgpt", "https://evilchatgpt.com/", browser),
            Triple("grok", "https://accounts.x.ai/sign-in", here),
            Triple("gemini", "https://GEMINI.google.com/app", here),
            Triple("claude", "http://claude.ai/", refuse),
            Triple("claude", "javascript:alert(1)", refuse),
            Triple("claude", "data:text/html,x", refuse),
            Triple("claude", "about:blank", refuse),
            Triple("claude", "file:///etc/hosts", refuse),
        )
        val wrong = cases.filter { (key, url, expected) -> MZGramAiRules.newWindowTarget(MZGramAiRules.provider(key), url) != expected }
            .map { (key, url, expected) -> "$key $url: ${MZGramAiRules.newWindowTarget(MZGramAiRules.provider(key), url)}, not $expected" }
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    // Every text of "Ask AI" in each of the twelve languages, with its
    // placeholder, and none of them English outside English.
    @Test
    fun texts_inTheTwelveLanguages() {
        val keys = listOf(
            "MZGramSectionAi", "MZGramShowAskAi", "MZGramAskAi", "MZGramAiDefaultProvider",
            "MZGramAiSignOut", "MZGramAiSignOutConfirm", "MZGramAiSignedOut", "MZGramAiInfo", "MZGramAiMessage",
            "MZGramAiPrompt", "MZGramAiProvider", "MZGramAiModelOnSite", "MZGramAiPrivacyTitle",
            "MZGramAiPrivacyText", "MZGramAiStatusOpening", "MZGramAiStatusInserted",
            "MZGramAiStatusNoField", "MZGramAiInsert", "MZGramAiCopy", "MZGramAiNoProfile", "MZGramAiNoWebView",
            "MZGramAiAttachments", "MZGramAiLeftOut", "MZGramAiLeftOverLimit", "MZGramAiLeftTooLarge",
            "MZGramAiLeftArchives", "MZGramAiLeftMedia", "MZGramAiLeftOther", "MZGramAiStatusDownloading",
            "MZGramAiStatusAttached", "MZGramAiSaveFiles", "MZGramAiFilesSaved",
            "MZGramAiMenuButtons", "MZGramAiMenuButtonsInfo", "MZGramAiWarningFiles", "MZGramAiFilesTitle",
            "MZGramAiFilesHint", "MZGramAiFilesAdd", "MZGramAiFilesOther", "MZGramAiFilesPhotosOnly",
            "MZGramAiFilesDownloading", "MZGramAiStatusPressAdd", "MZGramAiStatusOneByOne",
        )
        val withName = setOf(
            "MZGramAiModelOnSite", "MZGramAiPrivacyText", "MZGramAiStatusOpening", "MZGramAiAttachments",
            "MZGramAiLeftOut", "MZGramAiLeftOverLimit", "MZGramAiLeftTooLarge", "MZGramAiLeftArchives",
            "MZGramAiLeftMedia", "MZGramAiLeftOther", "MZGramAiStatusDownloading", "MZGramAiStatusAttached",
            "MZGramAiFilesSaved", "MZGramAiFilesTitle", "MZGramAiFilesAdd", "MZGramAiStatusPressAdd",
            "MZGramAiStatusOneByOne",
        )
        // The same word in some languages: AI in Polish, Message in French
        val same = setOf("MZGramSectionAi", "MZGramAiMessage")
        val problems = ArrayList<String>()
        val english = resourcesIn("en")
        // The permanent note and the Model header are gone from the box
        for (gone in listOf("MZGramAiPrivacyNote", "MZGramAiModel")) {
            if (context.resources.getIdentifier(gone, "string", context.packageName) != 0) problems.add("$gone: still there")
        }
        for (key in keys) {
            val id = context.resources.getIdentifier(key, "string", context.packageName)
            if (id == 0) {
                problems.add("$key: no such string")
                continue
            }
            for (code in MZGramLanguage.LANGUAGES) {
                val text = resourcesIn(code).getString(id)
                if (text.isBlank()) problems.add("$code $key: empty")
                if (text.contains("—")) problems.add("$code $key: long dash")
                if ((key in withName) != text.contains("%1\$s")) problems.add("$code $key: placeholder")
                if (code != "en" && text == english.getString(id) && key !in same) {
                    problems.add("$code $key: English")
                }
            }
        }
        // The warning tells about the attachments and that it comes once; the
        // info under the switch no longer says nothing is sent
        val attachments = english.getString(context.resources.getIdentifier("MZGramAiPrivacyText", "string", context.packageName))
        if (!attachments.contains("attachments") || !attachments.contains("once")) problems.add("the warning: $attachments")
        val info = english.getString(context.resources.getIdentifier("MZGramAiInfo", "string", context.packageName))
        if (info.contains("nothing is sent") || !info.contains("10 photos and files")) problems.add("the info: $info")
        assertTrue(problems.joinToString("; "), problems.isEmpty())
        MZGramScreens.log("ai texts: ${keys.size} keys in ${MZGramLanguage.LANGUAGES.size} languages")
    }

    // Photos, pictures sent as files and documents an AI reads go; archives,
    // video, GIFs, stickers, voice and music do not.
    @Test
    fun classifyFile_photosDocumentsArchivesMedia() {
        val cases = mapOf(
            ("scan.JPG" to "image/jpeg") to MZGramAiRules.KIND_PHOTO,
            ("chart.png" to "") to MZGramAiRules.KIND_PHOTO,
            ("sticker.webp" to "image/webp") to MZGramAiRules.KIND_PHOTO,
            ("" to "image/png") to MZGramAiRules.KIND_PHOTO,
            ("README" to "") to MZGramAiRules.KIND_FILE,
            ("README.md" to "") to MZGramAiRules.KIND_FILE,
            ("notes.txt" to "text/plain") to MZGramAiRules.KIND_FILE,
            ("data.csv" to "") to MZGramAiRules.KIND_FILE,
            ("config.json" to "application/json") to MZGramAiRules.KIND_FILE,
            ("feed.xml" to "") to MZGramAiRules.KIND_FILE,
            ("app.log" to "") to MZGramAiRules.KIND_FILE,
            ("main.kt" to "") to MZGramAiRules.KIND_FILE,
            ("paper.pdf" to "application/pdf") to MZGramAiRules.KIND_FILE,
            ("letter.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document") to MZGramAiRules.KIND_FILE,
            ("table.xlsx" to "") to MZGramAiRules.KIND_FILE,
            ("slides.pptx" to "") to MZGramAiRules.KIND_FILE,
            ("old.doc" to "application/msword") to MZGramAiRules.KIND_FILE,
            ("text.rtf" to "") to MZGramAiRules.KIND_FILE,
            ("sheet.ods" to "") to MZGramAiRules.KIND_FILE,
            ("doc.odt" to "") to MZGramAiRules.KIND_FILE,
            ("show.odp" to "") to MZGramAiRules.KIND_FILE,
            ("backup.zip" to "application/zip") to MZGramAiRules.KIND_ARCHIVE,
            ("photos.rar" to "") to MZGramAiRules.KIND_ARCHIVE,
            ("src.7z" to "") to MZGramAiRules.KIND_ARCHIVE,
            ("src.tar" to "") to MZGramAiRules.KIND_ARCHIVE,
            ("src.tar.gz" to "") to MZGramAiRules.KIND_ARCHIVE,
            ("dump.bz2" to "") to MZGramAiRules.KIND_ARCHIVE,
            ("dump.xz" to "") to MZGramAiRules.KIND_ARCHIVE,
            ("MZGram.apk" to "application/vnd.android.package-archive") to MZGramAiRules.KIND_ARCHIVE,
            ("noname" to "application/zip") to MZGramAiRules.KIND_ARCHIVE,
            ("clip.mp4" to "video/mp4") to MZGramAiRules.KIND_MEDIA,
            ("funny.gif" to "image/gif") to MZGramAiRules.KIND_MEDIA,
            ("voice.ogg" to "audio/ogg") to MZGramAiRules.KIND_MEDIA,
            ("song.mp3" to "") to MZGramAiRules.KIND_MEDIA,
            ("sticker.tgs" to "application/x-tgsticker") to MZGramAiRules.KIND_MEDIA,
            ("tool.exe" to "application/x-msdownload") to MZGramAiRules.KIND_UNSUPPORTED,
            ("model.bin" to "") to MZGramAiRules.KIND_UNSUPPORTED,
        )
        val wrong = cases.filter { (file, kind) -> MZGramAiRules.classifyFile(file.first, file.second) != kind }
            .map { (file, kind) -> "${file.first} ${file.second}: ${MZGramAiRules.classifyFile(file.first, file.second)}, not $kind" }
        assertTrue(wrong.joinToString("; "), wrong.isEmpty())
    }

    // At most 10, each at most 30 MB; the rest counted by why.
    @Test
    fun chooseAttachments_tenAtMost_theRestCounted() {
        val mb = 1024L * 1024
        val candidates = ArrayList<MZGramAiRules.Candidate>()
        for (i in 0 until 12) candidates.add(MZGramAiRules.Candidate(if (i % 2 == 0) MZGramAiRules.KIND_PHOTO else MZGramAiRules.KIND_FILE, mb))
        candidates.add(1, MZGramAiRules.Candidate(MZGramAiRules.KIND_FILE, 31 * mb))
        candidates.add(MZGramAiRules.Candidate(MZGramAiRules.KIND_ARCHIVE, mb))
        candidates.add(MZGramAiRules.Candidate(MZGramAiRules.KIND_MEDIA, mb))
        candidates.add(MZGramAiRules.Candidate(MZGramAiRules.KIND_MEDIA, mb))
        candidates.add(MZGramAiRules.Candidate(MZGramAiRules.KIND_UNSUPPORTED, mb))
        val choice = MZGramAiRules.chooseAttachments(candidates)
        assertEquals(10, MZGramAiRules.MAX_ATTACHMENTS)
        assertEquals(30L * mb, MZGramAiRules.MAX_ATTACHMENT_SIZE)
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 7, 8, 9, 10), choice.chosen)
        assertEquals("over the limit", 2, choice.overLimit)
        assertEquals("too large", 1, choice.tooLarge)
        assertEquals("archives", 1, choice.archives)
        assertEquals("media", 2, choice.media)
        assertEquals("other", 1, choice.unsupported)
        assertEquals(7, choice.leftOut())
        assertTrue(MZGramAiRules.chooseAttachments(emptyList()).chosen.isEmpty())
    }

    // The chooser: a field for several files takes every checked file at
    // once; a field for one file takes the first, the rest wait.
    @Test
    fun give_allAtOnce_orOneByOne() {
        val all = MZGramAiRules.give(listOf(0, 2, 3), true)
        assertEquals(listOf(0, 2, 3), all.now)
        assertEquals(emptyList<Int>(), all.later)
        val one = MZGramAiRules.give(listOf(0, 2, 3), false)
        assertEquals(listOf(0), one.now)
        assertEquals(listOf(2, 3), one.later)
        val none = MZGramAiRules.give(emptyList(), true)
        assertTrue(none.now.isEmpty() && none.later.isEmpty())
    }

    // The site's accept list: kinds, extensions, or anything
    @Test
    fun accepts_theSitesList() {
        assertTrue(MZGramAiRules.accepts(null, "a.pdf", "application/pdf"))
        assertTrue(MZGramAiRules.accepts(arrayOf(), "a.pdf", "application/pdf"))
        assertTrue(MZGramAiRules.accepts(arrayOf(""), "a.pdf", "application/pdf"))
        assertTrue(MZGramAiRules.accepts(arrayOf("*/*"), "a.pdf", "application/pdf"))
        assertTrue(MZGramAiRules.accepts(arrayOf("image/*"), "photo.jpg", "image/jpeg"))
        assertFalse(MZGramAiRules.accepts(arrayOf("image/*"), "a.pdf", "application/pdf"))
        assertTrue(MZGramAiRules.accepts(arrayOf("image/png,.pdf"), "A.PDF", "application/octet-stream"))
        assertTrue(MZGramAiRules.accepts(arrayOf("image/png", "application/pdf"), "a.pdf", "application/pdf"))
        assertFalse(MZGramAiRules.accepts(arrayOf(".txt"), "a.pdf", "application/pdf"))
        assertTrue("no type known: the site decides", MZGramAiRules.accepts(arrayOf("image/*"), "x", ""))
    }

    // Settings > MZGram > AI: a button in the chat list menu for each site,
    // off by default, kept.
    @Test
    fun menuButtons_offByDefault_kept() {
        val saved = preferences().getStringSet("aiMenuButtons", null)?.toSet()
        try {
            preferences().edit().remove("aiMenuButtons").commit()
            MZGramConfig.loadConfig(true)
            for (provider in MZGramAiRules.providers()) {
                assertFalse("${provider.key}: off by default", MZGramAiRules.menuButton(provider))
            }
            MZGramAiRules.setMenuButton(MZGramAiRules.provider("deepseek"), true)
            MZGramAiRules.setMenuButton(MZGramAiRules.provider("grok"), true)
            MZGramConfig.loadConfig(true)
            assertEquals(listOf("deepseek", "grok"), MZGramAiRules.providers().filter { MZGramAiRules.menuButton(it) }.map { it.key })
            MZGramAiRules.setMenuButton(MZGramAiRules.provider("grok"), false)
            MZGramConfig.loadConfig(true)
            assertEquals(listOf("deepseek"), MZGramAiRules.providers().filter { MZGramAiRules.menuButton(it) }.map { it.key })
        } finally {
            preferences().edit().apply { if (saved == null) remove("aiMenuButtons") else putStringSet("aiMenuButtons", saved) }.commit()
            MZGramConfig.loadConfig(true)
        }
    }

    private fun resourcesIn(code: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocale(code.split("-").let { if (it.size == 2) Locale(it[0], it[1]) else Locale(it[0]) })
    }).resources

    // "Ask AI" is in the message menu unless its switch is off; on by default.
    @Test
    fun askAiSwitch_onByDefault() {
        val saved = preferences().all["showAskAi"]
        try {
            preferences().edit().remove("showAskAi").commit()
            MZGramConfig.loadConfig(true)
            val field = MZGramConfig::class.java.getField("showAskAi")
            assertNotNull(field)
            assertTrue("on by default", field.getBoolean(null))
        } finally {
            preferences().edit().apply { if (saved is Boolean) putBoolean("showAskAi", saved) else remove("showAskAi") }.commit()
            MZGramConfig.loadConfig(true)
        }
    }
}
