package org.mzgram.pillstack

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.mzgram.core.CoreConfig
import org.mzgram.core.config.ConfigItem

object PillStackConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    private const val DEFAULT_ACTIVE = "5,6"

    const val CURRENCY_AUTO = "AUTO"

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()


    @JvmField
    val activePillsRaw = addConfig("MZPillActivePills", ConfigItem.configTypeString, DEFAULT_ACTIVE)

    @JvmField
    val hiddenPillsRaw = addConfig("MZPillHiddenPills", ConfigItem.configTypeString, "")

    @JvmField
    val infiniteScrolling = addConfig("MZPillInfiniteScrolling", ConfigItem.configTypeBool, true)

    @JvmField
    val lastActivePillId = addConfig("MZPillLastActiveId", ConfigItem.configTypeInt, -1)


    @JvmField
    val gramTargetCurrency = addConfig("MZPillGramCurrency", ConfigItem.configTypeString, CURRENCY_AUTO)

    @JvmField
    val btcTargetCurrency = addConfig("MZPillBtcCurrency", ConfigItem.configTypeString, CURRENCY_AUTO)

    @JvmField
    val usdTargetCurrency = addConfig("MZPillUsdCurrency", ConfigItem.configTypeString, CURRENCY_AUTO)

    @JvmField
    val ethTargetCurrency = addConfig("MZPillEthCurrency", ConfigItem.configTypeString, CURRENCY_AUTO)

    @JvmField
    val eurTargetCurrency = addConfig("MZPillEurCurrency", ConfigItem.configTypeString, CURRENCY_AUTO)

    @JvmField
    val goldTargetCurrency = addConfig("MZPillGoldCurrency", ConfigItem.configTypeString, CURRENCY_AUTO)

    @JvmField
    val rateInstancesRaw = addConfig("MZPillRateInstances", ConfigItem.configTypeString, "")

    @JvmField
    val rateInstancesMigrated = addConfig("MZPillRateInstancesMigrated", ConfigItem.configTypeBool, false)

    @JvmField
    val goldPriceCache = addConfig("MZPillGoldPriceCache", ConfigItem.configTypeString, "")

    @JvmField
    val goldPriceCacheTime = addConfig("MZPillGoldPriceCacheTime", ConfigItem.configTypeLong, 0L)

    @JvmField
    val ratesCache = addConfig("MZPillRatesCache", ConfigItem.configTypeString, "")

    @JvmField
    val ratesCacheTime = addConfig("MZPillRatesCacheTime", ConfigItem.configTypeLong, 0L)


    @JvmField
    val weatherUseCurrentLocation = addConfig("MZPillWeatherCurrentLocation", ConfigItem.configTypeBool, true)

    @JvmField
    val weatherLatitude = addConfig("MZPillWeatherLat", ConfigItem.configTypeString, "")

    @JvmField
    val weatherLongitude = addConfig("MZPillWeatherLon", ConfigItem.configTypeString, "")

    @JvmField
    val weatherAddress = addConfig("MZPillWeatherAddress", ConfigItem.configTypeString, "")


    @JvmStatic
    fun isConfigLoaded(): Boolean = configLoaded

    @JvmStatic
    fun infiniteScrolling(): Boolean = infiniteScrolling.Bool()

    @JvmStatic
    fun lastActivePillId(): Int = lastActivePillId.Int()

    @JvmStatic
    fun saveLastActivePillId(id: Int) {
        if (lastActivePillId.Int() != id) lastActivePillId.setConfigInt(id)
    }

    @JvmStatic
    fun useCurrentLocation(): Boolean = weatherUseCurrentLocation.Bool()

    @JvmStatic
    fun setUseCurrentLocation(value: Boolean) {
        if (weatherUseCurrentLocation.Bool() != value) weatherUseCurrentLocation.setConfigBool(value)
    }

    @JvmStatic
    fun customWeatherAddress(): String = weatherAddress.String() ?: ""

    @JvmStatic
    fun customWeatherLatitude(): Double = weatherLatitude.String().toDoubleOrNull() ?: Double.NaN

    @JvmStatic
    fun customWeatherLongitude(): Double = weatherLongitude.String().toDoubleOrNull() ?: Double.NaN

    @JvmStatic
    fun hasCustomWeatherLocation(): Boolean =
        !customWeatherLatitude().isNaN() && !customWeatherLongitude().isNaN()

    @JvmStatic
    fun setCustomWeatherLocation(lat: Double, lon: Double, address: String?) {
        weatherLatitude.setConfigString(lat.toString())
        weatherLongitude.setConfigString(lon.toString())
        weatherAddress.setConfigString(address ?: "")
    }


    private val activePills = ArrayList<Int>()
    private val hiddenPills = ArrayList<Int>()
    private val dormantActive = LinkedHashMap<Int, Int>()
    private val dormantHidden = LinkedHashSet<Int>()

    @JvmStatic
    fun getActivePills(): ArrayList<Int> = activePills

    @JvmStatic
    fun getHiddenPills(): ArrayList<Int> = hiddenPills

    @JvmStatic
    fun hasActivePills(): Boolean = synchronized(sync) { activePills.isNotEmpty() }

    @JvmStatic
    fun saveLayout(active: List<Int>, hidden: List<Int>) {
        synchronized(sync) {
            activePills.clear()
            activePills.addAll(active)
            hiddenPills.clear()
            hiddenPills.addAll(hidden)
        }
        persistLayout()
    }

    @JvmStatic
    fun setPillActive(id: Int, active: Boolean) {
        synchronized(sync) {
            activePills.remove(id)
            hiddenPills.remove(id)
            if (active) activePills.add(id) else hiddenPills.add(id)
        }
        persistLayout()
    }

    @JvmStatic
    fun resetLayout() {
        synchronized(sync) {
            activePills.clear()
            activePills.addAll(parseList(DEFAULT_ACTIVE))
            hiddenPills.clear()
            dormantActive.clear()
            dormantHidden.clear()
        }
        sanitizePills()
        persistLayout()
    }

    @JvmStatic
    fun savePillsLayout() {
        persistLayout()
        PillStackEvents.notifyLayoutChanged()
    }

    @JvmStatic
    fun notifySettingsChanged(vararg pillIds: Int) {
        PillStackEvents.notifySettingsChanged(*pillIds)
    }

    private fun persistLayout() {
        synchronized(sync) {
            val active = ArrayList(activePills)
            for ((id, index) in dormantActive) {
                if (!active.contains(id)) active.add(index.coerceAtMost(active.size), id)
            }
            val hidden = ArrayList(hiddenPills)
            for (id in dormantHidden) {
                if (!hidden.contains(id) && !active.contains(id)) hidden.add(id)
            }
            activePillsRaw.setConfigString(serializeList(active))
            hiddenPillsRaw.setConfigString(serializeList(hidden))
        }
    }

    @JvmStatic
    fun sanitizePills() {
        val known = PillRegistry.getRegisteredIds().map { it.toInt() }.toSet()
        synchronized(sync) {
            for (index in activePills.indices) {
                val id = activePills[index]
                if (!known.contains(id) && !dormantActive.containsKey(id)) dormantActive[id] = index
            }
            activePills.retainAll { known.contains(it) }
            for (id in hiddenPills) {
                if (!known.contains(id)) dormantHidden.add(id)
            }
            hiddenPills.retainAll { known.contains(it) && !activePills.contains(it) }
            for ((id, index) in dormantActive.entries.sortedBy { it.value }) {
                if (known.contains(id) && !activePills.contains(id)) {
                    hiddenPills.remove(id)
                    activePills.add(index.coerceAtMost(activePills.size), id)
                }
            }
            dormantActive.keys.removeAll { known.contains(it) }
            dormantHidden.removeAll { known.contains(it) }
            for (id in known) {
                if (!activePills.contains(id) && !hiddenPills.contains(id)) {
                    hiddenPills.add(id)
                }
            }
        }
    }

    private fun parseList(data: String?): List<Int> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()
    }

    private fun serializeList(list: List<Int>): String = list.joinToString(",")

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
            activePills.clear()
            activePills.addAll(parseList(activePillsRaw.String()))
            hiddenPills.clear()
            hiddenPills.addAll(parseList(hiddenPillsRaw.String()).filter { !activePills.contains(it) })
            dormantActive.clear()
            dormantHidden.clear()
            configLoaded = true
        }
        RateInstances.registerAll()
        sanitizePills()
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
            configLoaded = false
        }
        loadConfig(true)
    }
}
