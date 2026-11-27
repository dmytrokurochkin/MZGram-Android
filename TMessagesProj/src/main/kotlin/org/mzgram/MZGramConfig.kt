package org.mzgram

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.mzgram.core.CoreConfig
import org.mzgram.proxy.ProxyDisableCondition
import org.mzgram.core.config.ConfigItem

object MZGramConfig {

    private const val LEGACY_PROXY_VPN_KEY = "DisableProxyWhenVpnEnabled"

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()


    @JvmField
    val relativeLastSeen = addConfig("MZRelativeLastSeen", ConfigItem.configTypeBool, false)


    @JvmField
    val addCommaAfterMention = addConfig("MZAddCommaAfterMention", ConfigItem.configTypeBool, true)


    @JvmField
    val downloadEmojiPacks = addConfig("MZDownloadEmojiPacks", ConfigItem.configTypeBool, false)


    @JvmStatic
    fun downloadEmojiPacks(): Boolean = downloadEmojiPacks.Bool()

    @JvmStatic
    fun relativeLastSeen(): Boolean = relativeLastSeen.Bool()

    @JvmStatic
    fun addCommaAfterMention(): Boolean = addCommaAfterMention.Bool()


    @JvmField
    val proxyDisableConditions = addConfig("MZProxyDisableConditions", ConfigItem.configTypeInt, 0)

    @JvmStatic
    fun isProxyDisabledOn(condition: ProxyDisableCondition): Boolean =
        (proxyDisableConditions.Int() and condition.flag) != 0

    @JvmStatic
    fun setProxyDisabledOn(condition: ProxyDisableCondition, disabled: Boolean) {
        val current = proxyDisableConditions.Int()
        val updated = if (disabled) current or condition.flag else current and condition.flag.inv()
        if (updated != current) {
            proxyDisableConditions.setConfigInt(updated)
        }
    }

    @JvmStatic
    fun hasProxyDisableConditions(): Boolean = proxyDisableConditions.Int() != 0

    private fun migrateProxyConditions(preferences: SharedPreferences) {
        if (!preferences.contains(LEGACY_PROXY_VPN_KEY)) return
        if (preferences.getBoolean(LEGACY_PROXY_VPN_KEY, false)) {
            val migrated = proxyDisableConditions.Int() or ProxyDisableCondition.VPN.flag
            proxyDisableConditions.value = migrated
            preferences.edit().putInt(proxyDisableConditions.key, migrated).apply()
        }
        preferences.edit().remove(LEGACY_PROXY_VPN_KEY).apply()
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
            migrateProxyConditions(preferences)
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
