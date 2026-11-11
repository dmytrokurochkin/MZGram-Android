package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        val initialProxy = org.telegram.messenger.SharedConfig.currentProxy
        val initialEnabled = org.telegram.messenger.SharedConfig.isProxyEnabled()

        try {
            // When enabled, proxy is configured strictly to TOR_SOCKS_HOST (127.0.0.1) and TOR_SOCKS_PORT (9050)
            MZGramTorCompanion.applyTorProxySettings(true)
            val currentProxy = org.telegram.messenger.SharedConfig.currentProxy
            assertNotNull(currentProxy)
            assertEquals(MZGramTorCompanion.TOR_SOCKS_HOST, currentProxy.settings.address)
            assertEquals(MZGramTorCompanion.TOR_SOCKS_PORT, currentProxy.settings.port)
            // Proxy type must be SOCKS5
            assertEquals(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5, currentProxy.settings.type)

            // When disabled, applyTorProxySettings restores previous proxy state
            MZGramTorCompanion.applyTorProxySettings(false)
            assertEquals(initialProxy, org.telegram.messenger.SharedConfig.currentProxy)
        } finally {
            MZGramTorCompanion.applyTorProxySettings(false)
            org.telegram.messenger.SharedConfig.currentProxy = initialProxy
            if (initialProxy != null && initialEnabled) {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(true, initialProxy.settings)
            } else {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null)
            }
        }
    }

    @Test
    fun testTorCompanion_statePersistenceAcrossProcessRestart() {
        val initialProxy = org.telegram.messenger.SharedConfig.currentProxy
        val initialEnabled = org.telegram.messenger.SharedConfig.isProxyEnabled()

        try {
            // Setup a mock prior proxy
            val mockPriorSettings = org.telegram.utils.proxy.ProxySettings.builder()
                .setType(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5)
                .setAddress("198.51.100.1")
                .setPort(1080)
                .setUser("testuser")
                .setPassword("testpass")
                .build()
            val mockPriorProxy = org.telegram.messenger.SharedConfig.ProxyInfo(mockPriorSettings)
            org.telegram.messenger.SharedConfig.currentProxy = mockPriorProxy
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", true).commit()
            org.telegram.tgnet.ConnectionsManager.setProxySettings(true, mockPriorSettings)

            // Step 1: Enable Tor companion
            MZGramTorCompanion.applyTorProxySettings(true)
            val torProxy = org.telegram.messenger.SharedConfig.currentProxy
            assertNotNull(torProxy)
            assertEquals(MZGramTorCompanion.TOR_SOCKS_HOST, torProxy.settings.address)
            assertEquals(MZGramTorCompanion.TOR_SOCKS_PORT, torProxy.settings.port)

            // Step 2: Simulate process restart by resetting static memory fields without reflection
            MZGramTorCompanion.resetTestingState()

            // Step 3: Disable Tor companion in the new process session
            MZGramTorCompanion.applyTorProxySettings(false)

            // Assert: Prior proxy restored cleanly from SharedPreferences
            val restoredProxy = org.telegram.messenger.SharedConfig.currentProxy
            assertNotNull("Prior proxy must be restored from SharedPreferences after process restart", restoredProxy)
            assertEquals("198.51.100.1", restoredProxy.settings.address)
            assertEquals(1080, restoredProxy.settings.port)
            assertEquals("testuser", restoredProxy.settings.user)
            assertEquals("testpass", restoredProxy.settings.password)
            assertEquals(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5, restoredProxy.settings.type)
        } finally {
            MZGramTorCompanion.applyTorProxySettings(false)
            MZGramTorCompanion.resetTestingState()
            org.telegram.messenger.SharedConfig.currentProxy = initialProxy
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", initialEnabled).commit()
            if (initialProxy != null && initialEnabled) {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(true, initialProxy.settings)
            } else {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null)
            }
        }
    }

    @Test
    fun testTorCompanion_disabledPriorProxyNotEnabledOnTorDisable() {
        MZGramTorCompanion.applyTorProxySettings(false)
        MZGramTorCompanion.resetTestingState()
        val initialProxy = org.telegram.messenger.SharedConfig.currentProxy
        val initialEnabled = org.telegram.messenger.SharedConfig.isProxyEnabled()

        try {
            // Setup a mock prior proxy that is saved in currentProxy but DISABLED (proxy_enabled = false)
            val mockPriorSettings = org.telegram.utils.proxy.ProxySettings.builder()
                .setType(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5)
                .setAddress("198.51.100.2")
                .setPort(1080)
                .build()
            val mockPriorProxy = org.telegram.messenger.SharedConfig.ProxyInfo(mockPriorSettings)
            org.telegram.messenger.SharedConfig.currentProxy = mockPriorProxy
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", false).commit()
            org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null)

            assertFalse("Initial proxy must be disabled", org.telegram.messenger.SharedConfig.isProxyEnabled())

            // Step 1: Enable Tor companion
            MZGramTorCompanion.applyTorProxySettings(true)
            assertTrue("Tor proxy must be active", MZGramTorCompanion.isTorProxyActive())
            assertFalse("hadPreviousProxy must be false when prior proxy was disabled", MZGramTorCompanion.hadPreviousProxy())

            // Step 2: Disable Tor companion
            MZGramTorCompanion.applyTorProxySettings(false)

            // Assert: Disabling Tor must NOT turn on the proxy
            assertFalse("Proxy must remain disabled after Tor is turned off", org.telegram.messenger.SharedConfig.isProxyEnabled())
            val restored = org.telegram.messenger.SharedConfig.currentProxy
            assertNotNull("currentProxy after toggle cycle must not be null", restored)
            assertEquals("198.51.100.2", restored!!.settings.address)
            assertEquals(1080, restored.settings.port)
            assertEquals(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5, restored.settings.type)
            assertEquals("currentProxy after toggle cycle must equal the initial proxy object", mockPriorProxy, restored)
        } finally {
            MZGramTorCompanion.applyTorProxySettings(false)
            MZGramTorCompanion.resetTestingState()
            org.telegram.messenger.SharedConfig.currentProxy = initialProxy
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", initialEnabled).commit()
            if (initialProxy != null && initialEnabled) {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(true, initialProxy.settings)
            } else {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null)
            }
        }
    }

    @Test
    fun testTorCompanion_safeReadProxyType_stringOrInt() {
        val prefs = org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("mzgram_config_test_type", android.content.Context.MODE_PRIVATE)
        try {
            // Case 1: Stored as Integer
            prefs.edit().putInt("mzgram_tor_prev_type", 1).commit()
            assertEquals(1, MZGramTorCompanion.readSafeProxyType(prefs))

            // Case 2: Stored as String integer
            prefs.edit().putString("mzgram_tor_prev_type", "2").commit()
            assertEquals(2, MZGramTorCompanion.readSafeProxyType(prefs))

            // Case 3: Stored as String enum name (e.g. SOCKS5)
            prefs.edit().putString("mzgram_tor_prev_type", "SOCKS5").commit()
            assertEquals(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5.ordinal, MZGramTorCompanion.readSafeProxyType(prefs))

            // Case 4: Missing or invalid key defaults to 0 without exception
            prefs.edit().remove("mzgram_tor_prev_type").commit()
            assertEquals(0, MZGramTorCompanion.readSafeProxyType(prefs))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun testTorCompanion_alreadyActiveDoesNotOverwriteSavedState() {
        val initialProxy = org.telegram.messenger.SharedConfig.currentProxy
        val initialEnabled = org.telegram.messenger.SharedConfig.isProxyEnabled()

        try {
            // Setup a mock prior proxy
            val mockPriorSettings = org.telegram.utils.proxy.ProxySettings.builder()
                .setType(org.telegram.utils.proxy.ProxySettings.Type.SOCKS5)
                .setAddress("198.51.100.3")
                .setPort(1080)
                .setUser("origuser")
                .setPassword("origpass")
                .build()
            val mockPriorProxy = org.telegram.messenger.SharedConfig.ProxyInfo(mockPriorSettings)
            org.telegram.messenger.SharedConfig.currentProxy = mockPriorProxy
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", true).commit()
            org.telegram.tgnet.ConnectionsManager.setProxySettings(true, mockPriorSettings)

            // Step 1: First enable
            MZGramTorCompanion.applyTorProxySettings(true)
            val prefs = org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("mzgram_config", android.content.Context.MODE_PRIVATE)
            assertTrue(prefs.getBoolean("mzgram_tor_active", false))
            assertEquals("198.51.100.3", prefs.getString("mzgram_tor_prev_ip", null))

            // Step 2: Reset memory state while keeping SharedPreferences active (simulates process restart while Tor is running)
            MZGramTorCompanion.resetTestingState()

            // Step 3: Second enable call (e.g. onAppResume or redundant toggle)
            MZGramTorCompanion.applyTorProxySettings(true)

            // Assert: Saved prior state was NOT overwritten with Tor's address (127.0.0.1:9050)
            assertEquals("Saved prior IP must remain 198.51.100.3 and not be overwritten", "198.51.100.3", prefs.getString("mzgram_tor_prev_ip", null))
            assertEquals(1080, prefs.getInt("mzgram_tor_prev_port", 0))

            // Step 4: Disable Tor companion
            MZGramTorCompanion.applyTorProxySettings(false)

            // Assert: Original proxy restored
            val restoredProxy = org.telegram.messenger.SharedConfig.currentProxy
            assertNotNull(restoredProxy)
            assertEquals("198.51.100.3", restoredProxy.settings.address)
        } finally {
            MZGramTorCompanion.applyTorProxySettings(false)
            MZGramTorCompanion.resetTestingState()
            org.telegram.messenger.SharedConfig.currentProxy = initialProxy
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled", initialEnabled).commit()
            if (initialProxy != null && initialEnabled) {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(true, initialProxy.settings)
            } else {
                org.telegram.tgnet.ConnectionsManager.setProxySettings(false, null)
            }
        }
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
