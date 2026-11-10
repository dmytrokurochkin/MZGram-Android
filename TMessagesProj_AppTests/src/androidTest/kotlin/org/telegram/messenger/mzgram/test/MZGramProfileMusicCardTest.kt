package org.telegram.messenger.mzgram.test

import androidx.test.core.app.ApplicationLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader as TgAppLoader
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.mzgram.ProfileMusicCard

class MZGramProfileMusicCardTest {

    private var initialCard = false

    @Before
    fun setUp() {
        initialCard = MZGramConfig.profileMusicCard
        MZGramConfig.profileMusicCard = false
    }

    @After
    fun tearDown() {
        MZGramConfig.profileMusicCard = initialCard
    }

    @Test
    fun testConfigToggle() {
        assertFalse(MZGramConfig.profileMusicCard)
        MZGramConfig.toggleProfileMusicCard()
        assertTrue(MZGramConfig.profileMusicCard)
        MZGramConfig.toggleProfileMusicCard()
        assertFalse(MZGramConfig.profileMusicCard)
    }

    @Test
    fun testStringsExist() {
        val context = TgAppLoader.applicationContext
        if (context != null) {
            val title = context.getString(R.string.MZGramProfileMusicCard)
            val info = context.getString(R.string.MZGramProfileMusicCardInfo)
            assertTrue(title.isNotEmpty())
            assertTrue(info.isNotEmpty())
        }
    }

    @Test
    fun testCardComponentCreation() {
        val context = TgAppLoader.applicationContext
        if (context != null) {
            val card = ProfileMusicCard(context, null)
            assertNotNull(card)
            var clicked = false
            card.setOnCardClickListener { clicked = true }
            // Setting null document safely handled without throwing
            card.set(null, 0)
        }
    }
}
