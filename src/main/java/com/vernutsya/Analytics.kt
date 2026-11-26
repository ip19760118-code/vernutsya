package com.vernutsya

import android.content.Context
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

/**
 * Анонимная статистика через Яндекс AppMetrica (серверы в РФ).
 * Передаются названия действий, версия приложения, названия экранов,
 * округлённые длина и длительность прогулки.
 * Координаты, трек, названия точек, фото, заметки и номер SOS не передаются.
 * Отключается переключателем в настройках.
 */
object Analytics {

    // API-ключ кабинета AppMetrica — в двойных кавычках
    private const val API_KEY = "ad4e9cc6-9997-4f9c-865d-817e0b84cbdd"

    private lateinit var appCtx: Context
    private var version = ""
    private var ready = false
    private var activated = false
    private var lastSending: Boolean? = null

    fun enabled(): Boolean = activated && AppSettings.state.value.analyticsEnabled

    fun status(): String = if (enabled()) "включена" else "выключена"

    fun init(context: Context) {
        if (ready) return
        appCtx = context.applicationContext
        version = runCatching {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        ready = true
        if (API_KEY.isBlank()) return
        runCatching {
            val config = AppMetricaConfig.newConfigBuilder(API_KEY).build()
            AppMetrica.activate(appCtx, config)
            activated = true
        }
        applyToggle()
    }

    fun applyToggle() {
        if (!activated) return
        val on = AppSettings.state.value.analyticsEnabled
        if (lastSending != on) {
            runCatching { AppMetrica.setDataSendingEnabled(on) }
            lastSending = on
        }
    }

    fun screen(name: String) = log("screen_view", mapOf("screen_name" to name))

    fun log(name: String, params: Map<String, Any> = emptyMap()) {
        if (!activated) return
        applyToggle()
        if (!AppSettings.state.value.analyticsEnabled) return

        val m = HashMap<String, Any>()
        m["app_version"] = version
        params.forEach { (k, v) ->
            when (k) {
                "meters" -> m["walk_meters"] = ((v as? Number)?.toInt() ?: 0) / 10 * 10
                "minutes" -> m["walk_minutes"] = (v as? Number)?.toInt() ?: 0
                "saved" -> m["walk_saved"] = if (v == true) "yes" else "no"
                "app_version" -> m["app_version"] = v.toString().take(16)
                else -> when (v) {
                    is Int -> m[k] = v
                    is Long -> m[k] = v
                    is Double -> m[k] = v
                    is Float -> m[k] = v.toDouble()
                    is Boolean -> m[k] = if (v) "yes" else "no"
                    else -> m[k] = v.toString().take(100)
                }
            }
        }
        runCatching { AppMetrica.reportEvent(name, m) }
    }
}