package com.textbee.gateway.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.textbee.gateway.domain.usecase.GetServerConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isGatewayReady: Boolean = true,
    val isOfflineModeActive: Boolean = false,
    val connectedHost: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getServerConfigUseCase: GetServerConfigUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getServerConfigUseCase().collect { config ->
                _uiState.update {
                    it.copy(
                        isOfflineModeActive = config.isPrivateServerEnabled,
                        connectedHost = if (config.isPrivateServerEnabled) config.baseUrl else null
                    )
                }
            }
        }
    }
}
