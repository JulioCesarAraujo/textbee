package com.vernu.sms.offline

/**
 * Canal de mensagens entre o app e um servidor (plano 2.1). O LocalChannel é a
 * implementação por WebSocket usada no modo offline.
 */
interface MessageChannel {

    interface Listener {
        // Conectado; serverOrigin é o servidor em uso (ex.: http://192.168.0.10:3001)
        fun onConnected(channel: MessageChannel, serverOrigin: String)

        // Um frame de texto chegou do servidor
        fun onMessage(channel: MessageChannel, text: String)

        fun onDisconnected(channel: MessageChannel, reason: String)
    }

    val isConnected: Boolean

    fun connect()

    fun disconnect()

    // Registra quem recebe as mensagens e os eventos de conexão
    fun onMessage(listener: Listener)

    // Retorna false quando não há conexão aberta
    fun send(text: String): Boolean
}
