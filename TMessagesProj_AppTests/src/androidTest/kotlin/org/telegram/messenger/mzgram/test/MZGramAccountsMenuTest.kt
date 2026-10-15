package org.telegram.messenger.mzgram.test

import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.mzgram.MZGramAccountsMenu

// The accounts in the chat list menu (the three dots at the top of the chat
// list): every signed-in account, the current one first, "Add Account" while
// there is room, and a tap switches the account at once. Two accounts are
// made on the device for the test; nothing reaches a server.
class MZGramAccountsMenuTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var savedSwitch = true
    private val second = 1

    private fun user(id: Long, name: String) = TLRPC.TL_user().also {
        it.id = id
        it.first_name = name
    }

    @Before
    fun setUp() {
        savedSwitch = MZGramConfig.accountsInMenu
        MZGramConfig.accountsInMenu = true
        instrumentation.runOnMainSync {
            UserConfig.getInstance(0).setCurrentUser(user(7_000_000_001L, "MZGram test self"))
            // Loaded first: a later first load would read the empty saved
            // config over the user set here
            UserConfig.getInstance(second).loadConfig()
            UserConfig.getInstance(second).setCurrentUser(user(7_000_000_002L, "MZGram second account"))
            UserConfig.getInstance(second).loginTime = 1_800_000_000
        }
        MZGramScreens.launchApp()
        instrumentation.runOnMainSync {
            if (UserConfig.selectedAccount != 0) {
                MZGramAccountsMenu.switchTo(0, UserConfig.selectedAccount)
            }
        }
        assertTrue("on account 0", MZGramScreens.waitFor(30) { UserConfig.selectedAccount == 0 })
    }

    @After
    fun tearDown() {
        MZGramConfig.accountsInMenu = savedSwitch
        instrumentation.runOnMainSync {
            MZGramAccountsMenu.lastMenu?.get()?.dismiss()
            if (UserConfig.selectedAccount != 0) {
                MZGramAccountsMenu.switchTo(0, UserConfig.selectedAccount)
            }
        }
        // Back on account 0 with its chat list before the other account goes
        MZGramScreens.waitFor(30) { UserConfig.selectedAccount == 0 && chatList()?.currentAccount == 0 }
        Thread.sleep(1000)
        instrumentation.runOnMainSync {
            UserConfig.getInstance(second).clearConfig()
        }
    }

    private fun chatList(): DialogsActivity? =
        LaunchActivity::class.java.getMethod("getLastFragmentIncludeMainTabs").invoke(null) as? DialogsActivity

    private var listed: List<Int> = emptyList()
    private var listedFromSecond: List<Int> = emptyList()

    // The test accounts have no key on Telegram's servers, so the app may sign
    // the second one out after a while: it is set again on the main thread
    // right before the menu reads the accounts.
    private fun keepSecondAccount() {
        if (!UserConfig.getInstance(second).isClientActivated) {
            UserConfig.getInstance(second).setCurrentUser(user(7_000_000_002L, "MZGram second account"))
        }
        listed = MZGramAccountsMenu.accounts(0)
        listedFromSecond = MZGramAccountsMenu.accounts(second)
    }

    // The menu of the three dots, as a tap on them opens it
    private fun openMenu(): ViewGroup {
        assertTrue("chat list", MZGramScreens.waitFor(30) { chatList() != null })
        instrumentation.runOnMainSync {
            keepSecondAccount()
            MZGramAccountsMenu.lastMenu = null
            DialogsActivity::class.java.getDeclaredMethod("showItemOptions").also { it.isAccessible = true }.invoke(chatList())
        }
        var layout: ViewGroup? = null
        assertTrue("menu shown", MZGramScreens.waitFor(15) {
            layout = MZGramAccountsMenu.lastMenu?.get()?.layout
            layout?.isShown == true
        })
        Thread.sleep(500)
        return layout!!
    }

    private fun row(menu: ViewGroup, tag: String): View? {
        var found: View? = null
        instrumentation.runOnMainSync { found = menu.findViewWithTag(tag) }
        return found
    }

    @Test
    fun menu_listsTheAccounts_currentFirst_andAddAccount() {
        val menu = openMenu()
        assertEquals("the current account first", listOf(0, second), listed)
        assertEquals(listOf(second, 0), listedFromSecond)
        MZGramScreens.capture("accounts-menu")
        assertNotNull("this account", row(menu, MZGramAccountsMenu.TAG_ACCOUNT + 0))
        assertNotNull("the other account", row(menu, MZGramAccountsMenu.TAG_ACCOUNT + second))
        assertNotNull("Add Account while there is room", row(menu, MZGramAccountsMenu.TAG_ADD_ACCOUNT))
        var before = -1
        var after = -1
        instrumentation.runOnMainSync {
            val all = ArrayList<View>()
            menu.findViewsWithText(all, "MZGram test self", View.FIND_VIEWS_WITH_TEXT)
            before = all.size
            all.clear()
            menu.findViewsWithText(all, "MZGram second account", View.FIND_VIEWS_WITH_TEXT)
            after = all.size
        }
        assertTrue("names shown", before > 0 && after > 0)
    }

    @Test
    fun tapOnAnotherAccount_switchesToIt() {
        val menu = openMenu()
        val other = row(menu, MZGramAccountsMenu.TAG_ACCOUNT + second)!!
        instrumentation.runOnMainSync { other.performClick() }
        assertTrue("switched to the other account", MZGramScreens.waitFor(30) { UserConfig.selectedAccount == second })
        assertTrue("the menu closed", MZGramScreens.waitFor(10) { MZGramAccountsMenu.lastMenu?.get()?.isShown != true })
        Thread.sleep(1000)
        MZGramScreens.capture("accounts-menu-switched")
    }

    @Test
    fun switchOff_menuWithoutAccounts() {
        MZGramConfig.accountsInMenu = false
        assertTrue("chat list", MZGramScreens.waitFor(30) { chatList() != null })
        instrumentation.runOnMainSync {
            MZGramAccountsMenu.lastMenu = null
            DialogsActivity::class.java.getDeclaredMethod("showItemOptions").also { it.isAccessible = true }.invoke(chatList())
        }
        Thread.sleep(800)
        assertNull("no accounts in the menu", MZGramAccountsMenu.lastMenu?.get())
    }
}
