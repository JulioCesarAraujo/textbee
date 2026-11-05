package com.textbee.gateway.domain.repository

import com.textbee.gateway.domain.model.ConnectionTestResult
import com.textbee.gateway.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerConfigRepository {
    fun getServerConfig(): Flow<ServerConfig>
    suspend fun saveServerConfig(config: ServerConfig): Result<Unit>
    suspend fun testConnection(config: ServerConfig): ConnectionTestResult
    suspend fun resetToDefaults(): Result<Unit>
}
