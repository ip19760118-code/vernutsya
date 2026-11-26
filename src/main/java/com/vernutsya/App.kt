package com.vernutsya

import android.app.Application

/**
 * Инициализация до создания экранов.
 * AppMetrica активируется внутри Analytics.init() — автотрекинг
 * активностей и сессий включается автоматически, отдельных вызовов не требуется.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Analytics.init(this)
    }
}