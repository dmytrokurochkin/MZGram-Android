package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.peripherals.MZGramBadges
import org.telegram.messenger.mzgram.peripherals.MZGramEmojiFonts
import org.telegram.messenger.mzgram.peripherals.MZGramIconPacksCatalog
import org.telegram.messenger.mzgram.peripherals.MZGramNothingGlyph
import org.telegram.messenger.mzgram.peripherals.MZGramOnlineLyrics
import org.telegram.messenger.mzgram.peripherals.MZGramTorCompanion

class MZGramPeripheralsTest {

    private var initialTorEnabled = false
    private var initialTorPackage = "org.torproject.android"
    private var initialTorIdle = 5
    private var initialBadgesEnabled = false
    private var initialGlyphEnabled = false
    private var initialGlyphKey = ""
    private var initialGlyphBrightness = 100
    private var initialEmojiFontEnabled = false
    private var initialEmojiFontPath = ""
    private var initialLyricsOnline = false
    private var initialCatalogEnabled = false

    @Before
    fun setUp() {
        initialTorEnabled = MZGramConfig.torCompanionEnabled
        initialTorPackage = MZGramConfig.torCompanionPackage
        initialTorIdle = MZGramConfig.torIdleTimeoutMinutes
        initialBadgesEnabled = MZGramConfig.customBadgesEnabled
        initialGlyphEnabled = MZGramConfig.nothingGlyphEnabled
        initialGlyphKey = MZGramConfig.nothingGlyphApiKey
        initialGlyphBrightness = MZGramConfig.nothingGlyphBrightness
        initialEmojiFontEnabled = MZGramConfig.customEmojiFontEnabled
        initialEmojiFontPath = MZGramConfig.customEmojiFontPath
        initialLyricsOnline = MZGramConfig.syncedLyricsOnline
        initialCatalogEnabled = MZGramConfig.iconPacksCatalogEnabled
    }

    @After
    fun tearDown() {
        MZGramConfig.torCompanionEnabled = initialTorEnabled
        MZGramConfig.torCompanionPackage = initialTorPackage
        MZGramConfig.torIdleTimeoutMinutes = initialTorIdle
        MZGramConfig.customBadgesEnabled = initialBadgesEnabled
        MZGramConfig.nothingGlyphEnabled = initialGlyphEnabled
        MZGramConfig.nothingGlyphApiKey = initialGlyphKey
        MZGramConfig.nothingGlyphBrightness = initialGlyphBrightness
        MZGramConfig.customEmojiFontEnabled = initialEmojiFontEnabled
        MZGramConfig.customEmojiFontPath = initialEmojiFontPath
        MZGramConfig.syncedLyricsOnline = initialLyricsOnline
        MZGramConfig.iconPacksCatalogEnabled = initialCatalogEnabled
    }

    @Test
    fun testConfigDefaultsAndSetters() {
        MZGramConfig.torCompanionEnabled = false
        MZGramConfig.toggleTorCompanionEnabled()
        assertTrue(MZGramConfig.torCompanionEnabled)

        MZGramConfig.setTorCompanionPackage("it.belloworld.mercurygram.plugin.tor")
        assertEquals("it.belloworld.mercurygram.plugin.tor", MZGramConfig.torCompanionPackage)

        MZGramConfig.setTorIdleTimeoutMinutes(15)
        assertEquals(15, MZGramConfig.torIdleTimeoutMinutes)

        MZGramConfig.customBadgesEnabled = false
        MZGramConfig.toggleCustomBadgesEnabled()
        assertTrue(MZGramConfig.customBadgesEnabled)

        MZGramConfig.nothingGlyphEnabled = false
        MZGramConfig.toggleNothingGlyphEnabled()
        assertTrue(MZGramConfig.nothingGlyphEnabled)

        MZGramConfig.setNothingGlyphApiKey("test-glyph-key")
        assertEquals("test-glyph-key", MZGramConfig.nothingGlyphApiKey)

        MZGramConfig.setNothingGlyphBrightness(75)
        assertEquals(75, MZGramConfig.nothingGlyphBrightness)

        MZGramConfig.customEmojiFontEnabled = false
        MZGramConfig.toggleCustomEmojiFontEnabled()
        assertTrue(MZGramConfig.customEmojiFontEnabled)

        MZGramConfig.setCustomEmojiFontPath("/sdcard/apple_emoji.ttf")
        assertEquals("/sdcard/apple_emoji.ttf", MZGramConfig.customEmojiFontPath)

        MZGramConfig.syncedLyricsOnline = false
        MZGramConfig.toggleSyncedLyricsOnline()
        assertTrue(MZGramConfig.syncedLyricsOnline)

        MZGramConfig.iconPacksCatalogEnabled = false
        MZGramConfig.toggleIconPacksCatalogEnabled()
        assertTrue(MZGramConfig.iconPacksCatalogEnabled)
    }

