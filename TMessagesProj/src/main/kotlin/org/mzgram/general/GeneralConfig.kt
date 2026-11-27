package org.mzgram.general

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.mzgram.core.CoreConfig
import org.mzgram.core.config.ConfigItem
import org.mzgram.ext.ExtConfig

object GeneralConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()

    @JvmField
    val botPrivacyEnabled = addConfig("MZGeneralBotPrivacyEnabled", ConfigItem.configTypeBool, false)

    @JvmField
    val botPrivacyAlwaysAsk = addConfig("MZGeneralBotPrivacyAlwaysAsk", ConfigItem.configTypeBool, false)

    // Telegram user ID, never an account slot (slots are reused after logout).
    @JvmField
    val botPrivacyUserId = addConfig("MZGeneralBotPrivacyUserId", ConfigItem.configTypeLong, 0L)

    @JvmField
    val downloadSpeedBoost = addConfig("MZGeneralDownloadSpeedBoost", ConfigItem.configTypeInt, 0)

    @JvmStatic
    fun downloadSpeedBoost(): Int = downloadSpeedBoost.Int()

    @JvmField
    val showMZGramSettings = addConfig("MZGeneralShowMZGramSettings", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun showMZGramSettings(): Boolean {
        loadConfig(false)
        return showMZGramSettings.Bool()
    }

    @JvmField
    val crashReports = addConfig("MZGeneralCrashReports", ConfigItem.configTypeBool, false)

    @JvmField
    val contentCapture = addConfig("MZGeneralContentCapture", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun contentCapture(): Boolean {
        loadConfig(false)
        return contentCapture.Bool()
    }

    @JvmStatic
    fun crashReports(): Boolean {
        loadConfig(false)
        return crashReports.Bool()
    }

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
    }

    @JvmStatic
    fun loadConfig(force: Boolean) {
        synchronized(sync) {
            if (configLoaded && !force) return
            if (ApplicationLoader.applicationContext == null) return
            val preferences = getPreferences()
            for (item in configs) {
                try {
                    when (item.type) {
                        ConfigItem.configTypeBool ->
                            item.value = preferences.getBoolean(item.key, item.defaultValue as Boolean)

                        ConfigItem.configTypeInt ->
                            item.value = preferences.getInt(item.key, item.defaultValue as Int)

                        ConfigItem.configTypeLong ->
                            item.value = preferences.getLong(item.key, item.defaultValue as Long)

                        ConfigItem.configTypeFloat ->
                            item.value = preferences.getFloat(item.key, item.defaultValue as Float)

                        ConfigItem.configTypeString ->
                            item.value = preferences.getString(item.key, item.defaultValue as String?)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            // Older test builds used the dedicated-account flag as the master switch for Always ask.
            if (botPrivacyAlwaysAsk.Bool() && botPrivacyEnabled.Bool()) {
                botPrivacyEnabled.setConfigBool(false)
            }
            configLoaded = true
        }
        migrateLegacyKeys()
    }

    private fun migrateLegacyKeys() {
        if (getPreferences().getBoolean("enhancedFileLoader", false)) {
            CoreConfig.enhancedFileLoader.setConfigBool(false)
            if (downloadSpeedBoost.Int() == 0) {
                downloadSpeedBoost.setConfigInt(1)
            }
        }
        if (ExtConfig.disableCrashlyticsCollection.Bool()) {
            crashReports.setConfigBool(false)
            ExtConfig.disableCrashlyticsCollection.setConfigBool(false)
        }
        if (showMZGramSettings.Bool()) {
            showMZGramSettings.setConfigBool(false)
        }
    }

    @JvmStatic
    fun reset() {
        synchronized(sync) {
            val editor = getPreferences().edit()
            for (item in configs) {
                editor.remove(item.key)
                item.value = item.defaultValue
            }
            editor.apply()
        }
    }
}
