package org.telegram.messenger.mzgram.test

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramPeerId
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.SettingsActivity

// The own header of Telegram's Settings: the name, below it the number with
// the username, below it the own ID by Show ID (hidden by default, a tap
// copies it). Each line sits below the real bottom of the line above it, so
// that a name in a fallback font (runes) does not go over the number, and the
// header is as high as its lines need, also when Show ID is turned on while
// Settings are open: the ID is whole, not cut by its item nor under Accounts.
class MZGramSettingsHeaderTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val before = MZGramConfig.peerIdMode
    private val selfId = 7_000_000_001L

    @After
    fun tearDown() {
        MZGramConfig.setPeerIdMode(before)
    }

    private fun self(name: String) {
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = name
        self.phone = "380501234567"
        self.username = "mzgram_header_test"
        self.flags = self.flags or 8
        UserConfig.getInstance(0).setCurrentUser(self)
    }

    private fun field(settings: SettingsActivity, name: String): View =
        SettingsActivity::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(settings) as View

    private fun open(): SettingsActivity {
        MZGramScreens.launchApp()
        assertTrue("app opened", MZGramScreens.waitFor(30) { MZGramScreens.lastFragment() != null })
        val settings = SettingsActivity()
        MZGramScreens.open(settings)
        assertTrue("settings shown", MZGramScreens.waitFor(30) { settings.fragmentView?.isShown == true })
        Thread.sleep(1000)
        return settings
    }

    private fun top(view: View) = MZGramScreens.screenRect(view)[1]
    private fun bottom(view: View) = top(view) + view.height

    // The item of the list that holds the header, which cuts what is lower.
    private fun item(settings: SettingsActivity): View = field(settings, "topView").parent as View

    // The item below the header in the list: Accounts.
    private fun next(settings: SettingsActivity): View? {
        val item = item(settings)
        val list = item.parent as ViewGroup
        var found: View? = null
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (child !== item && child.top >= item.top && (found == null || child.top < found.top)) {
                found = child
            }
        }
        return found
    }

    @Test
    fun ownId_hiddenByDefault() {
        MZGramConfig.setPeerIdMode(MZGramPeerId.HIDDEN)
        self("MZGram test self")
        val settings = open()
        var shown = true
        var height = 0
        instrumentation.runOnMainSync {
            shown = field(settings, "mzgramIdView").visibility == View.VISIBLE
            height = item(settings).height
        }
        MZGramScreens.capture("settings-header-id-hidden")
        instrumentation.runOnMainSync { settings.finishFragment(false) }
        assertTrue("no own ID while Show ID is hidden", !shown)
        assertEquals("the header as high as Telegram's", AndroidUtilities.dp(188f), height)
    }

    @Test
    fun ownId_shownBelowTheNumber_andATapCopiesIt() {
        MZGramConfig.setPeerIdMode(MZGramPeerId.TELEGRAM_API)
        self("MZGram test self")
        val settings = open()
        var text = ""
        var shown = false
        var below = false
        var inside = false
        instrumentation.runOnMainSync {
            val id = field(settings, "mzgramIdView") as TextView
            val subtitle = field(settings, "subtitleView")
            val header = field(settings, "topView")
            shown = id.visibility == View.VISIBLE
            text = id.text.toString()
            below = top(id) >= bottom(subtitle)
            inside = bottom(id) <= bottom(header)
            id.performClick()
        }
        MZGramScreens.capture("settings-header-id-shown")
        Thread.sleep(500)
        var copied = ""
        instrumentation.runOnMainSync {
            val clipboard = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        }
        // The bulletin of the copy, if this Android shows one (below 12L
        // Telegram does, newer Android shows its own), goes away by itself.
        Thread.sleep(4000)
        var bulletin = true
        instrumentation.runOnMainSync { bulletin = Bulletin.getVisibleBulletin() != null }
        MZGramScreens.capture("settings-header-id-copied")
        instrumentation.runOnMainSync { settings.finishFragment(false) }
        assertTrue("the own ID is shown", shown)
        assertEquals("ID: $selfId", text)
        assertTrue("below the number", below)
        assertTrue("inside the header", inside)
        assertEquals("a tap copies the ID", selfId.toString(), copied)
        assertTrue("no bulletin of the copy left", !bulletin)
    }

    // Show ID turned on in MZGram's settings while Settings are open: back on
    // Settings (onResume) the header grows by the ID line, for a long name
    // and for a name with runes, which is taller than a line of Roboto.
    @Test
    fun ownId_turnedOnLater_isWhole_andAboveAccounts() {
        val names = listOf(
            "MZGram test self with a very long name that does not fit in one line",
            "ᛒᛖᚱᛊᛖᚱᚴ Берсерк ᛒᛖᚱᛊᛖᚱᚴ",
        )
        for (name in names) {
            MZGramConfig.setPeerIdMode(MZGramPeerId.HIDDEN)
            self(name)
            val settings = open()
            MZGramConfig.setPeerIdMode(MZGramPeerId.TELEGRAM_API)
            instrumentation.runOnMainSync { settings.onResume() }
            Thread.sleep(1000)
            var shown = false
            var idBottom = 0
            var itemBottom = 0
            var nextTop = Int.MAX_VALUE
            var titleBottom = 0
            var subtitleTop = 0
            instrumentation.runOnMainSync {
                val id = field(settings, "mzgramIdView")
                shown = id.visibility == View.VISIBLE && id.height > 0
                idBottom = bottom(id)
                itemBottom = bottom(item(settings))
                next(settings)?.let { nextTop = top(it) }
                titleBottom = bottom(field(settings, "titleView"))
                subtitleTop = top(field(settings, "subtitleView"))
            }
            MZGramScreens.capture("settings-header-id-later-" + names.indexOf(name))
            MZGramScreens.log("ID turned on later, $name: ID bottom $idBottom, header bottom $itemBottom, Accounts top $nextTop")
            instrumentation.runOnMainSync { settings.finishFragment(false) }
            assertTrue("the own ID is shown: $name", shown)
            assertTrue("the ID not cut by the header: $name, ID bottom $idBottom, header bottom $itemBottom", idBottom <= itemBottom)
            assertTrue("the ID not under Accounts: $name, ID bottom $idBottom, Accounts top $nextTop", idBottom <= nextTop)
            assertTrue("the number below the name: $name", subtitleTop >= titleBottom)
        }
    }

    @Test
    fun runicName_doesNotGoOverTheNumber() {
        MZGramConfig.setPeerIdMode(MZGramPeerId.HIDDEN)
        self("ᛒᛖᚱᛊᛖᚱᚴ Берсерк ᛒᛖᚱᛊᛖᚱᚴ")
        val settings = open()
        var titleBottom = 0
        var subtitleTop = 0
        var subtitleBottom = 0
        var headerBottom = 0
        instrumentation.runOnMainSync {
            val title = field(settings, "titleView")
            val subtitle = field(settings, "subtitleView")
            titleBottom = bottom(title)
            subtitleTop = top(subtitle)
            subtitleBottom = bottom(subtitle)
            headerBottom = bottom(field(settings, "topView"))
        }
        MZGramScreens.capture("settings-header-runes")
        MZGramScreens.log("runic name: title bottom $titleBottom, number top $subtitleTop")
        instrumentation.runOnMainSync { settings.finishFragment(false) }
        assertTrue("the number below the name: name bottom $titleBottom, number top $subtitleTop", subtitleTop >= titleBottom)
        assertTrue("the number inside the header", subtitleBottom <= headerBottom)
    }
}
