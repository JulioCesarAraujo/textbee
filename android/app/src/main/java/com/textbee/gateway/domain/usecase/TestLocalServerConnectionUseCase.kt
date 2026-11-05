package com.textbee.gateway.domain.usecase

import com.textbee.gateway.domain.model.ConnectionTestResult
import com.textbee.gateway.domain.model.ServerConfig
import com.textbee.gateway.domain.repository.ServerConfigRepository
import javax.inject.Inject

class TestLocalServerConnectionUseCase @Inject constructor(
    private val repository: ServerConfigRepository
) {
    suspend operator fun invoke(config: ServerConfig): ConnectionTestResult {
        if (!config.isValidHost()) {
            return ConnectionTestResult.Error("Host inválido: verifique o endereço IPv4 ou mDNS informado.")
        }
        if (!config.isValidPort()) {
            return ConnectionTestResult.Error("Porta inválida: informe uma porta TCP válida.")
        }
        return repository.testConnection(config)
    }
}
