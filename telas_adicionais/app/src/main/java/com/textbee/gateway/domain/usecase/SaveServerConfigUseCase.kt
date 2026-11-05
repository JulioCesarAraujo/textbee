package com.textbee.gateway.domain.usecase

import com.textbee.gateway.domain.model.ServerConfig
import com.textbee.gateway.domain.repository.ServerConfigRepository
import javax.inject.Inject

class SaveServerConfigUseCase @Inject constructor(
    private val repository: ServerConfigRepository
) {
    suspend operator fun invoke(config: ServerConfig): Result<Unit> {
        if (config.isPrivateServerEnabled) {
            if (!config.isValidHost()) {
                return Result.failure(IllegalArgumentException("Endereço host inválido. Use um IPv4 ou domínio mDNS."))
            }
            if (!config.isValidPort()) {
                return Result.failure(IllegalArgumentException("Porta TCP inválida. Informe um valor entre 1 e 65535."))
            }
        }
        return repository.saveServerConfig(config)
    }
}
