package com.textbee.gateway.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.textbee.gateway.domain.usecase.GetServerConfigUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class SmsPollingForegroundService : Service() {

    @Inject
    lateinit var getServerConfigUseCase: GetServerConfigUseCase

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        listenLocalQueue()
    }

    private fun listenLocalQueue() {
        serviceScope.launch {
            getServerConfigUseCase().collect { config ->
                if (config.isWorkerForegroundEnabled && config.isPrivateServerEnabled) {
                    // Polling or long-polling local server: config.baseUrl + "/v1/sms/queue"
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Textbee Gateway Ativo")
            .setContentText("Ouvindo mensagens SMS no servidor local.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Textbee Gateway Foreground Worker",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "textbee_gateway_worker"
        const val NOTIFICATION_ID = 1001
    }
}
