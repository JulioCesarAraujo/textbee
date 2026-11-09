package com.vernu.sms.managers

import android.content.Context

/**
 * Leitura das flags do modo offline (plano 6.3). Usa as mesmas preferências do
 * NetworkManager, para que o que uma tela grava a outra enxergue.
 */
class OfflineModeManager(context: Context) {
    private val network = NetworkManager(context)

    fun isOfflineModeEnabled(): Boolean = network.isOfflineModeEnabled()
    fun setOfflineModeEnabled(enabled: Boolean) = network.setOfflineModeEnabled(enabled)

    fun getOfflineModeApiUrl(): String = network.getOfflineModeApiUrl()
    fun setOfflineModeApiUrl(url: String) = network.setOfflineModeApiUrl(url)

    fun isEmailVerificationSkipped(): Boolean = network.isEmailVerificationSkipped()
    fun setEmailVerificationSkipped(skip: Boolean) = network.setEmailVerificationSkipped(skip)

    fun isLocalWebSocketFallback(): Boolean = network.isLocalWebSocketFallback()
    fun setLocalWebSocketFallback(fallback: Boolean) = network.setLocalWebSocketFallback(fallback)

    // Modo offline ligado e com pelo menos um servidor para conectar
    fun hasValidOfflineModeConfig(): Boolean =
        isOfflineModeEnabled() && network.getOfflineServerOrigins().isNotEmpty()
}
