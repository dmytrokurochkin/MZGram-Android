package org.telegram.messenger.mzgram.test

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramPeerId
import org.telegram.tgnet.TLRPC
import org.telegram.ui.SettingsActivity

// The own header of Telegram's Settings: the name, below it the number with
// the username, below it the own ID by Show ID (hidden by default, a tap
// copies it). Each line sits below the real bottom of the line above it, so
// that a name in a fallback font (runes) does not go over the number.
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

    @Test
    fun ownId_hiddenByDefault() {
        MZGramConfig.setPeerIdMode(MZGramPeerId.HIDDEN)
        self("MZGram test self")
        val settings = open()
        var shown = true
        instrumentation.runOnMainSync {
            shown = field(settings, "mzgramIdView").visibility == View.VISIBLE
        }
        MZGramScreens.capture("settings-header-id-hidden")
        instrumentation.runOnMainSync { settings.finishFragment(false) }
        assertTrue("no own ID while Show ID is hidden", !shown)
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
        instrumentation.runOnMainSync { settings.finishFragment(false) }
        assertTrue("the own ID is shown", shown)
        assertEquals("ID: $selfId", text)
        assertTrue("below the number", below)
        assertTrue("inside the header", inside)
        assertEquals("a tap copies the ID", selfId.toString(), copied)
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
