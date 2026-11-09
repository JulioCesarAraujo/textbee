package com.vernu.sms.managers

import android.content.Context
import android.content.SharedPreferences
import com.vernu.sms.BuildConfig

/**
 * Escolhe o servidor que o app usa (plano 6.3).
 *
 * - Modo offline desligado: API de produção (BuildConfig.API_BASE_URL).
 * - Modo offline ligado: o servidor offline (OFFLINE_MODE_API_URL). Se ele
 *   estiver vazio, o servidor local configurado na tela "Servidor local".
 * - Com o fallback ligado, o servidor local também entra como alternativa
 *   quando o servidor offline não responde (plano 6.4).
 *
 * É a única fonte dessas preferências: o OfflineModeManager e a tela
 * "Servidor local" leem e gravam aqui.
 */
class NetworkManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // URL base da API (termina em /api/v1/) que o Retrofit deve usar agora
    fun getServerUrl(): String {
        if (!isOfflineModeEnabled()) return getProductionApiUrl()
        val active = activeServerOrigin
        if (active != null && active in getOfflineServerOrigins()) return toApiBaseUrl(active)
        return getOfflineServerOrigins().firstOrNull()?.let { toApiBaseUrl(it) }
            ?: getProductionApiUrl()
    }

    fun getProductionApiUrl(): String = BuildConfig.API_BASE_URL

    // Servidores do modo offline, em ordem de preferência, como origem (scheme://host:porta)
    fun getOfflineServerOrigins(): List<String> {
        val primary = toOrigin(getOfflineModeApiUrl())
        val local = toOrigin(getLocalServerUrl())
        val origins = mutableListOf<String>()
        if (primary != null) origins.add(primary)
        if (local != null && (primary == null || isLocalWebSocketFallback())) origins.add(local)
        return origins.distinct()
    }

    // Endereços ws(s):// que o LocalChannel tenta, na mesma ordem
    fun getWebSocketUrls(): List<String> = getOfflineServerOrigins().map { toWebSocketUrl(it) }

    // Chave enviada no handshake: o token da tela "Servidor local" vale só para o servidor local
    fun getApiKeyFor(origin: String, deviceApiKey: String): String {
        val token = getLocalServerToken()
        return if (token.isNotBlank() && origin == toOrigin(getLocalServerUrl())) token else deviceApiKey
    }

    fun getOfflineModeApiUrl(): String = prefs.getString(KEY_OFFLINE_URL, "") ?: ""
    fun isOfflineModeEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun isEmailVerificationSkipped(): Boolean = prefs.getBoolean(KEY_SKIP_EMAIL, false)
    fun isLocalWebSocketFallback(): Boolean = prefs.getBoolean(KEY_WS_FALLBACK, true)
    fun getLocalServerUrl(): String = prefs.getString(KEY_LOCAL_URL, "") ?: ""
    fun getLocalServerToken(): String = prefs.getString(KEY_LOCAL_TOKEN, "") ?: ""

    fun setOfflineModeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    fun setOfflineModeApiUrl(url: String) = prefs.edit().putString(KEY_OFFLINE_URL, url.trim()).apply()
    fun setEmailVerificationSkipped(skip: Boolean) = prefs.edit().putBoolean(KEY_SKIP_EMAIL, skip).apply()
    fun setLocalWebSocketFallback(fallback: Boolean) = prefs.edit().putBoolean(KEY_WS_FALLBACK, fallback).apply()

    fun setLocalServer(url: String, token: String) {
        prefs.edit()
            .putString(KEY_LOCAL_URL, url.trim())
            .putString(KEY_LOCAL_TOKEN, token.trim())
            .apply()
    }

    companion object {
        const val PREFS_NAME = "offline_mode_prefs"
        private const val KEY_ENABLED = "OFFLINE_MODE_ENABLED"
        private const val KEY_OFFLINE_URL = "OFFLINE_MODE_API_URL"
        private const val KEY_SKIP_EMAIL = "OFFLINE_MODE_SKIP_EMAIL_VERIFICATION"
        private const val KEY_WS_FALLBACK = "OFFLINE_MODE_LOCAL_WS_FALLBACK"
        private const val KEY_LOCAL_URL = "LOCAL_SERVER_URL"
        private const val KEY_LOCAL_TOKEN = "LOCAL_SERVER_TOKEN"

        // Caminho do WebSocket no backend (LOCAL_GATEWAY_PATH, padrão /ws)
        const val WEBSOCKET_PATH = "/ws"

        // Servidor em que o WebSocket está conectado agora. A API REST segue o
        // mesmo servidor, para que o fallback valha também para o HTTP.
        @Volatile
        var activeServerOrigin: String? = null

        /** "http://192.168.0.10:3001/api/v1" -> "http://192.168.0.10:3001" */
        @JvmStatic
        fun toOrigin(url: String?): String? {
            var value = url?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (!value.contains("://")) value = "http://$value"
            val parsed = runCatching { java.net.URI(value) }.getOrNull() ?: return null
            val scheme = parsed.scheme?.lowercase() ?: return null
            val host = parsed.host ?: return null
            if (scheme != "http" && scheme != "https") return null
            val port = if (parsed.port > 0) ":${parsed.port}" else ""
            return "$scheme://$host$port"
        }

        @JvmStatic
        fun toApiBaseUrl(origin: String): String = "$origin/api/v1/"

        @JvmStatic
        fun toWebSocketUrl(origin: String): String =
            origin.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + WEBSOCKET_PATH

        @JvmStatic
        fun originOfWebSocketUrl(wsUrl: String): String =
            wsUrl.removeSuffix(WEBSOCKET_PATH)
                .replaceFirst("wss://", "https://")
                .replaceFirst("ws://", "http://")
    }
}
