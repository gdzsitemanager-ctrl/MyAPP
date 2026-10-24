package com.webapk.builder

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("Приложение запущено (v${BuildInfo.version(this)})")
    }
}

object BuildInfo {
    fun version(app: android.content.Context): String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
}
