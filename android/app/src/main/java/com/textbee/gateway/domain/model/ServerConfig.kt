package com.textbee.gateway.domain.model

data class ServerConfig(
    val isPrivateServerEnabled: Boolean = true,
    val protocol: String = "http://",
    val host: String = "192.168.1.150",
    val port: String = "8080",
    val apiToken: String = "",
    val isWorkerForegroundEnabled: Boolean = true,
    val isSqliteBufferEnabled: Boolean = true,
    val lastTestedAt: Long? = null,
    val lastLatencyMs: Long? = null
) {
    /**
     * Resolves the full REST base URL for the local gateway daemon
     */
    val baseUrl: String
        get() {
            val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
            val cleanPort = port.trim()
            return if (cleanPort.isNotBlank()) {
                "${protocol}${cleanHost}:${cleanPort}"
            } else {
                "${protocol}${cleanHost}"
            }
        }

    fun isValidHost(): Boolean {
        if (host.isBlank()) return false
        val cleanHost = host.trim()
        val ipPattern = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$".toRegex()
        val domainPattern = "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$".toRegex()
        val mDnsPattern = "^[a-zA-Z0-9\\-_]+\\.local$".toRegex()
        return ipPattern.matches(cleanHost) || domainPattern.matches(cleanHost) || mDnsPattern.matches(cleanHost) || cleanHost == "localhost"
    }

    fun isValidPort(): Boolean {
        val portInt = port.toIntOrNull() ?: return false
        return portInt in 1..65535
    }

    companion object {
        fun default(): ServerConfig = ServerConfig(
            isPrivateServerEnabled = true,
            protocol = "http://",
            host = "192.168.1.150",
            port = "8080",
            apiToken = "",
            isWorkerForegroundEnabled = true,
            isSqliteBufferEnabled = true
        )
    }
}
