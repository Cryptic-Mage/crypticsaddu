package com.helucryptic.android.signaling

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

enum class SignalingState {
    DISCONNECTED,   // not connected to anything
    CONNECTING,     // TCP handshake + hello in flight
    SIGNALING,      // authenticated on relay server, no peer yet (idle)
    CONNECTED,      // at least one peer in the room
    RECONNECTING    // lost relay, retrying
}

@Singleton
class SignalingClient @Inject constructor(
    private val policy: ReconnectPolicy,
    private val okHttp: OkHttpClient
) {
    private val _state     = MutableStateFlow(SignalingState.DISCONNECTED)
    private val _messages  = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    private val _lastError = MutableSharedFlow<String>(extraBufferCapacity = 4, replay = 1)

    val state: StateFlow<SignalingState>       = _state
    val messages: SharedFlow<SignalingMessage> = _messages
    val lastError: SharedFlow<String>          = _lastError

    private var ws: WebSocket? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Each call to openSocket() bumps this; callbacks check their captured copy
    // so stale callbacks from closed sockets are silently ignored.
    @Volatile private var generation = 0

    private val sendTimes = ArrayDeque<Long>()

    private var pendingUrl:      String  = ""
    private var pendingPassword: String  = ""
    private var pendingUsername: String  = ""
    private var pendingToken:    String  = ""
    private var pendingRoom:     String? = null
    private var sessionToken:    String  = ""

    fun connect(url: String, password: String, username: String, helloToken: String, room: String?) {
        pendingUrl = url; pendingPassword = password
        pendingUsername = username; pendingToken = helloToken; pendingRoom = room
        openSocket()
    }

    private fun openSocket() {
        reconnectJob?.cancel()
        ws?.close(1000, "switching connection")
        ws = null

        val myGen = ++generation          // captures this socket's generation
        _state.value = SignalingState.CONNECTING

        try {
            val baseUrl = pendingUrl.removeSuffix("/")
            val encodedUser = java.net.URLEncoder.encode(pendingUsername, "UTF-8")
            val path = if (baseUrl.contains("/ws/")) "" else "/ws/$encodedUser"

            var wsUrl = "$baseUrl$path"
                .replace("https://", "wss://")
                .replace("http://", "ws://")

            val params = mutableListOf<String>()
            if (pendingPassword.isNotEmpty())
                params += "password=" + java.net.URLEncoder.encode(pendingPassword, "UTF-8")
            if (!pendingRoom.isNullOrEmpty())
                params += "room="     + java.net.URLEncoder.encode(pendingRoom!!, "UTF-8")
            if (sessionToken.isNotEmpty())
                params += "session_token=" + java.net.URLEncoder.encode(sessionToken, "UTF-8")
            if (params.isNotEmpty()) wsUrl += "?" + params.joinToString("&")

            ws = okHttp.newWebSocket(Request.Builder().url(wsUrl).build(), makeListener(myGen))
        } catch (e: Exception) {
            android.util.Log.e("SignalingClient", "Failed to open socket: ${e.message}", e)
            _state.value = SignalingState.DISCONNECTED
        }
    }

    /** Creates a fresh listener that ignores callbacks if its generation is stale. */
    private fun makeListener(myGen: Int) = object : WebSocketListener() {
        override fun onOpen(socket: WebSocket, response: Response) {
            if (myGen != generation) { socket.close(1000, "stale"); return }
            policy.reset()
            socket.send(SignalingMessage.hello(pendingUsername, pendingToken, pendingPassword, pendingRoom))
        }

        override fun onMessage(socket: WebSocket, text: String) {
            if (myGen != generation) return
            val msg = SignalingMessage.parse(text) ?: return
            when (msg) {
                is SignalingMessage.SessionToken -> {
                    sessionToken = msg.token
                    if (_state.value == SignalingState.CONNECTING ||
                        _state.value == SignalingState.RECONNECTING) {
                        _state.value = SignalingState.SIGNALING
                    }
                }
                is SignalingMessage.Error -> {
                    scope.launch { _lastError.emit(msg.message.ifBlank { "Server rejected the connection" }) }
                    generation++                           // invalidate this socket's gen
                    _state.value = SignalingState.DISCONNECTED
                    socket.close(1008, "server rejected")
                    return
                }
                else -> {}
            }
            scope.launch { _messages.emit(msg) }
        }

        override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
            if (myGen != generation) return
            val code = response?.code
            if (code == 401 || code == 403) {
                scope.launch {
                    _lastError.emit("Authentication failed - check your server password (HTTP $code)")
                }
                generation++
                _state.value = SignalingState.DISCONNECTED
            } else {
                scheduleReconnect()
            }
        }

        override fun onClosed(socket: WebSocket, code: Int, reason: String) {
            if (myGen != generation) return   // stale close from a previous socket - ignore
            if (code == 1000) _state.value = SignalingState.DISCONNECTED
            else scheduleReconnect()
        }
    }

    fun onPeerConnected() {
        if (_state.value == SignalingState.SIGNALING) _state.value = SignalingState.CONNECTED
    }

    fun onPeerDisconnected() {
        if (_state.value == SignalingState.CONNECTED) _state.value = SignalingState.SIGNALING
    }

    fun send(json: String): Boolean {
        val socket = ws ?: return false   // was: silently "succeeded" with no socket
        synchronized(sendTimes) {
            val now = System.currentTimeMillis()
            sendTimes.removeAll { now - it > 10_000L }
            if (sendTimes.size >= 80) return false
            sendTimes.addLast(now)
        }
        return socket.send(json)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        generation++                          // invalidate any in-flight callbacks
        ws?.close(1000, "user disconnect")
        ws = null
        _state.value = SignalingState.DISCONNECTED
    }

    private fun scheduleReconnect() {
        if (_state.value == SignalingState.DISCONNECTED) return
        // Reconnect for rooms AND plain 1-to-1 sessions. Previously only rooms
        // recovered - a dropped 1-to-1 session went silently dead until the
        // user backed out and reconnected manually.
        if (pendingUsername.isEmpty()) {
            _state.value = SignalingState.DISCONNECTED
            return
        }
        _state.value = SignalingState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(policy.nextDelayMs())
            openSocket()
        }
    }
}
