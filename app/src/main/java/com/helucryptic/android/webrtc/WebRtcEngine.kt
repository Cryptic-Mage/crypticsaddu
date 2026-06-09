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

    // Per-peer crypto state — ConcurrentHashMap for safe access across coroutines
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

    fun setRoom(code: String, psk: String) {
        roomCode = code
        roomPsk  = psk
    }

    fun clearRoom() {
        roomCode = ""
        roomPsk  = ""
        pendingPskNonces.clear()
        pskVerified.clear()
    }

    /** Called by the transport layer when a relay data-channel message arrives. */
    fun onDataChannelMessage(peer: String, text: String) {
        scope.launch { handleFrame(peer, text) }
    }

    /** Called once the relay confirms a peer is present — starts PSK challenge or hello. */
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
        val nonce  = frame.optString("nonce").takeIf { it.isNotEmpty() }   ?: return
        val roomId = frame.optString("room_id").takeIf { it.isNotEmpty() } ?: return
        if (roomPsk.isEmpty()) return  // no PSK — can't respond (room not loaded yet)
        val proof = roomManager.pskProof(nonce, roomId, roomPsk)
        emit(peer, JSONObject().apply {
            put("__type", "psk_response")
            put("proof",  proof)
        }.toString())
    }

    private suspend fun handlePskResponse(peer: String, frame: JSONObject) {
        val proof  = frame.optString("proof").takeIf { it.isNotEmpty() } ?: return
        val nonce  = pendingPskNonces.remove(peer)                         ?: return
        val expected = roomManager.pskProof(nonce, roomCode, roomPsk)
        if (proof != expected) {
            android.util.Log.w("WebRtcEngine", "PSK verification failed for $peer — rejecting")
            return  // silently drop; peer can't participate
        }
        pskVerified.add(peer)
        sendHello(peer)
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
                // First contact — TOFU: trust the claimed key to verify the token,
                // then pin it via onPeerReady → upsertFromHello.
                verifyKey   = claimedPub
                isKeyChange = false
            }
            storedKey == claimedPub -> {
                // Known peer, key matches — normal path.
                verifyKey   = storedKey
                isKeyChange = false
            }
            else -> {
                // Stored key exists but differs from what this token claims.
                // Treat as a key-change event and reject the session.
                android.util.Log.w("WebRtcEngine",
                    "Key change detected for $peer — stored≠claimed. Blocking session.")
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
        onPeerReady?.invoke(username, peerEd, peerX)

        roomManager.groupKey?.let {
            emit(peer, JSONObject().apply {
                put("__type", "group_key")
                put("token", roomManager.encryptGroupKeyFor(sessionKeys[peer]!!))
            }.toString())
        }
    }

    private fun handleGroupKey(peer: String, frame: JSONObject) {
        val sk = sessionKeys[peer] ?: return
        if (roomManager.groupKey != null) return
        roomManager.installGroupKey(frame.getString("token"), sk)
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    private fun decryptAndDeliver(peer: String, frame: JSONObject) {
        val key   = roomManager.groupKey ?: sessionKeys[peer] ?: return
        val token = frame.optString("token").takeIf { it.isNotEmpty() } ?: return
        val payload = runCatching { PasetoV4.decrypt(token, key) }.getOrNull() ?: return
        val text  = payload["text"] as? String ?: return
        scope.launch { _incoming.emit(IncomingMessage(peer, text)) }
    }

    fun sendMessage(peer: String, text: String) {
        val key   = roomManager.groupKey ?: sessionKeys[peer] ?: return
        val token = PasetoV4.encrypt(mapOf("text" to text), key)
        emit(peer, JSONObject().apply { put("__type", "msg"); put("token", token) }.toString())
    }

    fun broadcastMessage(text: String): Boolean {
        val key = roomManager.groupKey ?: return false
        val token = PasetoV4.encrypt(mapOf("text" to text), key)
        val json  = JSONObject().apply { put("__type", "msg"); put("token", token) }.toString()
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
        p2p.close(peer)
    }

    // ── Frame dispatch ────────────────────────────────────────────────────────

    private suspend fun handleFrame(peer: String, text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (frame.optString("__type")) {
            "hello"         -> handleHello(peer, frame)
            "group_key"     -> handleGroupKey(peer, frame)
            "psk_challenge" -> handlePskChallenge(peer, frame)
            "psk_response"  -> handlePskResponse(peer, frame)
            else            -> if (peer in helloVerified) decryptAndDeliver(peer, frame)
        }
    }

    /** Tries P2P first; falls back to relay. */
    private fun emit(peer: String, json: String) {
        if (!p2p.send(peer, json)) onSendRaw?.invoke(peer, json)
    }
}
