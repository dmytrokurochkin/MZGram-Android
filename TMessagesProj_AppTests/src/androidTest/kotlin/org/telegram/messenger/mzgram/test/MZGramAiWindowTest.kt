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
// address, with the site's input field, its add-files button (a file input
// in the page, or on Gemini's page one made when the button is pressed), a
// Send button, a form and an Enter key handler that each mark the page as
// sent. The window must put the text into the field and nothing else; a
// real tap on the site's add-files button shows MZGram's chooser with the
// two files (a text file and a picture), and Add gives both to the site at
// once. Nothing is sent.
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

    private fun page(field: String, fileInput: Boolean = true, multiple: Boolean = true) = """<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body>
<form id="form" onsubmit="window.mzgramSent = 'form'; return false;">$field
<button id="plus" type="button" style="display:block;width:240px;height:96px;font-size:40px">+</button>
${if (fileInput) "<input type=\"file\" id=\"files\" ${if (multiple) "multiple" else ""} style=\"display:none\">" else ""}
<button id="send" type="submit" onclick="window.mzgramSent = 'button'">Send</button></form>
<script>
window.mzgramSent = '';
window.mzgramFiles = [];
window.mzgramChanges = 0;
function record(list) { window.mzgramChanges++; window.mzgramFiles = window.mzgramFiles.concat(Array.prototype.map.call(list, function (f) { return f.name + ':' + f.size; })); }
var files = document.getElementById('files');
if (files) files.addEventListener('change', function () { record(files.files); });
document.getElementById('plus').addEventListener('click', function () {
  var input = files;
  if (!input) { input = document.createElement('input'); input.type = 'file'; input.multiple = true; input.addEventListener('change', function () { record(input.files); }); }
  input.click();
});
document.addEventListener('paste', function (e) { if (e.clipboardData && e.clipboardData.files.length) { record(e.clipboardData.files); e.preventDefault(); } }, true);
document.addEventListener('keydown', function (e) { if (e.key === 'Enter') window.mzgramSent = 'enter'; }, true);
</script></body></html>"""

    private fun sitePage(key: String) = page(fields.getValue(key), key != "gemini")

    private fun pageFiles(window: MZGramAiActivity) =
        evaluate(window, "JSON.stringify(window.mzgramFiles)")?.let { org.json.JSONTokener(it).nextValue() as String }

    private fun pageChanges(window: MZGramAiActivity) = evaluate(window, "window.mzgramChanges")

    // Waits for what the page holds: evaluate() waits for the page itself,
    // so it runs on the test thread, not inside MZGramScreens.waitFor.
    private fun waitForPage(timeoutSec: Int, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            if (check()) return true
            Thread.sleep(250)
        }
        return check()
    }

    // A real tap on the site's add-files button: a user's tap, so the page
    // may open its file dialog
    private fun tapPlus(window: MZGramAiActivity) {
        val rect = evaluate(window, "JSON.stringify((function(){var r = document.getElementById('plus').getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2, window.devicePixelRatio];})())")
            ?.let { org.json.JSONArray(org.json.JSONTokener(it).nextValue() as String) }
        assertTrue("the add-files button", rect != null)
        val at = IntArray(2)
        instrumentation.runOnMainSync { window.webViewForTest()!!.getLocationOnScreen(at) }
        val x = at[0] + rect!!.getDouble(0).toFloat() * rect.getDouble(2).toFloat()
        val y = at[1] + rect.getDouble(1).toFloat() * rect.getDouble(2).toFloat()
        // A system window over the app (an "isn't responding" dialog of another
        // app, run 43494190895) refuses the tap: wait for it to go, at most 3 times
        for (attempt in 1..3) {
            try {
                val down = android.os.SystemClock.uptimeMillis()
                instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
                instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down, down + 80, android.view.MotionEvent.ACTION_UP, x, y, 0))
                return
            } catch (e: IllegalArgumentException) {
                if (attempt == 3) throw e
                MZGramScreens.log("MZGram AI test: the tap went to another app's window, waiting: ${e.message}")
                Thread.sleep(5000)
            }
        }
    }

    // The chooser after a tap on the site's button: its title and rows
    private fun chooser(window: MZGramAiActivity, provider: MZGramAiRules.Provider, rows: Int): org.telegram.ui.mzgram.MZGramAiFilesSheet {
        tapPlus(window)
        assertTrue("${provider.key}: the chooser", MZGramScreens.waitFor(15) { window.filesSheetForTest()?.isShowing == true })
        Thread.sleep(800)
        val sheet = window.filesSheetForTest()!!
        assertEquals("${provider.key}: the title", org.telegram.messenger.LocaleController.formatString(org.telegram.messenger.R.string.MZGramAiFilesTitle, provider.name), sheet.titleForTest())
        assertEquals("${provider.key}: the files", rows, sheet.rowsForTest())
        return sheet
    }

    // A text file and a 1x1 PNG, as the messages' files would be
    private val notes = "MZGram test file\nsecond line\n".toByteArray()
    private val pixel = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", android.util.Base64.DEFAULT)

    private fun testFiles(): List<MZGramAiActivity.Attachment> {
        val folder = java.io.File(context.cacheDir, "mzgram-ai-test").apply { mkdirs() }
        val text = java.io.File(folder, "notes.txt").apply { writeBytes(notes) }
        val picture = java.io.File(folder, "pixel.png").apply { writeBytes(pixel) }
        return listOf(
            MZGramAiActivity.Attachment.ofFile("notes.txt", "text/plain", text),
            MZGramAiActivity.Attachment.ofFile("pixel.png", "image/png", picture),
        )
    }

    private val expectedFiles get() = "[\"notes.txt:${notes.size}\",\"pixel.png:${pixel.size}\"]"

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
            val window = MZGramAiActivity.forTest(provider.key, text, sitePage(provider.key), testFiles())
            MZGramScreens.open(window)
            assertTrue("${provider.key}: the window opened", MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() === window })
            MZGramScreens.log("MZGram AI test: ${provider.key} window created, start ${provider.startUrl}, profile ${window.profileName}")
            val done = MZGramScreens.waitFor(60) { window.aiState == MZGramAiActivity.STATE_FILES_READY }
            MZGramScreens.capture("ai-${provider.key}")
            assertTrue("${provider.key}: the files are ready for the site's button (${window.aiState})", done)
            assertEquals("${provider.key}: the AI profile", MZGramAiRules.PROFILE, window.profileName)
            assertEquals("${provider.key}: the page", "\"ok\"", evaluate(window, check(provider)))
            assertEquals("${provider.key}: nothing is put into the page by itself", "[]", pageFiles(window))
            val sheet = chooser(window, provider, 2)
            MZGramScreens.capture("ai-${provider.key}-chooser")
            MZGramAppearance.tap(sheet.addButtonForTest())
            assertTrue("${provider.key}: the files are given", waitForPage(15) { pageFiles(window) == expectedFiles })
            assertEquals("${provider.key}: both at once", "1", pageChanges(window))
            assertEquals("${provider.key}: the window says so", MZGramAiActivity.STATE_ATTACHED, window.aiState)
            assertEquals("${provider.key}: the files went by", "chooser", window.attachedHow)
            // A while later still nothing is sent
            Thread.sleep(2000)
            assertEquals("${provider.key}: the page later", "\"ok\"", evaluate(window, check(provider)))
            MZGramScreens.log("MZGram AI test: ${provider.key} files given by ${window.attachedHow}: 2")
            MZGramScreens.log("MZGram AI test: PASS ${provider.key}: the field has the text, 2 files attached, nothing sent")
            passed.add(provider.key)
            assertTrue("${provider.key}: the window closes", close(window, null))
        }
        assertEquals(listOf("gemini", "claude", "chatgpt", "deepseek", "grok"), passed)
        MZGramScreens.log("MZGram AI test: done")
    }

    // A site whose file field takes one file: the first checked one goes,
    // the next press of the site's button offers the rest.
    @Test
    fun window_oneFileField_takesThemOneByOne() {
        if (!MZGramAiActivity.profileSupported()) {
            return
        }
        MZGramConfig.setAiWarned(true)
        val provider = MZGramAiRules.provider("deepseek")!!
        val window = MZGramAiActivity.forTest(provider.key, text, page(fields.getValue(provider.key), true, false), testFiles())
        MZGramScreens.open(window)
        assertTrue("the files are ready", MZGramScreens.waitFor(60) { window.aiState == MZGramAiActivity.STATE_FILES_READY })
        MZGramAppearance.tap(chooser(window, provider, 2).addButtonForTest())
        assertTrue("the first file", waitForPage(15) { pageFiles(window) == "[\"notes.txt:${notes.size}\"]" })
        assertEquals("one waits", MZGramAiActivity.STATE_ONE_BY_ONE, window.aiState)
        MZGramAppearance.tap(chooser(window, provider, 1).addButtonForTest())
        assertTrue("the second file", waitForPage(15) { pageFiles(window) == expectedFiles })
        assertEquals(MZGramAiActivity.STATE_ATTACHED, window.aiState)
        MZGramScreens.log("MZGram AI test: PASS one file at a time")
        assertTrue("the window closes", close(window, null))
    }

    // No profiles: the window says so, has no WebView and loads nothing; the
    // text can still be copied.
    private fun window_withoutProfiles_opensNoSite() {
        val shown = ArrayList<String>()
        for (provider in MZGramAiRules.providers()) {
            val window = MZGramAiActivity.forTest(provider.key, text, sitePage(provider.key), testFiles())
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

    // A site opened from the chat list menu has no message: no warning, even
    // before the first window with one.
    @Test
    fun siteAlone_noWarning() {
        preferences().edit().putBoolean("aiWarned", false).commit()
        MZGramConfig.loadConfig(true)
        val start = MZGramScreens.lastFragment()!!
        instrumentation.runOnMainSync { MZGramAiActivity.openSite(start, "deepseek") }
        assertTrue("the window at once", MZGramScreens.waitFor(15) { MZGramScreens.lastFragment() is MZGramAiActivity })
        assertNull("no warning", start.visibleDialog)
        assertFalse("still not warned", warned())
        val window = MZGramScreens.lastFragment() as MZGramAiActivity
        assertTrue("it closes", close(window, start))
    }
}
