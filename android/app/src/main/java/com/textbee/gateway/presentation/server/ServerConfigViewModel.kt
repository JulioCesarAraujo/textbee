package com.textbee.gateway.presentation.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.textbee.gateway.domain.model.ConnectionTestResult
import com.textbee.gateway.domain.model.ServerConfig
import com.textbee.gateway.domain.usecase.GetServerConfigUseCase
import com.textbee.gateway.domain.usecase.SaveServerConfigUseCase
import com.textbee.gateway.domain.usecase.TestLocalServerConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerConfigUiState(
    val config: ServerConfig = ServerConfig.default(),
    val isTestingConnection: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isSaving: Boolean = false,
    val userMessage: String? = null
)

@HiltViewModel
class ServerConfigViewModel @Inject constructor(
    private val getServerConfigUseCase: GetServerConfigUseCase,
    private val saveServerConfigUseCase: SaveServerConfigUseCase,
    private val testLocalServerConnectionUseCase: TestLocalServerConnectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerConfigUiState())
    val uiState: StateFlow<ServerConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getServerConfigUseCase().collect { savedConfig ->
                _uiState.update { it.copy(config = savedConfig) }
            }
        }
    }

    fun onPrivateServerToggled(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(isPrivateServerEnabled = enabled)) }
    }

    fun onProtocolChanged(protocol: String) {
        _uiState.update { it.copy(config = it.config.copy(protocol = protocol)) }
    }

    fun onHostChanged(host: String) {
        _uiState.update { it.copy(config = it.config.copy(host = host)) }
    }

    fun onPortChanged(port: String) {
        _uiState.update { it.copy(config = it.config.copy(port = port)) }
    }

    fun onApiTokenChanged(token: String) {
        _uiState.update { it.copy(config = it.config.copy(apiToken = token)) }
    }

    fun onWorkerForegroundToggled(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(isWorkerForegroundEnabled = enabled)) }
    }

    fun onSqliteBufferToggled(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(isSqliteBufferEnabled = enabled)) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, testResult = null) }
            val result = testLocalServerConnectionUseCase(_uiState.value.config)
            _uiState.update { it.copy(isTestingConnection = false, testResult = result) }
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val outcome = saveServerConfigUseCase(_uiState.value.config)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    userMessage = if (outcome.isSuccess) "Configurações aplicadas com sucesso!" else outcome.exceptionOrNull()?.message
                )
            }
        }
    }

    fun resetDefaults() {
        _uiState.update {
            it.copy(
                config = ServerConfig.default(),
                testResult = null,
                userMessage = "Restaurado para padrão oficial Textbee Cloud."
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
