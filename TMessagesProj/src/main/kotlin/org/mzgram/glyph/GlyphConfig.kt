package org.mzgram.glyph

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.mzgram.core.CoreConfig
import org.mzgram.core.config.ConfigItem

object GlyphConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()

    @JvmField
    val enabled = addConfig("MZGlyphEnabled", ConfigItem.configTypeBool, false)

    @JvmField
    val onNewMessage = addConfig("MZGlyphNewMessage", ConfigItem.configTypeBool, true)

    @JvmField
    val onRecording = addConfig("MZGlyphRecording", ConfigItem.configTypeBool, true)

    @JvmField
    val onCall = addConfig("MZGlyphCalls", ConfigItem.configTypeBool, true)

    @JvmField
    val screenOffOnly = addConfig("MZGlyphScreenOff", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun enabled(): Boolean = enabled.Bool()

    @JvmStatic
    fun onNewMessage(): Boolean = onNewMessage.Bool()

    @JvmStatic
    fun onRecording(): Boolean = onRecording.Bool()

    @JvmStatic
    fun onCall(): Boolean = onCall.Bool()

    @JvmStatic
    fun screenOffOnly(): Boolean = screenOffOnly.Bool()

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
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            configLoaded = true
        }
    }
}
