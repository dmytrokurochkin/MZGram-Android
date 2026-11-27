package org.mzgram.utils

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.mzgram.core.CoreConfig
import org.mzgram.core.config.ConfigItem
import org.mzgram.ext.ExtConfig

object UtilsConfig {

    /** ActionBarLayout.BACK_ANIMATION_SPRING */
    private const val BACK_ANIMATION_SPRING = 1

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()

    @JvmField
    val predictiveBackIntensity =
        addConfig("MZPredictiveBackIntensity", ConfigItem.configTypeInt, 100)

    @JvmStatic
    fun predictiveBackIntensity(): Float {
        ensureLoaded()
        return (predictiveBackIntensity.Int() / 100f).coerceIn(0f, 2f)
    }

    @JvmStatic
    fun adjustPredictiveBackProgress(progress: Float): Float =
        (progress * predictiveBackIntensity()).coerceIn(0f, 1f)

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
        applyMotionDefaults()
    }

    @JvmStatic
    fun applyMotionDefaults() {
        try {
            val prefs = getPreferences()
            val key = ExtConfig.backAnimationStyle.key
            if (!prefs.contains(key)) {
                ExtConfig.backAnimationStyle.setConfigInt(BACK_ANIMATION_SPRING)
            }
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    @JvmStatic
    fun ensureLoaded() {
        if (!configLoaded) loadConfig(false)
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
            configLoaded = true
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
