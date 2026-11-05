package com.textbee.gateway.domain.model

sealed interface ConnectionTestResult {
    data class Success(
        val latencyMs: Long,
        val engineVersion: String = "v2.4.2",
        val endpoint: String = "/v1/sms/ready",
        val message: String = "ACK OK"
    ) : ConnectionTestResult

    data class Error(
        val message: String,
        val errorCode: Int? = null,
        val exception: Throwable? = null
    ) : ConnectionTestResult
}
