package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramDeletedDialogs
import org.telegram.tgnet.TLRPC

class MZGramDeletedDialogsInMainListTest {

    private val account = 0
    private var initialEnabled = false
    private var initialListEnabled = false

    @Before
    fun setUp() {
        initialEnabled = MZGramConfig.deletedDialogsInMainList
        initialListEnabled = MZGramConfig.deletedDialogsList
        MZGramConfig.deletedDialogsList = true
        MZGramConfig.deletedDialogsInMainList = false
    }

    @After
    fun tearDown() {
        MZGramConfig.deletedDialogsList = initialListEnabled
        MZGramConfig.deletedDialogsInMainList = initialEnabled
    }

    @Test
    fun testConfigToggle() {
        assertFalse(MZGramConfig.deletedDialogsInMainList)
        MZGramConfig.toggleDeletedDialogsInMainList()
        assertTrue(MZGramConfig.deletedDialogsInMainList)
        MZGramConfig.toggleDeletedDialogsInMainList()
        assertFalse(MZGramConfig.deletedDialogsInMainList)
    }

    @Test
    fun testRestoreAndRemoveSafeCalls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val controller = MessagesController.getInstance(account)
            assertNotNull(controller)
        }
        MZGramConfig.deletedDialogsInMainList = true
        MZGramDeletedDialogs.restore(account)
        MZGramDeletedDialogs.removeRestored(account)
    }
}
