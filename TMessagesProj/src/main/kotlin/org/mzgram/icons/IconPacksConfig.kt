package org.mzgram.icons

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.mzgram.core.CoreConfig
import org.mzgram.core.config.ConfigItem

object IconPacksConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()

    @JvmField
    val enabled = addConfig("MZIconPacksEnabled", ConfigItem.configTypeBool, false)

    @JvmField
    val activePacks = addConfig("MZIconPacksActive", ConfigItem.configTypeString, "")

    @JvmField
    val editingPackId = addConfig("MZIconPacksEditing", ConfigItem.configTypeString, "")

    @JvmField
    val customNotificationIcon = addConfig("MZIconPacksNotificationIcon", ConfigItem.configTypeBool, false)

    @JvmField
    val useSystemIconShape = addConfig("MZIconPacksSystemShape", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun useSystemIconShape(): Boolean = useSystemIconShape.Bool()

    @JvmStatic
    fun toggleSystemIconShape(): Boolean {
        val value = !useSystemIconShape.Bool()
        useSystemIconShape.setConfigBool(value)
        return value
    }

    @JvmStatic
    fun enabled(): Boolean = enabled.Bool()

    @JvmStatic
    fun currentEditingPackId(): String? {
        val value = editingPackId.String()
        return if (value.isNullOrBlank()) null else value
    }

    @JvmStatic
    fun setEditingPackId(packId: String?) {
        editingPackId.setConfigString(packId ?: "")
    }

    @JvmStatic
    fun isEditing(): Boolean = currentEditingPackId() != null

    @JvmStatic
    fun customNotificationIcon(): Boolean = customNotificationIcon.Bool()

    @JvmStatic
    fun getActivePackIds(): MutableList<String> {
        val raw = activePacks.String()
        if (raw.isBlank()) return ArrayList()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    @JvmStatic
    fun setActivePackIds(ids: List<String>) {
        activePacks.setConfigString(ids.filter { it.isNotBlank() }.joinToString(","))
    }

    @JvmStatic
    fun isPackActive(packId: String): Boolean = getActivePackIds().contains(packId)

    @JvmStatic
    fun togglePack(packId: String): Boolean {
        val ids = getActivePackIds()
        val nowActive: Boolean
        if (ids.remove(packId)) {
            nowActive = false
        } else {
            ids.add(0, packId)
            nowActive = true
        }
        setActivePackIds(ids)
        return nowActive
    }

    @JvmStatic
    fun forgetPack(packId: String) {
        val ids = getActivePackIds()
        if (ids.remove(packId)) {
            setActivePackIds(ids)
        }
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
