package com.helucryptic.android.webrtc

import android.content.Context
import com.helucryptic.android.crypto.CryptoManager
import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.crypto.PasetoV4
import com.helucryptic.android.data.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingMessage(val sender: String, val plaintext: String)

@Singleton
class WebRtcEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val crypto: CryptoManager,
    private val identityStore: IdentityStore,
    private val roomManager: RoomManager,
    private val p2p: P2PChannelManager,
    private val contactRepository: ContactRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 128)
    val incoming: SharedFlow<IncomingMessage> = _incoming

    // Per-peer crypto state - ConcurrentHashMap for safe access across coroutines
    private val sessionKeys   = ConcurrentHashMap<String, ByteArray>()
    private val myEphPriv     = ConcurrentHashMap<String, String>()
    private val helloVerified: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // PSK state
    @Volatile private var roomPsk  = ""
    @Volatile private var roomCode = ""
    private val pendingPskNonces = ConcurrentHashMap<String, String>()  // peer → nonce we challenged with
    private val pskVerified: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Callbacks wired by ConnectionManager
    var onPeerReady: ((username: String, ed25519Pub: String, x25519Pub: String) -> Unit)? = null
    var onKeyChange: ((String) -> Unit)? = null
    var onSendRaw:   ((peer: String, json: String) -> Unit)? = null   // relay path
    var onDelivery:  ((peer: String, msgId: String) -> Unit)? = null  // chat acked by peer
    var onRtt:       ((peer: String, rttMs: Long) -> Unit)? = null     // heartbeat round-trip
    var onPeerStale: ((peer: String) -> Unit)? = null                  // heartbeat: dead channel

    // --- Reliability layer (heartbeat + outbox + delivery acks) --------------
    private val lastPong   = ConcurrentHashMap<String, Long>()         // peer → monotonic ms
    private val rttMs       = ConcurrentHashMap<String, Long>()         // peer → last RTT
    private val outbox      = ConcurrentHashMap<String, ArrayDeque<Pair<String, String>>>()  // peer → [(id,text)]
    private val awaitingAck = ConcurrentHashMap<String, MutableSet<String>>()
    @Volatile private var heartbeatJob: kotlinx.coroutines.Job? = null
    private val maxOutboxPerPeer = 500

    fun setRoom(code: String, psk: String) {
        roomCode = code
        roomPsk  = psk
    }

    fun clearRoom() {
        roomCode = ""
        roomPsk  = ""
        pendingPskNonces.clear()
        pskVerified.clear()
        // Full disconnect: stop pinging and drop transient reliability state.
        // (Outbox is intentionally retained so unsent 1-to-1 messages survive.)
        stopHeartbeat()
        lastPong.clear()
        rttMs.clear()
        awaitingAck.clear()
    }

    /** Called by the transport layer when a relay data-channel message arrives. */
    fun onDataChannelMessage(peer: String, text: String) {
        scope.launch { handleFrame(peer, text) }
    }

    /** Called once the relay confirms a peer is present - starts PSK challenge or hello. */
    fun onChannelOpen(peer: String) {
        scope.launch {
            if (roomPsk.isNotEmpty()) sendPskChallenge(peer)
            else sendHello(peer)
        }
    }

    // ── PSK challenge / response ──────────────────────────────────────────────

    private suspend fun sendPskChallenge(peer: String) {
        val nonce = UUID.randomUUID().toString()
        pendingPskNonces[peer] = nonce
        emit(peer, JSONObject().apply {
            put("__type",  "psk_challenge")
            put("nonce",   nonce)
            put("room_id", roomCode)
        }.toString())
    }

    private suspend fun handlePskChallenge(peer: String, frame: JSONObject) {
        val nonce = frame.optString("nonce").takeIf { it.isNotEmpty() } ?: return
        if (roomPsk.isEmpty()) return  // no PSK - can't respond (room not loaded yet)
        // SECURITY: bind the proof to OUR room id (never an attacker-supplied
        // one) and to OUR username as the responder. The responder binding is
        // what defeats reflection: an attacker echoing our own nonce back as a
        // challenge gets a proof bound to OUR name, which can never satisfy
        // the check we run on THEIR response. Matches desktop _psk_proof().
        val frameRoom = frame.optString("room_id")
        if (frameRoom.isNotEmpty() && frameRoom != roomCode) return
        val me = identityStore.username ?: return
        val proof = roomManager.pskProof(nonce, roomCode, roomPsk, responder = me)
        emit(peer, JSONObject().apply {
            put("__type", "psk_response")
            put("proof",  proof)
        }.toString())
    }

    private suspend fun handlePskResponse(peer: String, frame: JSONObject) {
        val proof  = frame.optString("proof").takeIf { it.isNotEmpty() } ?: return
        val nonce  = pendingPskNonces.remove(peer)                         ?: return
        // Verify against the PEER as responder (nonce is single-use: removed above).
        val expected = roomManager.pskProof(nonce, roomCode, roomPsk, responder = peer)
        if (!constantTimeEquals(proof, expected)) {
            android.util.Log.w("WebRtcEngine", "PSK verification failed for $peer - rejecting")
            return  // silently drop; peer can't participate
        }
        pskVerified.add(peer)
        sendHello(peer)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        if (x.size != y.size) return false
        var diff = 0
        for (i in x.indices) diff = diff or (x[i].toInt() xor y[i].toInt())
        return diff == 0
    }

    // ── Hello / key exchange ──────────────────────────────────────────────────

    private suspend fun sendHello(peer: String) {
        if (roomPsk.isNotEmpty() && peer !in pskVerified) return
        val eph = crypto.generateEphemeralX25519()
        myEphPriv[peer] = eph.priv
        val payload = mapOf(
            "username"       to (identityStore.username ?: ""),
            "x25519_pub"     to (identityStore.x25519Pub ?: ""),
            "ed25519_pub"    to (identityStore.ed25519Pub ?: ""),
            "eph_x25519_pub" to eph.pub,
            "iat"            to java.time.Instant.now().toString()
        )
        val token = PasetoV4.sign(payload, identityStore.ed25519Priv ?: return)
        emit(peer, JSONObject().apply { put("__type", "hello"); put("token", token) }.toString())
    }

    private suspend fun handleHello(peer: String, frame: JSONObject) {
        if (peer in helloVerified) return
        val token      = frame.getString("token")
        val claimedPub = PasetoV4.extractClaimedEd25519Pub(token) ?: return

        // ── TOFU / key-pinning ────────────────────────────────────────────────
        // Look up the key we have stored for this peer (keyed by username == peer).
        val storedContact = contactRepository.get(peer)
        val storedKey     = storedContact?.ed25519Pub?.takeIf { it.isNotEmpty() }

        val verifyKey: String
        val isKeyChange: Boolean
        when {
            storedKey == null -> {
                // First contact - TOFU: trust the claimed key to verify the token,
                // then pin it via onPeerReady → upsertFromHello.
                verifyKey   = claimedPub
                isKeyChange = false
            }
            storedKey == claimedPub -> {
                // Known peer, key matches - normal path.
                verifyKey   = storedKey
                isKeyChange = false
            }
            else -> {
                // Stored key exists but differs from what this token claims.
                // Treat as a key-change event and reject the session.
                android.util.Log.w("WebRtcEngine",
                    "Key change detected for $peer - stored≠claimed. Blocking session.")
                onKeyChange?.invoke(peer)
                return
            }
        }

        val payload = runCatching { PasetoV4.verify(token, verifyKey) }.getOrNull() ?: run {
            android.util.Log.w("WebRtcEngine", "Signature verification failed for $peer")
            return
        }

        val peerEd   = payload["ed25519_pub"]    as? String ?: return
        val peerX    = payload["x25519_pub"]     as? String ?: return
        val peerEph  = payload["eph_x25519_pub"] as? String ?: return
        val username = payload["username"]       as? String ?: peer

        // SECURITY: bind the claimed identity to the signaling peer name. A
        // peer connected as `peer` must not be able to assert a DIFFERENT
        // username - that would poison another contact's pinned keys and let
        // them impersonate that contact in stored history. (Desktop SEC check.)
        if (username != peer) {
            android.util.Log.w("WebRtcEngine",
                "Hello username mismatch: signaling peer '$peer' claims '$username' - rejecting")
            return
        }

        // SECURITY: reject a signed hello with an implausible timestamp
        // (defence-in-depth against replay; the ephemeral DH is the real guard).
        val iat = payload["iat"] as? String
        if (iat != null) {
            val fresh = runCatching {
                // Desktop sends "…+00:00" (python isoformat), Android sends "…Z" -
                // OffsetDateTime.parse accepts both; Instant.parse only the latter.
                val ts = runCatching { java.time.OffsetDateTime.parse(iat).toInstant() }
                    .getOrElse { java.time.Instant.parse(iat) }
                kotlin.math.abs(java.time.Duration.between(ts, java.time.Instant.now()).seconds) <= MAX_HELLO_SKEW_SECONDS
            }.getOrDefault(false)
            if (!fresh) {
                android.util.Log.w("WebRtcEngine", "Stale/implausible hello timestamp from $peer - rejecting")
                return
            }
        }

        // Sanity: the key inside the payload body must match what we verified against.
        if (peerEd != verifyKey) {
            android.util.Log.w("WebRtcEngine", "Payload ed25519_pub mismatch for $peer")
            if (!isKeyChange) onKeyChange?.invoke(username)
            return
        }

        val myEph = myEphPriv.getOrPut(peer) {
            crypto.generateEphemeralX25519().also { myEphPriv[peer] = it.priv }.priv
        }
        sessionKeys[peer] = crypto.deriveSessionKeyV2(
            myXPrivB64    = identityStore.x25519Priv ?: return,
            myEphPrivB64  = myEph,
            peerXPubB64   = peerX,
            peerEphPubB64 = peerEph
        )
        helloVerified.add(peer)
        lastPong[peer] = android.os.SystemClock.elapsedRealtime()   // fresh heartbeat clock
        startHeartbeat()                                            // idempotent
        onPeerReady?.invoke(username, peerEd, peerX)
        // Deliver anything queued for this peer while it was offline (1-to-1).
        if (roomManager.groupKey == null) flushOutbox(peer)

        // Defence-in-depth: never hand the group key to a peer that hasn't
        // proven the PSK in an invite-only room (handleFrame also gates this).
        if (roomPsk.isNotEmpty() && peer !in pskVerified) return
        roomManager.groupKey?.let {
            emit(peer, JSONObject().apply {
                put("__type", "group_key")
                put("token", roomManager.encryptGroupKeyFor(sessionKeys[peer]!!))
            }.toString())
        }
    }

    private companion object {
        // Generous skew tolerance for badly-set clocks; replay is really
        // defeated by the per-session ephemeral DH.
        const val MAX_HELLO_SKEW_SECONDS = 24L * 3600L
        // App-layer heartbeat cadence + the silence window after which a peer is
        // considered stale (see docs/WIRE_PROTOCOL.md).
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val HEARTBEAT_DEAD_MS     = 45_000L
    }

    private fun handleGroupKey(peer: String, frame: JSONObject) {
        val sk = sessionKeys[peer] ?: return
        // SECURITY: once the room creator is known, only accept the group key
        // from them - a non-creator member must not be able to race a key of
        // their choosing onto the room (matches desktop SEC-05). The creator
        // exception also covers legitimate re-keys after creator promotion.
        val creator = roomManager.roomCreator
        if (creator.isNotEmpty() && peer != creator) {
            android.util.Log.w("WebRtcEngine", "Ignoring group_key from non-creator $peer")
            return
        }
        // Accept a REPLACEMENT key from the creator (promotion re-key); ignore
        // duplicates from anyone else once a key is installed.
        if (roomManager.groupKey != null && creator.isEmpty()) return
        runCatching { roomManager.installGroupKey(frame.getString("token"), sk) }
            .onFailure { android.util.Log.w("WebRtcEngine", "group_key install failed: ${it.message}") }
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    private fun decryptAndDeliver(peer: String, frame: JSONObject) {
        val key   = roomManager.groupKey ?: sessionKeys[peer] ?: return
        val token = frame.optString("token").takeIf { it.isNotEmpty() } ?: return
        val payload = runCatching { PasetoV4.decrypt(token, key) }.getOrNull() ?: return
        val text  = payload["text"] as? String ?: return
        scope.launch { _incoming.emit(IncomingMessage(peer, text)) }
        // Delivery receipt for a 1-to-1 message that carried an id. (Room
        // messages are multi-recipient, so acks there would be ambiguous.)
        val msgId = payload["id"] as? String
        if (!msgId.isNullOrEmpty() && roomManager.groupKey == null) {
            scope.launch { sendAck(peer, msgId) }
        }
    }

    private fun sendAck(peer: String, msgId: String) {
        val key = sessionKeys[peer] ?: return
        val token = PasetoV4.encrypt(mapOf("id" to msgId), key)
        emit(peer, JSONObject().apply { put("__type", "ack"); put("token", token) }.toString())
    }

    private fun handleAck(peer: String, frame: JSONObject) {
        val key = sessionKeys[peer] ?: return
        val token = frame.optString("token").takeIf { it.isNotEmpty() } ?: return
        val payload = runCatching { PasetoV4.decrypt(token, key) }.getOrNull() ?: return
        val id = payload["id"] as? String ?: return
        awaitingAck[peer]?.remove(id)
        onDelivery?.invoke(peer, id)
    }

    /**
     * Send a 1-to-1 chat. Returns the message id (caller persists it so the
     * delivery ack can flip the row to "delivered"). Queues to the per-peer
     * outbox when the peer isn't reachable yet, instead of dropping the message.
     */
    fun sendMessage(peer: String, text: String, msgId: String): String {
        // No session/group key yet (peer not handshaked) → queue in order;
        // flushed once the session is ready. emit() handles the P2P-or-relay
        // choice when a key exists.
        val key = roomManager.groupKey ?: sessionKeys[peer]
        if (key == null) {
            enqueueOutbox(peer, msgId, text)
            return msgId
        }
        val token = PasetoV4.encrypt(mapOf("text" to text, "id" to msgId), key)
        // Emit "chat" (desktop-compatible). decryptAndDeliver still accepts the
        // legacy "msg" type from older peers.
        emit(peer, JSONObject().apply { put("__type", "chat"); put("token", token) }.toString())
        awaitingAck.getOrPut(peer) { ConcurrentHashMap.newKeySet() }.add(msgId)
        return msgId
    }

    private fun enqueueOutbox(peer: String, msgId: String, text: String) {
        val dq = outbox.getOrPut(peer) { ArrayDeque() }
        synchronized(dq) {
            while (dq.size >= maxOutboxPerPeer) dq.removeFirst()
            dq.addLast(msgId to text)
        }
        android.util.Log.i("WebRtcEngine", "$peer not ready - queued chat (outbox=${dq.size})")
    }

    private fun flushOutbox(peer: String) {
        val dq = outbox[peer] ?: return
        val key = sessionKeys[peer] ?: return
        val pending: List<Pair<String, String>>
        synchronized(dq) { pending = dq.toList(); dq.clear() }
        for ((id, text) in pending) {
            val token = PasetoV4.encrypt(mapOf("text" to text, "id" to id), key)
            emit(peer, JSONObject().apply { put("__type", "chat"); put("token", token) }.toString())
            awaitingAck.getOrPut(peer) { ConcurrentHashMap.newKeySet() }.add(id)
        }
    }

    fun broadcastMessage(text: String): Boolean {
        val key = roomManager.groupKey ?: return false
        val token = PasetoV4.encrypt(mapOf("text" to text), key)
        val json  = JSONObject().apply { put("__type", "chat"); put("token", token) }.toString()
        roomManager.members.value.forEach { peer -> emit(peer, json) }
        return true
    }

    fun rebroadcastGroupKey() {
        scope.launch {
            roomManager.members.value.forEach { peer ->
                val sk = sessionKeys[peer] ?: return@forEach
                emit(peer, JSONObject().apply {
                    put("__type", "group_key")
                    put("token", roomManager.encryptGroupKeyFor(sk))
                }.toString())
            }
        }
    }

    fun disconnectPeer(peer: String) {
        sessionKeys.remove(peer)
        myEphPriv.remove(peer)
        helloVerified.remove(peer)
        pendingPskNonces.remove(peer)
        pskVerified.remove(peer)
        // Clear heartbeat/ack state, but KEEP the outbox so queued messages
        // survive the disconnect and flush when the peer reconnects.
        lastPong.remove(peer)
        rttMs.remove(peer)
        awaitingAck.remove(peer)
        p2p.close(peer)
    }

    // ── Frame dispatch ────────────────────────────────────────────────────────

    private suspend fun handleFrame(peer: String, text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (frame.optString("__type")) {
            // Heartbeat is ungated (no secrets; must work regardless of hello):
            // answer a ping; record a pong's round-trip.
            "__ping" -> {
                emit(peer, JSONObject().apply { put("__type", "__pong"); put("ts", frame.opt("ts")) }.toString())
                lastPong[peer] = android.os.SystemClock.elapsedRealtime()
            }
            "__pong" -> {
                lastPong[peer] = android.os.SystemClock.elapsedRealtime()
                val ts = frame.optDouble("ts", Double.NaN)
                if (!ts.isNaN()) {
                    val rtt = (System.currentTimeMillis() - ts).toLong().coerceAtLeast(0)
                    rttMs[peer] = rtt
                    onRtt?.invoke(peer, rtt)
                }
            }
            // SECURITY (PSK gate): in a PSK-protected room, NO identity or key
            // material is processed until the peer has proven the PSK. Without
            // this gate, anyone who knew the room code could send a hello,
            // complete the key exchange, and be handed the group key -
            // bypassing the invite-only protection entirely.
            "hello"         -> {
                if (roomPsk.isNotEmpty() && peer !in pskVerified) {
                    android.util.Log.w("WebRtcEngine", "Dropping hello from $peer - PSK not yet proven")
                    return
                }
                handleHello(peer, frame)
            }
            "group_key"     -> {
                if (roomPsk.isNotEmpty() && peer !in pskVerified) return
                handleGroupKey(peer, frame)
            }
            "psk_challenge" -> handlePskChallenge(peer, frame)
            "psk_response"  -> handlePskResponse(peer, frame)
            "ack"           -> if (peer in helloVerified) handleAck(peer, frame)
            // "chat" (current) and "msg" (legacy Android) are both direct chats.
            "chat", "msg"   -> if (peer in helloVerified) decryptAndDeliver(peer, frame)
            else            -> if (peer in helloVerified) decryptAndDeliver(peer, frame)
        }
    }

    // ── Heartbeat (app-layer keepalive over the channel) ──────────────────────

    /** Idempotently start the periodic ping loop. */
    fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
                val now = android.os.SystemClock.elapsedRealtime()
                for (peer in helloVerified.toList()) {
                    lastPong.putIfAbsent(peer, now)
                    emit(peer, JSONObject().apply {
                        put("__type", "__ping"); put("ts", System.currentTimeMillis().toDouble())
                    }.toString())
                    if (now - (lastPong[peer] ?: now) > HEARTBEAT_DEAD_MS) {
                        android.util.Log.w("WebRtcEngine", "$peer silent > ${HEARTBEAT_DEAD_MS}ms - stale, asking for heal")
                        lastPong[peer] = now   // avoid repeat-firing while healing
                        onPeerStale?.invoke(peer)
                    }
                }
            }
        }
    }

    fun stopHeartbeat() { heartbeatJob?.cancel(); heartbeatJob = null }

    /** Live round-trip estimate for a peer, or null if not yet measured. */
    fun rttFor(peer: String): Long? = rttMs[peer]

    fun pendingOutbox(peer: String): Int = outbox[peer]?.size ?: 0

    /** Tries P2P first; falls back to relay. */
    private fun emit(peer: String, json: String) {
        if (!p2p.send(peer, json)) onSendRaw?.invoke(peer, json)
    }
}
