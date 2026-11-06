package com.textbee.gateway.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.textbee.gateway.domain.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "textbee_server_config")

@Singleton
class ServerPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PRIVATE_SERVER_ENABLED = booleanPreferencesKey("private_server_enabled")
        val PROTOCOL = stringPreferencesKey("protocol")
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val API_TOKEN = stringPreferencesKey("api_token")
        val WORKER_FOREGROUND = booleanPreferencesKey("worker_foreground")
        val SQLITE_BUFFER = booleanPreferencesKey("sqlite_buffer")
    }

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            isPrivateServerEnabled = prefs[Keys.PRIVATE_SERVER_ENABLED] ?: true,
            protocol = prefs[Keys.PROTOCOL] ?: "http://",
            host = prefs[Keys.HOST] ?: "192.168.1.150",
            port = prefs[Keys.PORT] ?: "8080",
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            isWorkerForegroundEnabled = prefs[Keys.WORKER_FOREGROUND] ?: true,
            isSqliteBufferEnabled = prefs[Keys.SQLITE_BUFFER] ?: true
        )
    }

    suspend fun saveConfig(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRIVATE_SERVER_ENABLED] = config.isPrivateServerEnabled
            prefs[Keys.PROTOCOL] = config.protocol
            prefs[Keys.HOST] = config.host
            prefs[Keys.PORT] = config.port
            prefs[Keys.API_TOKEN] = config.apiToken
            prefs[Keys.WORKER_FOREGROUND] = config.isWorkerForegroundEnabled
            prefs[Keys.SQLITE_BUFFER] = config.isSqliteBufferEnabled
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
