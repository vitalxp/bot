package app.taxicom.commandos.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min

/**
 * Account bus client: offers from reader phones reach the cockpit, decisions travel back (target < 200 ms).
 * Reconnects with exponential backoff; while disconnected each reader keeps scoring on its own.
 */
class BusClient(
    private val api: ApiClient,
    private val tokens: TokenStore,
    private val scope: CoroutineScope,
) {
    private val _incoming = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val incoming: SharedFlow<JsonObject> = _incoming
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var socket: WebSocket? = null
    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var backoff = 1_000L
            while (true) {
                val token = tokens.load()?.accessToken
                if (token == null) {
                    delay(5_000)
                    continue
                }
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                val req = Request.Builder().url("${api.wsUrl}?token=$token").build()
                socket = api.http.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        _connected.value = true
                        backoff = 1_000L
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching { api.json.parseToJsonElement(text).jsonObject }.getOrNull()?.let { _incoming.tryEmit(it) }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        closed.complete(Unit)
                    }
                })
                closed.await()
                _connected.value = false
                delay(backoff)
                backoff = min(backoff * 2, 30_000L)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        socket?.close(1000, "shift_end")
        socket = null
        _connected.value = false
    }

    fun send(message: JsonObject): Boolean = socket?.send(message.toString()) == true
}
