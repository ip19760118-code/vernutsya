package com.vernutsya

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UiMode { SIMPLE, ADVANCED }

enum class TargetIcon(val label: String) {
    HOUSE("Домик"), CAR("Авто"), TENT("Палатка"),
    MUSHROOM("Гриб"), FLAG("Флаг"), STAR("Звезда")
}

data class SettingsState(
    val mode: UiMode = UiMode.SIMPLE,
    val icon: TargetIcon = TargetIcon.HOUSE,
    val keepScreenOn: Boolean = false,
    val sosPhone: String = "",
    val nightMode: Boolean = false,
    val autoEco: Boolean = true,
    val autoCheckUpdate: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val alongTrack: Boolean = false
)

object AppSettings {
    private const val PREFS = "vernutsya_settings"
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching { UiMode.valueOf(prefs.getString("mode", "SIMPLE") ?: "SIMPLE") }.getOrDefault(UiMode.SIMPLE)
        val icon = runCatching { TargetIcon.valueOf(prefs.getString("icon", "HOUSE") ?: "HOUSE") }.getOrDefault(TargetIcon.HOUSE)
        _state.value = SettingsState(
            mode, icon,
            prefs.getBoolean("keep_screen_on", false),
            prefs.getString("sos_phone", "") ?: "",
            prefs.getBoolean("night_mode", false),
            prefs.getBoolean("auto_eco", true),
            prefs.getBoolean("auto_check_update", true),
            prefs.getBoolean("analytics", true),
            prefs.getBoolean("along_track", false)
        )
    }

    fun setMode(v: UiMode) { prefs.edit().putString("mode", v.name).apply(); _state.value = _state.value.copy(mode = v) }
    fun setIcon(v: TargetIcon) { prefs.edit().putString("icon", v.name).apply(); _state.value = _state.value.copy(icon = v) }
    fun setKeepScreenOn(v: Boolean) { prefs.edit().putBoolean("keep_screen_on", v).apply(); _state.value = _state.value.copy(keepScreenOn = v) }
    fun setSosPhone(v: String) { prefs.edit().putString("sos_phone", v).apply(); _state.value = _state.value.copy(sosPhone = v) }
    fun setNightMode(v: Boolean) { prefs.edit().putBoolean("night_mode", v).apply(); _state.value = _state.value.copy(nightMode = v) }
    fun setAutoEco(v: Boolean) { prefs.edit().putBoolean("auto_eco", v).apply(); _state.value = _state.value.copy(autoEco = v) }
    fun setAutoCheckUpdate(v: Boolean) { prefs.edit().putBoolean("auto_check_update", v).apply(); _state.value = _state.value.copy(autoCheckUpdate = v) }
    fun setAnalyticsEnabled(v: Boolean) { prefs.edit().putBoolean("analytics", v).apply(); _state.value = _state.value.copy(analyticsEnabled = v) }
    fun setAlongTrack(v: Boolean) { prefs.edit().putBoolean("along_track", v).apply(); _state.value = _state.value.copy(alongTrack = v) }
}