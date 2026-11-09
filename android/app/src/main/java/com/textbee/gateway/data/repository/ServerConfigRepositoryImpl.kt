package com.textbee.gateway.data.repository

import android.content.Context
import com.textbee.gateway.data.local.ServerPreferencesDataSource
import com.textbee.gateway.domain.model.ConnectionTestResult
import com.textbee.gateway.domain.model.ServerConfig
import com.textbee.gateway.domain.repository.ServerConfigRepository
import com.vernu.sms.managers.NetworkManager
import com.vernu.sms.offline.OfflineModeController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class ServerConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDataSource: ServerPreferencesDataSource,
    private val okHttpClient: OkHttpClient
) : ServerConfigRepository {

    override fun getServerConfig(): Flow<ServerConfig> = localDataSource.serverConfigFlow

    // Grava a tela e repassa ao NetworkManager, que o restante do app usa:
    // o switch é o mesmo do modo offline e o endereço vira o servidor local
    override suspend fun saveServerConfig(config: ServerConfig): Result<Unit> = runCatching {
        localDataSource.saveConfig(config)
        val network = NetworkManager(context)
        network.setLocalServer(config.baseUrl, config.apiToken)
        network.setOfflineModeEnabled(config.isPrivateServerEnabled)
        OfflineModeController.reconnect(context)
    }

    override suspend fun testConnection(config: ServerConfig): ConnectionTestResult = withContext(Dispatchers.IO) {
        val testUrl = "${config.baseUrl}$STATUS_ENDPOINT"
        val requestBuilder = Request.Builder().url(testUrl).get()
        if (config.apiToken.isNotBlank()) {
            requestBuilder.addHeader("x-api-key", config.apiToken)
        }

        try {
            val client = okHttpClient.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            var statusCode = 0
            var body = ""
            val latency = measureTimeMillis {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    statusCode = response.code
                    body = response.body?.string().orEmpty()
                }
            }

            if (statusCode in 200..299) {
                val data = runCatching { JSONObject(body).getJSONObject("data") }.getOrNull()
                val gatewayEnabled = data?.optJSONObject("localGateway")?.optBoolean("enabled") == true
                ConnectionTestResult.Success(
                    latencyMs = latency,
                    engineVersion = data?.optString("deliveryMode")?.ifEmpty { null } ?: "—",
                    endpoint = STATUS_ENDPOINT,
                    message = if (gatewayEnabled) "WebSocket local ativo"
                    else "Servidor respondeu, mas o WebSocket local está desligado (OFFLINE_MODE_ENABLED=false)"
                )
            } else {
                ConnectionTestResult.Error(
                    message = "Servidor respondeu com código HTTP ${statusCode}.",
                    errorCode = statusCode
                )
            }
        } catch (e: Exception) {
            ConnectionTestResult.Error(
                message = "Falha ao conectar em ${config.baseUrl}: ${e.localizedMessage ?: "Tempo limite esgotado"}",
                exception = e
            )
        }
    }

    override suspend fun resetToDefaults(): Result<Unit> = runCatching {
        localDataSource.saveConfig(ServerConfig.default())
    }

    private companion object {
        const val STATUS_ENDPOINT = "/api/v1/gateway/offline/status"
    }
}
