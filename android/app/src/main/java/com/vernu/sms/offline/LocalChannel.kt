package com.vernu.sms.offline

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * WebSocket com o servidor local (plano 2.2).
 *
 * - Conecta em ws(s)://<servidor>/ws?deviceId=<id> com a API key no header
 *   x-api-key, como o LocalGateway do backend espera.
 * - Reconecta sozinho com backoff exponencial (1 s, 2 s, 4 s ... até 60 s).
 * - Quando há mais de um servidor (servidor offline + servidor local de
 *   fallback), cada falha passa para o próximo da lista (plano 6.4).
 *
 * Os endereços são lidos a cada tentativa, então uma mudança nas
 * configurações vale na próxima reconexão.
 */
class LocalChannel(
    private val client: OkHttpClient,
    private val deviceId: String,
    private val endpoints: () -> List<Endpoint>,
) : MessageChannel {

    data class Endpoint(val webSocketUrl: String, val apiKey: String)

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "LocalChannel").apply { isDaemon = true }
    }
    private val lock = Any()

    private var listener: MessageChannel.Listener? = null
    private var socket: WebSocket? = null
    private var connected = false
    private var stopped = true
    // Cada socket tem um número; eventos de sockets antigos são ignorados
    private var generation = 0
    private var endpointIndex = 0
    private var backoffMs = INITIAL_BACKOFF_MS
    private var reconnectTask: ScheduledFuture<*>? = null

    override val isConnected: Boolean
        get() = synchronized(lock) { connected }

    override fun onMessage(listener: MessageChannel.Listener) {
        synchronized(lock) { this.listener = listener }
    }

    override fun connect() {
        synchronized(lock) {
            if (!stopped) return
            stopped = false
            backoffMs = INITIAL_BACKOFF_MS
        }
        scheduler.execute { openSocket() }
    }

    override fun disconnect() {
        val toClose: WebSocket?
        synchronized(lock) {
            stopped = true
            generation++
            reconnectTask?.cancel(false)
            reconnectTask = null
            toClose = socket
            socket = null
            connected = false
        }
        toClose?.close(NORMAL_CLOSURE, "Offline mode turned off")
    }

    // Encerra o executor; o canal não pode mais ser usado depois disso
    fun shutdown() {
        disconnect()
        scheduler.shutdownNow()
    }

    override fun send(text: String): Boolean {
        val ws = synchronized(lock) { if (connected) socket else null } ?: return false
        return ws.send(text)
    }

    private fun openSocket() {
        val list = endpoints()
        val myGeneration: Int
        val endpoint: Endpoint
        synchronized(lock) {
            if (stopped) return
            if (list.isEmpty()) {
                Log.w(TAG, "No offline server configured; retrying later")
                scheduleReconnectLocked()
                return
            }
            if (endpointIndex >= list.size) endpointIndex = 0
            endpoint = list[endpointIndex]
            generation++
            myGeneration = generation
        }

        val url = endpoint.webSocketUrl + "?deviceId=" + java.net.URLEncoder.encode(deviceId, "UTF-8")
        val request = try {
            Request.Builder()
                .url(url)
                .header("x-api-key", endpoint.apiKey)
                .build()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid WebSocket URL $url: ${e.message}")
            onSocketFailed(myGeneration, "invalid url")
            return
        }

        Log.i(TAG, "Connecting to ${endpoint.webSocketUrl}")
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val l: MessageChannel.Listener?
                synchronized(lock) {
                    if (myGeneration != generation || stopped) {
                        webSocket.close(NORMAL_CLOSURE, null)
                        return
                    }
                    connected = true
                    backoffMs = INITIAL_BACKOFF_MS
                    l = listener
                }
                Log.i(TAG, "Connected to ${endpoint.webSocketUrl}")
                l?.onConnected(this@LocalChannel, com.vernu.sms.managers.NetworkManager.originOfWebSocketUrl(endpoint.webSocketUrl))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val l = synchronized(lock) { if (myGeneration == generation) listener else null }
                l?.onMessage(this@LocalChannel, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onSocketFailed(myGeneration, "closed ($code ${reason.ifEmpty { "-" }})")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val detail = if (response != null) "HTTP ${response.code}" else (t.message ?: t.javaClass.simpleName)
                if (response?.code == 401) {
                    Log.e(TAG, "Server refused the device credentials (401) at ${endpoint.webSocketUrl}")
                }
                onSocketFailed(myGeneration, detail)
            }
        })
        synchronized(lock) {
            if (myGeneration == generation && !stopped) socket = ws else ws.cancel()
        }
    }

    private fun onSocketFailed(failedGeneration: Int, reason: String) {
        val l: MessageChannel.Listener?
        val wasConnected: Boolean
        synchronized(lock) {
            if (failedGeneration != generation) return
            wasConnected = connected
            connected = false
            socket = null
            l = listener
            if (stopped) return
            // Uma conexão que nunca abriu passa para o próximo servidor da lista
            if (!wasConnected) endpointIndex++
            scheduleReconnectLocked()
        }
        Log.w(TAG, "WebSocket down: $reason")
        if (wasConnected) l?.onDisconnected(this, reason)
    }

    private fun scheduleReconnectLocked() {
        reconnectTask?.cancel(false)
        // ±20% para que vários aparelhos não reconectem no mesmo instante
        val jitter = 1.0 + (Random.nextDouble() - 0.5) * 0.4
        val delay = (backoffMs * jitter).toLong()
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        Log.d(TAG, "Reconnecting in ${delay} ms")
        reconnectTask = scheduler.schedule({ openSocket() }, delay, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val TAG = "LocalChannel"
        private const val NORMAL_CLOSURE = 1000
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
