package com.textbee.gateway.data.repository

import com.textbee.gateway.data.local.ServerPreferencesDataSource
import com.textbee.gateway.domain.model.ConnectionTestResult
import com.textbee.gateway.domain.model.ServerConfig
import com.textbee.gateway.domain.repository.ServerConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class ServerConfigRepositoryImpl @Inject constructor(
    private val localDataSource: ServerPreferencesDataSource,
    private val okHttpClient: OkHttpClient
) : ServerConfigRepository {

    override fun getServerConfig(): Flow<ServerConfig> = localDataSource.serverConfigFlow

    override suspend fun saveServerConfig(config: ServerConfig): Result<Unit> = runCatching {
        localDataSource.saveConfig(config)
    }

    override suspend fun testConnection(config: ServerConfig): ConnectionTestResult = withContext(Dispatchers.IO) {
        val testUrl = "${config.baseUrl}/v1/sms/ready"
        val requestBuilder = Request.Builder().url(testUrl).get()
        if (config.apiToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiToken}")
        }

        try {
            val client = okHttpClient.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            var statusCode = 0
            val latency = measureTimeMillis {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    statusCode = response.code
                }
            }

            if (statusCode in 200..299) {
                ConnectionTestResult.Success(
                    latencyMs = latency,
                    engineVersion = "v2.4.2",
                    endpoint = "/v1/sms/ready",
                    message = "ACK OK"
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
}