    @Test
    fun testTorCompanionConstants() {
        assertEquals(9050, MZGramTorCompanion.TOR_SOCKS_PORT)
        assertEquals(1, MZGramTorCompanion.BLOCKING_STUB_PORT)
        assertEquals("127.0.0.1", MZGramTorCompanion.TOR_SOCKS_HOST)
        assertEquals("org.torproject.android", MZGramTorCompanion.PACKAGE_ORBOT)
        assertEquals("it.belloworld.mercurygram.plugin.tor", MZGramTorCompanion.PACKAGE_MERCURY_TOR)
    }

    @Test
    fun testTorCompanion_strictSocks5NoDirectFallback() {
        // Disabled by default
        assertFalse(MZGramConfig.torCompanionEnabled)

        // When enabled, proxy is configured strictly to TOR_SOCKS_HOST (127.0.0.1) and TOR_SOCKS_PORT (9050)
        MZGramTorCompanion.applyTorProxySettings(true)
        val currentProxy = org.telegram.messenger.SharedConfig.currentProxy
        assertNotNull(currentProxy)
        assertEquals(MZGramTorCompanion.TOR_SOCKS_HOST, currentProxy.settings.address)
        assertEquals(MZGramTorCompanion.TOR_SOCKS_PORT, currentProxy.settings.port)
        // Proxy type must be SOCKS5
        assertEquals(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5, currentProxy.settings.type)

        // When disabled, applyTorProxySettings does not touch proxy
        val savedProxy = org.telegram.messenger.SharedConfig.currentProxy
        MZGramTorCompanion.applyTorProxySettings(false)
        assertEquals(savedProxy, org.telegram.messenger.SharedConfig.currentProxy)
    }

    @Test
    fun testBadgesSystem() {
        MZGramConfig.customBadgesEnabled = true
        MZGramBadges.setBadge(123456L, "VIP Donor", 0xFFE91E63.toInt())

        val badge = MZGramBadges.getBadge(123456L)
        assertNotNull(badge)
        assertEquals("VIP Donor", badge?.text)
        assertEquals(0xFFE91E63.toInt(), badge?.color)

        MZGramBadges.setBadge(123456L, null, 0)
        MZGramConfig.customBadgesEnabled = false
    }

    @Test
    fun testNothingGlyphAvailability() {
        // Without Nothing hardware, isNothingDevice returns false on emulators/standard devices
        if (!MZGramNothingGlyph.isNothingDevice()) {
            assertFalse(MZGramNothingGlyph.isAvailable())
        }
    }

    @Test
    fun testEmojiFontsFile() {
        val file = MZGramEmojiFonts.getCustomEmojiFontFile()
        assertNotNull(file)
        assertEquals(MZGramEmojiFonts.FONT_FILENAME, file.name)
    }

    @Test
    fun testOnlineLyricsCache() {
        val cacheFile = MZGramOnlineLyrics.getCacheFile("Queen", "Bohemian Rhapsody")
        assertNotNull(cacheFile)
        assertTrue(cacheFile.name.endsWith(".lrc"))
    }

    @Test
    fun testIconPacksCatalogLicenseVerification() {
        val approved = MZGramIconPacksCatalog.getApprovedPacks()
        assertTrue(approved.size >= 7)

        for (pack in approved) {
            assertTrue(pack.approved)
            assertTrue(pack.license == MZGramIconPacksCatalog.LICENSE_APACHE2 || pack.license == MZGramIconPacksCatalog.LICENSE_MIT)
            assertTrue(pack.iconsCount > 0)
        }

        val all = MZGramIconPacksCatalog.getAllPacks()
        assertTrue(all.size > approved.size)
        val excluded = MZGramIconPacksCatalog.getExcludedCount()
        assertTrue(excluded >= 8)
    }
}
