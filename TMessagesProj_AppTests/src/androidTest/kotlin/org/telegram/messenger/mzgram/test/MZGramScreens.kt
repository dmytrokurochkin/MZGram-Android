package org.telegram.messenger.mzgram.test

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.telegram.ui.LaunchActivity
import java.io.File
import java.io.FileOutputStream

// Screenshots of the real app screens for the CI artifact (mzgram-screens/,
// pulled by .github/scripts/mzgram_tests.sh).
object MZGramScreens {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun log(msg: String) = Log.i("MZGramArchiveTest", msg)

    // The whole screen as the user sees it.
    fun capture(name: String): Bitmap {
        instrumentation.waitForIdleSync()
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
}
