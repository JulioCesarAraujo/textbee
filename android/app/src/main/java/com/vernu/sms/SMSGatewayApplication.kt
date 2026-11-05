package com.vernu.sms

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.vernu.sms.helpers.SettingsChangeWatcher
import dagger.hilt.android.HiltAndroidApp

/**
 * Application do app. Portada de Java para Kotlin porque o Hilt (via KSP)
 * só processa @HiltAndroidApp em código Kotlin.
 */
@HiltAndroidApp
class SMSGatewayApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Inicializa o WorkManager cedo (necessário para o heartbeat em background)
        try {
            WorkManager.initialize(this, workManagerConfiguration())
            Log.d(TAG, "WorkManager initialized successfully")
        } catch (e: IllegalStateException) {
            // Já inicializado (ex.: androidx.startup) — pode continuar
            Log.d(TAG, "WorkManager already initialized or will be initialized automatically")
        }

        registerActivityLifecycleCallbacks(SettingsChangeWatcher())
    }

    private fun workManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun getWorkManagerConfiguration(): Configuration = workManagerConfiguration()

    companion object {
        private const val TAG = "SMSGatewayApplication"
    }
}
