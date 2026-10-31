package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
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
import org.telegram.messenger.mzgram.MZGramAiRules
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.mzgram.MZGramAiActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The "Ask AI" window for real, with no network and no sign-in: each of the
// five sites is replaced by a page of the tests under the site's own
// address, with the site's input field, a Send button, a form and an Enter
// key handler that each mark the page as sent. The window must put the text
// into the field, in the AI profile, and send nothing.
//
// A WebView without profiles (the API 34 emulator image has WebView 113)
// must not open the site at all. CI runs this class twice: in the main run
// on API 34, and on an API 35 emulator with mzAiProfiles=required
// (.github/scripts/mzgram_ai_window.sh), where profiles must be there and
// the text must get into each site's field.
class MZGramAiWindowTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var activity: Activity? = null
    private var savedWarned: Any? = null

    private fun preferences() = context.getSharedPreferences("mzgram_config", Context.MODE_PRIVATE)

    // Quotes, a closing script tag, Cyrillic, Arabic and an emoji
    private val text = "Translate to English:\n\nПривіт, \"світ\" </script> & 'ok' مرحبا 👋"

    private val fields = mapOf(
        "gemini" to """<rich-textarea><div class="ql-editor" contenteditable="true" style="display:block;min-height:48px"></div></rich-textarea>""",
        "claude" to """<div class="ProseMirror" contenteditable="true" style="min-height:48px"></div>""",
        "chatgpt" to """<div id="prompt-textarea" class="ProseMirror" contenteditable="true" style="min-height:48px"></div>""",
        "deepseek" to """<textarea id="chat-input" rows="3"></textarea>""",
        "grok" to """<textarea aria-label="Ask Grok anything" rows="3"></textarea>""",
    )

    private fun page(field: String) = """<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body>
<form id="form" onsubmit="window.mzgramSent = 'form'; return false;">$field
<button id="send" type="submit" onclick="window.mzgramSent = 'button'">Send</button></form>
<script>
window.mzgramSent = '';
document.addEventListener('keydown', function (e) { if (e.key === 'Enter') window.mzgramSent = 'enter'; }, true);
</script></body></html>"""

    @Before
    fun setUp() {
        savedWarned = preferences().all["aiWarned"]
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        activity = MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        MZGramScreens.log("MZGram AI test: WebView ${MZGramAiActivity.webViewState()}")
    }

    @After
    fun tearDown() {
        preferences().edit().apply { if (savedWarned is Boolean) putBoolean("aiWarned", savedWarned as Boolean) else remove("aiWarned") }.commit()
        MZGramConfig.loadConfig(true)
        instrumentation.runOnMainSync { activity?.finish() }
    }

    private fun warned() = preferences().getBoolean("aiWarned", false)

    private fun evaluate(window: MZGramAiActivity, script: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            window.evaluateForTest(script) { value ->
                result = value
                latch.countDown()
            }
        }
        latch.await(15, TimeUnit.SECONDS)
        return result
    }

    // What the page holds: "ok" when the field has the text once and nothing
    // was sent.
    private fun check(provider: MZGramAiRules.Provider): String {
        val selector = MZGramAiRules.scriptString(provider.inputSelectors[0])
        return """(function(){
var e = document.querySelector($selector);
if (!e) return 'no field';
if (window.mzgramSent) return 'sent by ' + window.mzgramSent;
var f = function (s) { return (s || '').replace(/\s+/g, ' ').trim(); };
var v = f(e.tagName === 'TEXTAREA' ? e.value : e.innerText);
return v === f(${MZGramAiRules.scriptString(text)}) ? 'ok' : 'the field has: ' + v;
})()"""
    }

    private val profilesRequired get() = InstrumentationRegistry.getArguments().getString("mzAiProfiles") == "required"

    // Closes the window, once its opening animation lets it
    private fun close(window: MZGramAiActivity, under: BaseFragment?) =
        MZGramScreens.waitFor(15) {
            if (MZGramScreens.lastFragment() === window) window.finishFragment(false)
            MZGramScreens.lastFragment() !== window && (under == null || MZGramScreens.lastFragment() === under)
        }

    @Test
    fun window_putsTheTextIntoEachSite_andSendsNothing() {
        val profiles = MZGramAiActivity.profileSupported()
        MZGramScreens.log("MZGram AI test: profiles $profiles, required $profilesRequired")
        if (profilesRequired) {
            assertTrue("this WebView must have profiles: ${MZGramAiActivity.webViewState()}", profiles)
        }
        if (!profiles) {
            window_withoutProfiles_opensNoSite()
            return
        }
        val passed = ArrayList<String>()
        for (provider in MZGramAiRules.providers()) {
            val window = MZGramAiActivity.forTest(provider.key, text, page(fields.getValue(provider.key)))
            MZGramScreens.open(window)
            assertTrue("${provider.key}: the window opened", MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() === window })
            MZGramScreens.log("MZGram AI test: ${provider.key} window created, start ${provider.startUrl}, profile ${window.profileName}")
            val done = MZGramScreens.waitFor(45) { window.aiState != MZGramAiActivity.STATE_OPENING }
            MZGramScreens.capture("ai-${provider.key}")
            assertTrue("${provider.key}: the window says what happened", done)
            assertEquals("${provider.key}: the AI profile", MZGramAiRules.PROFILE, window.profileName)
            assertEquals("${provider.key}: the text is in the field", MZGramAiActivity.STATE_INSERTED, window.aiState)
            assertEquals("${provider.key}: the page", "\"ok\"", evaluate(window, check(provider)))
            // A while later still nothing is sent
            Thread.sleep(2000)
            assertEquals("${provider.key}: the page later", "\"ok\"", evaluate(window, check(provider)))
            MZGramScreens.log("MZGram AI test: PASS ${provider.key}: the field has the text, nothing sent")
            passed.add(provider.key)
            assertTrue("${provider.key}: the window closes", close(window, null))
        }
        assertEquals(listOf("gemini", "claude", "chatgpt", "deepseek", "grok"), passed)
        MZGramScreens.log("MZGram AI test: done")
    }

    // No profiles: the window says so, has no WebView and loads nothing; the
    // text can still be copied.
    private fun window_withoutProfiles_opensNoSite() {
        val shown = ArrayList<String>()
        for (provider in MZGramAiRules.providers()) {
            val window = MZGramAiActivity.forTest(provider.key, text, page(fields.getValue(provider.key)))
            MZGramScreens.open(window)
            assertTrue("${provider.key}: the window opened", MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() === window })
            MZGramScreens.capture("ai-${provider.key}-no-profiles")
            assertEquals("${provider.key}: says there are no profiles", MZGramAiActivity.STATE_NO_PROFILE, window.aiState)
            assertNull("${provider.key}: no profile", window.profileName)
            assertNull("${provider.key}: no page", evaluate(window, "1"))
            MZGramScreens.log("MZGram AI test: PASS ${provider.key}: no profiles in this WebView, the site is not opened")
            shown.add(provider.key)
            assertTrue("${provider.key}: the window closes", close(window, null))
        }
        assertEquals(listOf("gemini", "claude", "chatgpt", "deepseek", "grok"), shown)
        MZGramScreens.log("MZGram AI test: done (no profiles)")
    }

    // The warning that the text goes to another service comes before the
    // first window only, and Cancel opens nothing.
    @Test
    fun warning_onceBeforeTheFirstWindow() {
        preferences().edit().putBoolean("aiWarned", false).commit()
        MZGramConfig.loadConfig(true)
        val start = MZGramScreens.lastFragment()!!
        val first = MZGramAiActivity.forTest("claude", text, page(fields.getValue("claude")))
        instrumentation.runOnMainSync { MZGramAiActivity.open(start, first) }
        var dialog: AlertDialog? = null
        assertTrue("the warning", MZGramScreens.waitFor(10) { dialog = start.visibleDialog as? AlertDialog; dialog != null })
        assertTrue("no window yet", MZGramScreens.lastFragment() === start)
        assertFalse(warned())

        instrumentation.runOnMainSync { dialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick() }
        assertTrue("Cancel closes it", MZGramScreens.waitFor(10) { start.visibleDialog == null })
        assertTrue("Cancel opens nothing", MZGramScreens.lastFragment() === start)
        assertFalse(warned())

        instrumentation.runOnMainSync { MZGramAiActivity.open(start, first) }
        assertTrue("the warning again", MZGramScreens.waitFor(10) { dialog = start.visibleDialog as? AlertDialog; dialog != null })
        MZGramScreens.capture("ai-warning")
        instrumentation.runOnMainSync { dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick() }
        assertTrue("Continue opens the window", MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() === first })
        assertTrue("kept", warned())
        assertTrue("the first window closes", close(first, start))

        val second = MZGramAiActivity.forTest("grok", text, page(fields.getValue("grok")))
        instrumentation.runOnMainSync { MZGramAiActivity.open(start, second) }
        assertTrue("then no warning", MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() === second })
        assertNull(start.visibleDialog)
        assertNotNull(second.getFragmentView())
        assertTrue("the second window closes", close(second, start))
    }
}
