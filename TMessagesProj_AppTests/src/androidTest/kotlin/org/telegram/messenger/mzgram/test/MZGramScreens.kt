package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.UniversalFragment
import org.telegram.ui.LaunchActivity
import org.telegram.ui.mzgram.MZGramSettingsActivity
import java.io.File
import java.io.FileOutputStream

// Screenshots of the real app screens for the CI artifact (mzgram-screens/,
// pulled by .github/scripts/mzgram_tests.sh).
object MZGramScreens {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    // The whole screen as the user sees it.
    // No waitForIdleSync: a screen with a running animation (the chat list
    // shows "Connecting..." without a network) is never idle.
    fun capture(name: String): Bitmap {
        Thread.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        save(bitmap, name)
        return bitmap
    }

    fun save(bitmap: Bitmap, name: String) {
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "mzgram-screens").also { it.mkdirs() }
        val out = File(dir, "mzgram-$name.png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The test run uninstalls the app, and its files dir with it.
        shell("mkdir -p /data/local/tmp/mzgram-screens")
        shell("cp ${out.absolutePath} /data/local/tmp/mzgram-screens/")
        log("screenshot $name: ${out.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
    }

    fun launchApp(): Activity {
        // Otherwise Android and the app ask for them over the first screen.
        val pkg = instrumentation.targetContext.packageName
        for (permission in listOf("POST_NOTIFICATIONS", "READ_CONTACTS", "WRITE_CONTACTS")) {
            shell("pm grant $pkg android.permission.$permission")
        }
        val intent = Intent(instrumentation.targetContext, LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return instrumentation.startActivitySync(intent)
    }

    fun waitFor(timeoutSec: Int, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            var ok = false
            instrumentation.runOnMainSync { ok = check() }
            if (ok) return true
            Thread.sleep(250)
        }
        var ok = false
        instrumentation.runOnMainSync { ok = check() }
        return ok
    }

    fun <T : View> findView(root: View?, type: Class<T>, filter: (T) -> Boolean = { true }): T? {
        if (root == null) return null
        if (type.isInstance(root) && root.isShown && filter(type.cast(root)!!)) return type.cast(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), type, filter)?.let { return it }
            }
        }
        return null
    }

    fun screenRect(view: View): IntArray {
        val xy = IntArray(2)
        view.getLocationOnScreen(xy)
        return intArrayOf(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }

    // LaunchActivity and the list views extend androidx classes the test
    // classpath does not have, so they are reached through reflection.
    fun lastFragment(): BaseFragment? =
        LaunchActivity::class.java.getMethod("getLastFragment").invoke(null) as BaseFragment?

    fun listViewOf(fragment: UniversalFragment): ViewGroup? =
        UniversalFragment::class.java.getField("listView").get(fragment) as ViewGroup?

    // Opens a screen on top of the app, as tapping its entry would.
    fun open(fragment: BaseFragment) {
        instrumentation.runOnMainSync {
            lastFragment()!!.presentFragment(fragment, false, true)
        }
    }

    // Settings > MZGram as the user goes through it: the main page, then a
    // few topics, each opened by tapping its row and left with Back.
    // Returns the topics whose page did not open or did not go back.
    fun settingsPages(prefix: String, sections: List<Int>): List<String> {
        val problems = ArrayList<String>()
        val main = MZGramSettingsActivity()
        open(main)
        if (!waitFor(30) { (listViewOf(main)?.childCount ?: 0) > 0 }) {
            return listOf("main page not shown")
        }
        Thread.sleep(1000)
        capture("$prefix-main")
        for (section in sections) {
            instrumentation.runOnMainSync {
                val items = ArrayList<org.telegram.ui.Components.UItem>()
                val fill = MZGramSettingsActivity::class.java.getDeclaredMethod("fillItems", ArrayList::class.java, org.telegram.ui.Components.UniversalAdapter::class.java)
                fill.isAccessible = true
                fill.invoke(main, items, null)
                val row = items.first { it.id == MZGramSettingsActivity.SECTION_ROW_ID + section }
                val onClick = MZGramSettingsActivity::class.java.getDeclaredMethod("onClick", org.telegram.ui.Components.UItem::class.java, View::class.java, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                onClick.isAccessible = true
                onClick.invoke(main, row, null, 0, 0f, 0f)
            }
            val opened = waitFor(30) {
                val page = lastFragment()
                page is MZGramSettingsActivity && page !== main && (listViewOf(page)?.childCount ?: 0) > 0
            }
            if (!opened) {
                problems.add("section $section did not open")
                continue
            }
            Thread.sleep(1000)
            capture("$prefix-section-$section")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            if (!waitFor(30) { lastFragment() === main }) {
                problems.add("section $section: Back did not return to the main page")
            }
            Thread.sleep(500)
        }
        return problems
    }
}
