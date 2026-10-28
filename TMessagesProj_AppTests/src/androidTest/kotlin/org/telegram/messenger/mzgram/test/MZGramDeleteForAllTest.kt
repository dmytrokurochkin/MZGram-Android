package org.telegram.messenger.mzgram.test

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Components.AlertsCreator

// Settings > MZGram > Message menu > "Delete for everyone by default": the
// box of Delete has "Also delete for ..." already ticked. Telegram leaves it
// unticked. Checked on the real box of a message in a private chat.
class MZGramDeleteForAllTest {

    private val account = 0
    private val selfId = 7_000_000_001L
    private val otherId = 7_400_000_001L
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val controller get() = MessagesController.getInstance(account)
    private var saved = false
    private var activity: Activity? = null

    @Before
    fun setUp() {
        val self = TLRPC.TL_user()
        self.id = selfId
        self.first_name = "MZGram test self"
        UserConfig.getInstance(account).setCurrentUser(self)
        saved = MZGramConfig.deleteForAllByDefault
    }

    @After
    fun tearDown() {
        MZGramConfig.deleteForAllByDefault = saved
        instrumentation.runOnMainSync {
            MZGramScreens.lastFragment()?.dismissCurrentDialog()
            activity?.finish()
        }
    }

    // The Delete box of one message of our own in a private chat, and
    // whether its "Also delete for ..." box is ticked.
    private fun deleteForAllTicked(name: String): Boolean {
        activity = MZGramScreens.launchApp()
        assertTrue("chat list shown", MZGramScreens.waitFor(45) { MZGramScreens.lastFragment()?.fragmentView?.isShown == true })
        val other = TLRPC.TL_user().also {
            it.id = otherId
            it.first_name = "Delete test"
            it.access_hash = 1
        }
        val message = TLRPC.TL_message().also {
            it.id = 1000
            it.out = true
            it.date = (System.currentTimeMillis() / 1000).toInt()
            it.message = "MZGram delete for everyone"
            it.from_id = TLRPC.TL_peerUser().also { p -> p.user_id = selfId }
            it.peer_id = TLRPC.TL_peerUser().also { p -> p.user_id = otherId }
            it.dialog_id = otherId
        }
        instrumentation.runOnMainSync {
            controller.putUser(other, false)
            val fragment = MZGramScreens.lastFragment()!!
            AlertsCreator.createDeleteMessagesAlert(fragment, other, null, null, null, 0,
                MessageObject(account, message, false, false), null, null, 0, 0, null, null, null, null)
        }
        var cell: CheckBoxCell? = null
        assertTrue("the Delete box with its tick box", MZGramScreens.waitFor(10) {
            val dialog = MZGramScreens.lastFragment()?.visibleDialog
            cell = MZGramScreens.findView(dialog?.window?.decorView, CheckBoxCell::class.java)
            cell != null
        })
        MZGramScreens.capture(name)
        var ticked = false
        instrumentation.runOnMainSync { ticked = cell!!.isChecked }
        MZGramScreens.log("delete for everyone $name: ticked=$ticked")
        assertNotNull(cell)
        return ticked
    }

    @Test
    fun deleteForAll_offByDefault_likeTelegram() {
        MZGramConfig.deleteForAllByDefault = false
        assertFalse("unticked, as in Telegram", deleteForAllTicked("delete-for-all-off"))
    }

    @Test
    fun deleteForAll_switchedOn_isTicked() {
        MZGramConfig.deleteForAllByDefault = true
        assertTrue("ticked when the switch is on", deleteForAllTicked("delete-for-all-on"))
    }
}
