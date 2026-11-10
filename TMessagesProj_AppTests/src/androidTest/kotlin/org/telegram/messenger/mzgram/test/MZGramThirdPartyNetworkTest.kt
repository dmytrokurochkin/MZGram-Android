package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramDoH
import org.telegram.messenger.mzgram.MZGramPills
import org.telegram.ui.mzgram.pills.MZGramPillStack

class MZGramThirdPartyNetworkTest {

    private var initialDoh = 0
    private var initialOpenFreeMap = false

    @Before
    fun setUp() {
        initialDoh = MZGramConfig.dohProvider
        initialOpenFreeMap = MZGramConfig.openFreeMapPreview
        MZGramConfig.dohProvider = 0
        MZGramConfig.openFreeMapPreview = false
    }

    @After
    fun tearDown() {
        MZGramConfig.dohProvider = initialDoh
        MZGramConfig.openFreeMapPreview = initialOpenFreeMap
    }

    @Test
    fun testDoHConfiguration() {
        assertEquals(0, MZGramConfig.dohProvider)
        MZGramConfig.setDohProvider(MZGramDoH.PROVIDER_CLOUDFLARE)
        assertEquals(MZGramDoH.PROVIDER_CLOUDFLARE, MZGramConfig.dohProvider)
        assertNotNull(MZGramDoH.getProviderUrl(MZGramDoH.PROVIDER_CLOUDFLARE, "telegram.org"))
        assertTrue(MZGramDoH.getProviderUrl(MZGramDoH.PROVIDER_CLOUDFLARE, "telegram.org")!!.contains("cloudflare-dns.com"))

        MZGramConfig.setDohProvider(MZGramDoH.PROVIDER_GOOGLE)
        assertEquals(MZGramDoH.PROVIDER_GOOGLE, MZGramConfig.dohProvider)
        assertTrue(MZGramDoH.getProviderUrl(MZGramDoH.PROVIDER_GOOGLE, "telegram.org")!!.contains("dns.google"))

        MZGramConfig.setDohProvider(MZGramDoH.PROVIDER_ADGUARD)
        assertEquals(MZGramDoH.PROVIDER_ADGUARD, MZGramConfig.dohProvider)
        assertTrue(MZGramDoH.getProviderUrl(MZGramDoH.PROVIDER_ADGUARD, "telegram.org")!!.contains("dns.adguard-dns.com"))

        MZGramConfig.setDohProvider(MZGramDoH.PROVIDER_OFF)
        assertEquals(MZGramDoH.PROVIDER_OFF, MZGramConfig.dohProvider)
    }

    @Test
    fun testOpenFreeMapPreviewToggle() {
        assertFalse(MZGramConfig.openFreeMapPreview)
        MZGramConfig.toggleOpenFreeMapPreview()
        assertTrue(MZGramConfig.openFreeMapPreview)
        MZGramConfig.toggleOpenFreeMapPreview()
        assertFalse(MZGramConfig.openFreeMapPreview)
    }

    @Test
    fun testWeatherAndRatesPillsRegistered() {
        assertTrue(MZGramPills.isKnown(MZGramPills.WEATHER))
        assertTrue(MZGramPills.isKnown(MZGramPills.RATES))
        assertNotNull(MZGramPillStack.titleOf(MZGramPills.WEATHER))
        assertNotNull(MZGramPillStack.titleOf(MZGramPills.RATES))

        val context = ApplicationLoader.applicationContext
        if (context != null) {
            val weatherPill = MZGramPillStack.create(MZGramPills.WEATHER, context, null)
            assertNotNull(weatherPill)
            val ratesPill = MZGramPillStack.create(MZGramPills.RATES, context, null)
            assertNotNull(ratesPill)
        }
    }

    @Test
    fun testStringsPresent() {
        val context = ApplicationLoader.applicationContext
        if (context != null) {
            assertTrue(context.getString(R.string.MZGramDoHProvider).isNotEmpty())
            assertTrue(context.getString(R.string.MZGramOpenFreeMapPreview).isNotEmpty())
            assertTrue(context.getString(R.string.MZGramPillWeather).isNotEmpty())
            assertTrue(context.getString(R.string.MZGramPillRates).isNotEmpty())
        }
    }
}
