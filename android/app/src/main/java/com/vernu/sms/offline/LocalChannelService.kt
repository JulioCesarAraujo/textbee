package com.vernu.sms.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vernu.sms.AppConstants
import com.vernu.sms.R
import com.vernu.sms.helpers.SharedPreferenceHelper
import com.vernu.sms.managers.NetworkManager
import com.vernu.sms.ui.main.NewMainActivity
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Mantém o WebSocket com o servidor local aberto (plano 2.3). Roda só com o
 * modo offline ligado; o OfflineModeController liga e desliga.
 */
class LocalChannelService : Service(), MessageChannel.Listener {

    private var channel: LocalChannel? = null
    private var sweeper: ScheduledExecutorService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground(getString(R.string.app_name) + ": conectando ao servidor offline…")

        val deviceId = SharedPreferenceHelper.getSharedPreferenceString(
            this, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, ""
        ) ?: ""
        val network = NetworkManager(this)
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            // Detecta conexão morta mesmo sem tráfego
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val localChannel = LocalChannel(client, deviceId) {
            val deviceApiKey = SharedPreferenceHelper.getSharedPreferenceString(
                this, AppConstants.SHARED_PREFS_API_KEY_KEY, ""
            ) ?: ""
            network.getOfflineServerOrigins().map { origin ->
                LocalChannel.Endpoint(
                    webSocketUrl = NetworkManager.toWebSocketUrl(origin),
                    apiKey = network.getApiKeyFor(origin, deviceApiKey),
                )
            }
        }
        localChannel.onMessage(this)
        OfflineOperationProcessor.attach(localChannel)
        localChannel.connect()
        channel = localChannel

        // Confere de tempos em tempos operações paradas (ex.: app morto no meio do envio)
        sweeper = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({
                OfflineOperationProcessor.execute { OfflineOperationProcessor.sweep(applicationContext) }
            }, 1, 5, TimeUnit.MINUTES)
        }
        Log.i(TAG, "Offline channel service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECONNECT) {
            // Endereços mudaram: fecha e abre de novo com a configuração atual
            channel?.disconnect()
            NetworkManager.activeServerOrigin = null
            channel?.connect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sweeper?.shutdownNow()
        channel?.let {
            OfflineOperationProcessor.detach(it)
            it.shutdown()
        }
        channel = null
        NetworkManager.activeServerOrigin = null
        Log.i(TAG, "Offline channel service stopped")
        super.onDestroy()
    }

    override fun onConnected(channel: MessageChannel, serverOrigin: String) {
        NetworkManager.activeServerOrigin = serverOrigin
        updateNotification("Modo offline: conectado a $serverOrigin")
        OfflineOperationProcessor.onConnected(applicationContext)
    }

    override fun onMessage(channel: MessageChannel, text: String) {
        OfflineOperationProcessor.onFrame(applicationContext, text)
    }

    override fun onDisconnected(channel: MessageChannel, reason: String) {
        updateNotification("Modo offline: reconectando ao servidor…")
    }

    private fun startInForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, NewMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("textbee — modo offline")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "Modo offline", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val TAG = "LocalChannelService"
        private const val NOTIFICATION_CHANNEL_ID = "offline_mode"
        private const val NOTIFICATION_ID = 7392
        const val ACTION_RECONNECT = "com.vernu.sms.offline.RECONNECT"

        fun intent(context: Context) = Intent(context, LocalChannelService::class.java)
    }
}
