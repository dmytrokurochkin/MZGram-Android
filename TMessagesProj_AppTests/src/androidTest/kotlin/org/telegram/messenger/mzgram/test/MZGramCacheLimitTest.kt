package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.AutoDeleteMediaTask
import org.telegram.messenger.FileLoader
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.CacheControlActivity
import org.telegram.ui.Components.SlideChooseView
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Settings > Data and storage > Storage usage > Maximum cache size: the
// 1 GB option, and that the limit really clears the cache. The cache files
// here are sparse (no disk space used): the cleanup counts their length,
// and orders them by the last access time, which the test sets.
class MZGramCacheLimitTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = SharedConfig.getPreferences()
    private lateinit var dir: File
    private var savedLimit = Int.MAX_VALUE

    @Before
    fun setUp() {
        savedLimit = prefs.getInt("cache_limit", Int.MAX_VALUE)
        dir = File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "mzgram-cache-limit-test")
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        prefs.edit().putInt("cache_limit", savedLimit).commit()
    }

    // Four 400 MB files, the oldest first, used 4, 3, 2 and 1 hours ago.
    private fun cacheFiles(count: Int = 4): List<File> {
        val now = System.currentTimeMillis()
        return (0 until count).map { i ->
            val file = File(dir, "cache-$i.bin")
            RandomAccessFile(file, "rw").use { it.setLength(400L * 1024 * 1024) }
            val time = FileTime.fromMillis(now - (count - i) * 3_600_000L)
            Files.getFileAttributeView(file.toPath(), BasicFileAttributeView::class.java).setTimes(time, time, null)
            file
        }
    }

    private fun runCleanup() {
        SharedConfig.lastKeepMediaCheckTime = 0
        AutoDeleteMediaTask.run()
        waitForCleanup()
    }

    private fun waitForCleanup() {
        val done = CountDownLatch(1)
        Utilities.cacheClearQueue.postRunnable { done.countDown() }
        assertTrue("cleanup finished", done.await(60, TimeUnit.SECONDS))
    }

    @Test
    fun oneGigabyteLimit_removesTheOldestFilesUntilTheCacheFits() {
        val files = cacheFiles()
        prefs.edit().putInt("cache_limit", 1).commit()
        runCleanup()
        MZGramScreens.log("cache limit 1 GB: kept ${files.filter { it.exists() }.map { it.name }}")
        // 1600 MB of test files: the two oldest go, 800 MB stays under 1000 MB
        assertFalse("oldest removed", files[0].exists())
        assertFalse("second oldest removed", files[1].exists())
        assertTrue("newer file kept", files[2].exists())
        assertTrue("newest file kept", files[3].exists())
    }

    @Test
    fun oneGigabyteLimit_keepsACacheUnderTheLimit() {
        val files = cacheFiles(2)
        prefs.edit().putInt("cache_limit", 1).commit()
        runCleanup()
        assertTrue("800 MB of files kept under a 1 GB limit", files.all { it.exists() })
    }

    // Values saved before the 1 GB option keep their meaning.
    @Test
    fun earlierLimits_keepTheirMeaning() {
        val files = cacheFiles()
        prefs.edit().putInt("cache_limit", 5).commit()
        runCleanup()
        assertTrue("1600 MB kept under 5 GB", files.all { it.exists() })
        prefs.edit().putInt("cache_limit", Int.MAX_VALUE).commit()
        runCleanup()
        assertTrue("1600 MB kept without a limit", files.all { it.exists() })
    }

    private fun openSlider(): Pair<CacheControlActivity, SlideChooseView> {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val fragment = CacheControlActivity()
        MZGramScreens.open(fragment)
        assertTrue("storage usage shown", MZGramScreens.waitFor(30) { fragment.fragmentView?.isShown == true })
        var slider: SlideChooseView? = null
        // The row comes once the storage size is known; scroll down to it
        assertTrue("cache size slider shown", MZGramScreens.waitFor(60) {
            val list = CacheControlActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(fragment)
            val adapter = list.javaClass.getMethod("getAdapter").invoke(list)
            val count = adapter.javaClass.getMethod("getItemCount").invoke(adapter) as Int
            if (count > 0) {
                list.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(list, count - 1)
            }
            slider = MZGramScreens.findView(fragment.fragmentView, SlideChooseView::class.java)
            slider != null
        })
        return fragment to slider!!
    }

    private fun options(slider: SlideChooseView): List<String> =
        (SlideChooseView::class.java.getDeclaredField("optionsStr").also { it.isAccessible = true }.get(slider) as Array<*>).map { it.toString() }

    private fun selected(slider: SlideChooseView): Int =
        SlideChooseView::class.java.getDeclaredField("selectedIndex").also { it.isAccessible = true }.getInt(slider)

    // The saved 1 GB is shown after the screen opens again (as after an app
    // update: the value lives in the app's settings), and choosing it from a
    // higher limit clears the cache at once instead of at the next daily check.
    @Test
    fun slider_listsOneGigabyte_showsTheSavedValue_andAppliesALowerLimitAtOnce() {
        val files = cacheFiles()
        prefs.edit().putInt("cache_limit", 5).commit()
        SharedConfig.lastKeepMediaCheckTime = (System.currentTimeMillis() / 1000).toInt()
        val (fragment, slider) = openSlider()
        val labels = options(slider)
        MZGramScreens.log("cache size options: $labels, selected ${selected(slider)}")
        assertEquals("the first option", "1 GB", labels.first())
        assertEquals("the saved 5 GB is selected", "5 GB", labels[selected(slider)])

        instrumentation.runOnMainSync {
            val callback = SlideChooseView::class.java.getDeclaredField("callback").also { it.isAccessible = true }.get(slider)
            callback.javaClass.getMethod("onOptionSelected", Int::class.javaPrimitiveType).also { it.isAccessible = true }.invoke(callback, 0)
        }
        assertEquals("1 GB saved", 1, prefs.getInt("cache_limit", Int.MAX_VALUE))
        waitForCleanup()
        assertFalse("the lower limit cleared the oldest file at once", files[0].exists())
        assertTrue("the newest file kept", files[3].exists())
        instrumentation.runOnMainSync { fragment.finishFragment() }

        val (again, sliderAgain) = openSlider()
        assertEquals("the saved 1 GB is selected after reopening", "1 GB", options(sliderAgain)[selected(sliderAgain)])
        instrumentation.runOnMainSync { again.finishFragment() }
    }
}
