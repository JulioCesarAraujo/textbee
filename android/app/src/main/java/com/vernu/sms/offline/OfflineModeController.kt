package com.vernu.sms.offline

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.vernu.sms.AppConstants
import com.vernu.sms.helpers.SharedPreferenceHelper
import com.vernu.sms.managers.NetworkManager

/**
 * Liga ou desliga a conexão com o servidor local conforme o switch do modo
 * offline (plano 4.1 e 4.2). Sem detecção automática de rede: só o switch decide.
 */
object OfflineModeController {
    private const val TAG = "OfflineModeController"

    fun shouldRun(context: Context): Boolean {
        val network = NetworkManager(context)
        val deviceId = SharedPreferenceHelper.getSharedPreferenceString(
            context, AppConstants.SHARED_PREFS_DEVICE_ID_KEY, ""
        ).orEmpty()
        val apiKey = SharedPreferenceHelper.getSharedPreferenceString(
            context, AppConstants.SHARED_PREFS_API_KEY_KEY, ""
        ).orEmpty()
        return network.isOfflineModeEnabled() &&
            network.getOfflineServerOrigins().isNotEmpty() &&
            deviceId.isNotEmpty() && apiKey.isNotEmpty()
    }

    // Aplica o estado atual das configurações
    fun apply(context: Context) {
        val appContext = context.applicationContext
        if (shouldRun(appContext)) {
            start(appContext, reconnect = false)
        } else {
            appContext.stopService(LocalChannelService.intent(appContext))
            NetworkManager.activeServerOrigin = null
        }
    }

    // Os endereços mudaram: reconecta se o serviço estiver rodando
    fun reconnect(context: Context) {
        val appContext = context.applicationContext
        if (shouldRun(appContext)) start(appContext, reconnect = true) else apply(appContext)
    }

    private fun start(context: Context, reconnect: Boolean) {
        val intent = LocalChannelService.intent(context)
        if (reconnect) intent.action = LocalChannelService.ACTION_RECONNECT
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // Android 12+ não deixa iniciar foreground service com o app em segundo
            // plano; o serviço sobe na próxima vez que o app for aberto
            Log.w(TAG, "Could not start the offline channel service: ${e.message}")
        }
    }
}
