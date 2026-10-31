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
            "MZGramSectionAi", "MZGramSectionAiInfo", "MZGramShowAskAi", "MZGramAskAi", "MZGramAiDefaultProvider",
            "MZGramAiSignOut", "MZGramAiSignOutConfirm", "MZGramAiSignedOut", "MZGramAiInfo", "MZGramAiMessage",
            "MZGramAiPrompt", "MZGramAiProvider", "MZGramAiModel", "MZGramAiModelOnSite", "MZGramAiPrivacyTitle",
            "MZGramAiPrivacyText", "MZGramAiPrivacyNote", "MZGramAiStatusOpening", "MZGramAiStatusInserted",
            "MZGramAiStatusNoField", "MZGramAiInsert", "MZGramAiCopy", "MZGramAiNoProfile", "MZGramAiNoWebView",
        )
        val withName = setOf("MZGramAiModelOnSite", "MZGramAiPrivacyText", "MZGramAiStatusOpening")
        // The same word in some languages: AI and Model in Polish, Model in Turkish, Message in French
        val same = setOf("MZGramSectionAi", "MZGramAiModel", "MZGramAiMessage")
        val problems = ArrayList<String>()
        val english = resourcesIn("en")
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
        assertTrue(problems.joinToString("; "), problems.isEmpty())
        MZGramScreens.log("ai texts: ${keys.size} keys in ${MZGramLanguage.LANGUAGES.size} languages")
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
