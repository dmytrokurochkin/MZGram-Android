package org.telegram.messenger.mzgram.test

import android.accounts.AccountManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

// MZGram has its own package, org.mzgram.messenger, so it installs beside
// Telegram from Google Play (org.telegram.messenger) and from telegram.org
// (org.telegram.messenger.web) instead of being taken for their update. What
// the system ties to an app is under that package: the content providers and
// the contacts account type (only one app may own an account type).
class MZGramPackageTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun package_isMZGramsOwn() {
        assertEquals("org.mzgram.messenger", context.packageName)
        for (telegram in listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta")) {
            assertFalse(telegram, context.packageName == telegram)
        }
    }

    @Test
    fun providers_areUnderThePackage() {
        for (suffix in listOf("provider", "streaming", "notification_image_provider", "call_sound_provider")) {
            val authority = context.packageName + "." + suffix
            val provider = context.packageManager.resolveContentProvider(authority, 0)
            assertNotNull(authority, provider)
            assertEquals(authority, context.packageName, provider!!.packageName)
        }
    }

    @Test
    fun contactsAccountType_isThePackage() {
        val own = AccountManager.get(context).authenticatorTypes.filter { it.packageName == context.packageName }
        assertEquals("MZGram's account types", listOf(context.packageName), own.map { it.type })
    }
}
