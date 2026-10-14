package org.telegram.messenger.mzgram.test

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ThemesHorizontalListCell
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.DefaultThemesPreviewCell
import org.telegram.ui.ThemeActivity
import org.telegram.ui.mzgram.MZGramUpdateBanner

// Settings > Chat settings (Appearance) as the user goes through it: the
// screens are opened and tapped like on a phone (real touch events), and
// what changed is read from the theme and from the screen itself.
object MZGramAppearance {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    // On the main thread; directly when already there (inside MZGramScreens.waitFor)
    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block() else instrumentation.runOnMainSync(block)
    }

    // The built-in themes, as Browse Themes lists them: Classic, Day, Tinted, Night, Arctic
    val builtIn = listOf("Blue", "Day", "Dark Blue", "Night", "Arctic Blue")

    // The app on its chat list, signed in as a test user, with no update
    // banner over the bottom of the screens.
    fun startApp() {
        val self = TLRPC.TL_user()
        self.id = 7_000_000_001L
        self.first_name = "MZGram test self"
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramConfig.checkUpdates = false
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        onMain { MZGramUpdateBanner.current()?.hide() }
    }

    fun open(type: Int): ThemeActivity {
        val fragment = ThemeActivity(type)
        MZGramScreens.open(fragment)
        assertTrue("appearance screen $type shown", MZGramScreens.waitFor(30) { fragment.fragmentView?.isShown == true })
        Thread.sleep(800)
        return fragment
    }

    fun close(fragment: ThemeActivity) {
        onMain { fragment.finishFragment(false) }
        Thread.sleep(500)
    }

    private fun intField(fragment: ThemeActivity, name: String) =
        ThemeActivity::class.java.getDeclaredField(name).also { it.isAccessible = true }.getInt(fragment)

    // The screen's list, through reflection: the test classpath has no androidx.recyclerview
    private fun listOf(fragment: ThemeActivity): Any =
        ThemeActivity::class.java.getDeclaredField("listView").also { it.isAccessible = true }.get(fragment)!!

    // Scrolls the screen to one of its rows ("themeListRow", "nightSystemDefaultRow", ...)
    fun scrollTo(fragment: ThemeActivity, row: String): Int {
        val position = intField(fragment, row)
        assertTrue("$row is on the screen", position >= 0)
        onMain {
            val list = listOf(fragment)
            list.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(list, position)
        }
        Thread.sleep(700)
        return position
    }

    fun rowView(fragment: ThemeActivity, row: String): View? {
        val position = scrollTo(fragment, row)
        var view: View? = null
        onMain {
            val list = listOf(fragment)
            val holder = list.javaClass.getMethod("findViewHolderForAdapterPosition", Int::class.javaPrimitiveType).invoke(list, position)
            view = holder?.javaClass?.getField("itemView")?.get(holder) as View?
        }
        return view
    }

    // A real tap in the middle of a view, as a finger does it. A note at the
    // bottom ("System Default theme is now disabled") is closed first, as the
    // user waits for it to go: it lies over the rows under it.
    fun tap(view: View) {
        onMain { Bulletin.hideVisible() }
        Thread.sleep(400)
        var rect = IntArray(4)
        onMain { rect = MZGramScreens.screenRect(view) }
        val x = (rect[0] + rect[2]) / 2f
        val y = (rect[1] + rect[3]) / 2f
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_UP, x, y, 0))
    }

    // A field of the view's class or of a class it extends (the screens use anonymous subclasses)
    private fun <T> field(view: Any, name: String): T? {
        var type: Class<*>? = view.javaClass
        while (type != null) {
            val found = type.declaredFields.firstOrNull { it.name == name }
            if (found != null) {
                found.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return found.get(view) as T?
            }
            type = type.superclass
        }
        throw NoSuchFieldException(name)
    }

    private fun viewsNamed(root: View?, simpleName: String): List<View> {
        val found = ArrayList<View>()
        fun walk(view: View?) {
            if (view == null) return
            if (view.javaClass.simpleName == simpleName && view.isShown) found.add(view)
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return found
    }

    // Browse Themes: the built-in themes of the theme list (keys), in order
    fun themeList(fragment: ThemeActivity): List<String> {
        var keys = kotlin.collections.listOf<String>()
        onMain {
            val cell = MZGramScreens.findView(fragment.fragmentView, ThemesHorizontalListCell::class.java)
            val themes = ArrayList<Theme.ThemeInfo>()
            if (cell != null) {
                field<List<Theme.ThemeInfo>>(cell, "defaultThemes")?.let { themes.addAll(it) }
                field<List<Theme.ThemeInfo>>(cell, "customThemes")?.let { themes.addAll(it) }
            }
            keys = themes.map { it.key }
        }
        return keys
    }

    fun themeView(fragment: ThemeActivity, key: String): View? {
        var view: View? = null
        onMain {
            view = viewsNamed(fragment.fragmentView, "InnerThemeView").firstOrNull { field<Theme.ThemeInfo>(it, "themeInfo")?.key == key }
        }
        return view
    }

    // Scrolls the theme list so the theme is on the screen, then returns its view
    fun showTheme(fragment: ThemeActivity, key: String): View? {
        val index = themeList(fragment).indexOf(key)
        onMain {
            val cell = MZGramScreens.findView(fragment.fragmentView, ThemesHorizontalListCell::class.java)
            if (cell != null && index >= 0) {
                cell.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType).invoke(cell, index)
            }
        }
        Thread.sleep(600)
        return themeView(fragment, key)
    }

    // The accent circles on the screen: (view, accent id)
    fun accentViews(fragment: ThemeActivity): List<Pair<View, Int>> {
        var result = kotlin.collections.listOf<Pair<View, Int>>()
        onMain {
            result = viewsNamed(fragment.fragmentView, "InnerAccentView").mapNotNull { view ->
                field<Theme.ThemeAccent>(view, "currentAccent")?.let { view to it.id }
            }
        }
        return result
    }

    // The color theme row of Appearance and its day/night switch
    fun row(fragment: ThemeActivity): DefaultThemesPreviewCell? {
        var cell: DefaultThemesPreviewCell? = null
        onMain { cell = MZGramScreens.findView(fragment.fragmentView, DefaultThemesPreviewCell::class.java) }
        return cell
    }

    fun rowCount(cell: DefaultThemesPreviewCell): Int {
        var count = 0
        onMain {
            val adapter = field<Any>(cell, "adapter")!!
            count = (adapter.javaClass.getField("items").get(adapter) as List<*>?)?.size ?: 0
        }
        return count
    }

    fun dayNightSwitch(cell: DefaultThemesPreviewCell): View = field<View>(cell, "dayNightCell")!!

    fun rowSelected(cell: DefaultThemesPreviewCell): Int {
        var selected = -1
        onMain { selected = field<Int>(cell, "selectedPosition") ?: -1 }
        return selected
    }

    fun hasServerThemes(): Boolean {
        var result = false
        onMain {
            result = org.telegram.messenger.mzgram.MZGramDefaultThemes.hasServerThemes(MediaDataController.getInstance(0))
        }
        return result
    }

    fun active(): Theme.ThemeInfo {
        var theme: Theme.ThemeInfo? = null
        onMain { theme = Theme.getActiveTheme() }
        return theme!!
    }

    fun describe(): String {
        val theme = active()
        return "${theme.key} accent ${theme.currentAccentId} dark=${theme.isDark} auto=${Theme.selectedAutoNightType}"
    }

    fun color(key: Int): Int {
        var value = 0
        onMain { value = Theme.getColor(key) }
        return value
    }

    // Average brightness of the screen, 0 (black) to 255 (white)
    fun brightness(bitmap: Bitmap): Int {
        var sum = 0L
        var count = 0
        val step = 8
        var y = bitmap.height / 10
        while (y < bitmap.height * 9 / 10) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                sum += (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                count++
                x += step
            }
            y += step
        }
        return (sum / count.coerceAtLeast(1)).toInt()
    }

    // Applies a theme and accent the way the app's own screens do
    fun apply(key: String, accentId: Int = -1) {
        onMain {
            val theme = Theme.getTheme(key)
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, accentId)
        }
        MZGramScreens.waitFor(10) { Theme.getActiveTheme().key == key }
        Thread.sleep(800)
    }

    // The state the other tests expect: Classic with its default accent,
    // the night mode following the system (the app's default)
    fun reset() {
        onMain {
            // The night themes and the last ones picked as on a fresh install
            Theme.getTheme("Night").setCurrentAccentId(0)
            Theme.getTheme("Dark Blue").setCurrentAccentId(0)
            org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", android.app.Activity.MODE_PRIVATE).edit()
                .remove("lastDayTheme").remove("lastDarkTheme").commit()
        }
        apply("Blue", Theme.DEFALT_THEME_ACCENT_ID)
        onMain {
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM
            Theme.saveAutoNightThemeConfig()
        }
    }

    // Built-in accents of a theme (ids below 100), the default one first
    fun builtInAccents(key: String): List<Int> {
        var ids = kotlin.collections.listOf<Int>()
        onMain {
            ids = Theme.getTheme(key).themeAccents.filter { it.id < 100 }.map { it.id }
        }
        return ids
    }
}
