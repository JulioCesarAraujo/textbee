package com.textbee.gateway.domain.usecase

import com.textbee.gateway.domain.model.ServerConfig
import com.textbee.gateway.domain.repository.ServerConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetServerConfigUseCase @Inject constructor(
    private val repository: ServerConfigRepository
) {
    operator fun invoke(): Flow<ServerConfig> = repository.getServerConfig()
}
