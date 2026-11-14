package org.telegram.messenger.mzgram.test

import android.view.KeyEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.UserConfig
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat
import org.telegram.messenger.mzgram.plugins.MZGramPlugins
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.UniversalFragment
import org.telegram.ui.mzgram.MZGramAccountOrderActivity
import org.telegram.ui.mzgram.MZGramAiActivity
import org.telegram.ui.mzgram.MZGramDeletedDialogsActivity
import org.telegram.ui.mzgram.MZGramDeletedMessagesActivity
import org.telegram.ui.mzgram.MZGramDeviceCheckActivity
import org.telegram.ui.mzgram.MZGramDrawerItemsActivity
import org.telegram.ui.mzgram.MZGramFeedActivity
import org.telegram.ui.mzgram.MZGramFeedChannelsActivity
import org.telegram.ui.mzgram.MZGramIconPacksActivity
import org.telegram.ui.mzgram.MZGramMessageDetailsActivity
import org.telegram.ui.mzgram.MZGramMessageHistoryActivity
import org.telegram.ui.mzgram.MZGramPasscodeAccountsActivity
import org.telegram.ui.mzgram.MZGramPeripheralsActivity
import org.telegram.ui.mzgram.MZGramPillsActivity
import org.telegram.ui.mzgram.MZGramPinnedReactionsActivity
import org.telegram.ui.mzgram.MZGramPluginInfoActivity
import org.telegram.ui.mzgram.MZGramPluginJournalActivity
import org.telegram.ui.mzgram.MZGramPluginsActivity
import org.telegram.ui.mzgram.MZGramPluginsCatalogActivity
import org.telegram.ui.mzgram.MZGramPluginSettingsActivity
import org.telegram.ui.mzgram.MZGramPluginSourceActivity
import org.telegram.ui.mzgram.MZGramRegexFiltersActivity
import org.telegram.ui.mzgram.MZGramSettingsActivity
import org.telegram.ui.mzgram.MZGramShadowBanActivity
import org.telegram.ui.mzgram.MZGramTranslationAiActivity
import org.telegram.ui.mzgram.MZGramUnifiedPushActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Phase A3: Parity screen captures for all MZGram settings pages and custom screens
// across both light and dark themes.
class MZGramParityScreensTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var testPluginInstalled = false
    private val testPluginId = "parity_test_plugin"

    @Before
    fun setUp() {
        val self = TLRPC.TL_user().apply {
            id = 7_000_000_001L
            first_name = "MZGram test user"
        }
        UserConfig.getInstance(0).setCurrentUser(self)
        MZGramConfig.checkUpdates = false
        MZGramAppearance.startApp()
    }

    @After
    fun tearDown() {
        if (testPluginInstalled) {
            try {
                MZGramPlugins.delete(testPluginId)
            } catch (ignored: Throwable) {
            }
        }
        MZGramAppearance.reset()
    }

    private fun ensureTestPlugin(): String {
        if (MZGramPlugins.get(testPluginId) == null) {
            val metaJson = """
                {
                    "id": "$testPluginId",
                    "name": "Parity Test Plugin",
                    "version": "1.0.0",
                    "author": "MZGram",
                    "description": "Test plugin for screen capture"
                }
            """.trimIndent()
            val meta = MZGramPluginFormat.metaFromJson(metaJson)!!
            val cacheDir = ApplicationLoader.applicationContext.cacheDir
            val tempFile = File(cacheDir, "$testPluginId.plugin").apply {
                writeText("# parity test plugin code\n")
            }
            val latch = CountDownLatch(1)
            MZGramPlugins.install(tempFile, meta, null, emptyList()) {
                latch.countDown()
            }
            latch.await(5, TimeUnit.SECONDS)
            testPluginInstalled = true
        }
        return testPluginId
    }

    private fun createDummyMessageObject(): MessageObject {
        val message = TLRPC.TL_message().apply {
            id = 1
            dialog_id = 1L
            message = "MZGram parity test message"
            date = (System.currentTimeMillis() / 1000).toInt()
            from_id = TLRPC.TL_peerUser().apply { user_id = UserConfig.getInstance(0).clientUserId }
            peer_id = TLRPC.TL_peerUser().apply { user_id = UserConfig.getInstance(0).clientUserId }
        }
        return MessageObject(0, message, false, false)
    }

    private fun captureWithScrolling(screenName: String, maxScrolls: Int = 6) {
        Thread.sleep(600)
        MZGramScreens.capture("parity-$screenName-0")
        val fragment = MZGramScreens.lastFragment() ?: return
        val list = when (fragment) {
            is UniversalFragment -> MZGramScreens.listViewOf(fragment)
            else -> MZGramScreens.findView(fragment.fragmentView, View::class.java) { it.canScrollVertically(1) }
        } ?: return

        var part = 1
        while (part < maxScrolls) {
            var canScroll = false
            instrumentation.runOnMainSync {
                canScroll = list.canScrollVertically(1)
                if (canScroll) {
                    val scrollAmount = (list.height * 3 / 4).coerceAtLeast(300)
                    list.scrollBy(0, scrollAmount)
                }
            }
            if (!canScroll) break
            Thread.sleep(600)
            MZGramScreens.capture("parity-$screenName-$part")
            part++
        }
    }

    private fun captureAllSettingsPages(themeName: String) {
        val main = MZGramSettingsActivity()
        MZGramScreens.open(main)
        assertTrue("main settings page opened", MZGramScreens.waitFor(30) {
            (MZGramScreens.listViewOf(main)?.childCount ?: 0) > 0
        })
        captureWithScrolling("$themeName-main")

        for (section in MZGramSettingsActivity.MAIN_PAGE_ORDER) {
            instrumentation.runOnMainSync {
                val items = ArrayList<org.telegram.ui.Components.UItem>()
                val fill = MZGramSettingsActivity::class.java.getDeclaredMethod(
                    "fillItems",
                    ArrayList::class.java,
                    org.telegram.ui.Components.UniversalAdapter::class.java
                )
                fill.isAccessible = true
                fill.invoke(main, items, null)
                val row = items.first { it.id == MZGramSettingsActivity.SECTION_ROW_ID + section }
                val onClick = MZGramSettingsActivity::class.java.getDeclaredMethod(
                    "onClick",
                    org.telegram.ui.Components.UItem::class.java,
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
                onClick.isAccessible = true
                onClick.invoke(main, row, null, 0, 0f, 0f)
            }
            val opened = MZGramScreens.waitFor(30) {
                val page = MZGramScreens.lastFragment()
                page is MZGramSettingsActivity && page !== main && (MZGramScreens.listViewOf(page)?.childCount ?: 0) > 0
            }
            assertTrue("section $section opened", opened)
            val sectionName = when (section) {
                MZGramSettingsActivity.SECTION_GENERAL -> "general"
                MZGramSettingsActivity.SECTION_APPEARANCE -> "appearance"
                MZGramSettingsActivity.SECTION_CHATS -> "chats"
                MZGramSettingsActivity.SECTION_ARCHIVE -> "archive"
                MZGramSettingsActivity.SECTION_GHOST_MODE -> "ghost-mode"
                MZGramSettingsActivity.SECTION_ADS_AND_FILTERS -> "ads-and-filters"
                MZGramSettingsActivity.SECTION_PRIVACY -> "privacy"
                MZGramSettingsActivity.SECTION_AI -> "ai"
                MZGramSettingsActivity.SECTION_OTHER -> "other"
                else -> "section-$section"
            }
            captureWithScrolling("$themeName-$sectionName")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertTrue("Back returns to main settings", MZGramScreens.waitFor(30) {
                MZGramScreens.lastFragment() === main
            })
            Thread.sleep(400)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        Thread.sleep(500)
    }

    private fun captureAllCustomScreens(themeName: String) {
        val pluginId = ensureTestPlugin()
        val dummyMsg = createDummyMessageObject()
        val screens: List<Pair<String, () -> BaseFragment>> = listOf(
            "settings-hub" to { MZGramSettingsActivity() },
            "account-order" to { MZGramAccountOrderActivity() },
            "ai-screen" to { MZGramAiActivity.forTest("gemini", "Parity prompt", "<html><body>MZGram Parity Screen</body></html>") },
            "deleted-dialogs" to { MZGramDeletedDialogsActivity() },
            "deleted-messages" to { MZGramDeletedMessagesActivity(null, 1L) },
            "device-check" to { MZGramDeviceCheckActivity() },
            "drawer-items" to { MZGramDrawerItemsActivity() },
            "feed" to { MZGramFeedActivity() },
            "feed-channels" to { MZGramFeedChannelsActivity() },
            "icon-packs" to { MZGramIconPacksActivity() },
            "message-details" to { MZGramMessageDetailsActivity(dummyMsg) },
            "message-history" to { MZGramMessageHistoryActivity(0, dummyMsg) },
            "passcode-accounts" to { MZGramPasscodeAccountsActivity() },
            "peripherals" to { MZGramPeripheralsActivity() },
            "pills" to { MZGramPillsActivity() },
            "pinned-reactions" to { MZGramPinnedReactionsActivity() },
            "plugin-info" to { MZGramPluginInfoActivity(pluginId) },
            "plugin-journal" to { MZGramPluginJournalActivity(pluginId) },
            "plugins" to { MZGramPluginsActivity() },
            "plugins-catalog" to { MZGramPluginsCatalogActivity() },
            "plugin-settings" to { MZGramPluginSettingsActivity(pluginId, "Settings") },
            "plugin-source" to { MZGramPluginSourceActivity(pluginId) },
            "regex-filters" to { MZGramRegexFiltersActivity() },
            "shadow-ban" to { MZGramShadowBanActivity() },
            "translation-ai" to { MZGramTranslationAiActivity() },
            "unified-push" to { MZGramUnifiedPushActivity() },
        )

        for ((name, factory) in screens) {
            val fragment = factory()
            MZGramScreens.open(fragment)
            val shown = MZGramScreens.waitFor(20) {
                val last = MZGramScreens.lastFragment()
                last != null && last::class.java == fragment::class.java && last.fragmentView?.isShown == true
            }
            if (shown) {
                captureWithScrolling("$themeName-$name")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                Thread.sleep(400)
            }
        }
    }

    @Test
    fun testParityScreens_lightTheme() {
        MZGramAppearance.apply("Blue", Theme.DEFALT_THEME_ACCENT_ID)
        val problems = MZGramScreens.settingsPages("parity-light", MZGramSettingsActivity.MAIN_PAGE_ORDER.toList())
        assertTrue("settingsPages should succeed without problems: $problems", problems.isEmpty())
        captureAllSettingsPages("light")
        captureAllCustomScreens("light")
    }

    @Test
    fun testParityScreens_darkTheme() {
        MZGramAppearance.apply("Dark Blue", Theme.DEFALT_THEME_ACCENT_ID)
        val problems = MZGramScreens.settingsPages("parity-dark", MZGramSettingsActivity.MAIN_PAGE_ORDER.toList())
        assertTrue("settingsPages should succeed without problems: $problems", problems.isEmpty())
        captureAllSettingsPages("dark")
        captureAllCustomScreens("dark")
    }
}
