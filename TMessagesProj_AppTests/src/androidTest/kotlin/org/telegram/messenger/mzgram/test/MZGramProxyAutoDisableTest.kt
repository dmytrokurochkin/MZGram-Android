package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramProxyAutoDisable
import org.telegram.messenger.mzgram.MZGramSettingsBackup

// The proxy goes off by itself only on the networks the user picked.
class MZGramProxyAutoDisableTest {

    @Test
    fun shouldDisable_onlyThePickedNetworks() {
        val vpn = MZGramProxyAutoDisable.VPN
        val mobile = MZGramProxyAutoDisable.MOBILE_DATA
        val wifi = MZGramProxyAutoDisable.WIFI
        // Nothing picked: never
        assertFalse(MZGramProxyAutoDisable.shouldDisable(0, true, true, true))
        assertTrue(MZGramProxyAutoDisable.shouldDisable(vpn, true, false, true))
        assertFalse(MZGramProxyAutoDisable.shouldDisable(vpn, false, false, true))
        assertTrue(MZGramProxyAutoDisable.shouldDisable(mobile, false, true, false))
        assertFalse(MZGramProxyAutoDisable.shouldDisable(mobile, false, false, true))
        assertTrue(MZGramProxyAutoDisable.shouldDisable(wifi, false, false, true))
        // A VPN over Wi-Fi with only Wi-Fi picked still counts as Wi-Fi
        assertTrue(MZGramProxyAutoDisable.shouldDisable(wifi, true, false, true))
        assertTrue(MZGramProxyAutoDisable.shouldDisable(vpn or mobile, false, true, false))
        assertFalse(MZGramProxyAutoDisable.shouldDisable(vpn or mobile, false, false, false))
    }

    @Test
    fun flags_areDistinctBits() {
        assertEquals(7, MZGramProxyAutoDisable.ALL)
        assertEquals(MZGramProxyAutoDisable.ALL,
                MZGramProxyAutoDisable.VPN or MZGramProxyAutoDisable.MOBILE_DATA or MZGramProxyAutoDisable.WIFI)
    }

    @Test
    fun backup_keepsTheMaskInRange() {
        assertEquals(5, MZGramSettingsBackup.accepted("mzgram_config", "proxyDisableConditions", 5))
        assertNull(MZGramSettingsBackup.accepted("mzgram_config", "proxyDisableConditions", 8))
        assertNull(MZGramSettingsBackup.accepted("mzgram_config", "autoDisabled", true))
        assertFalse(MZGramSettingsBackup.files().contains("mzgram_proxy"))
    }
}
