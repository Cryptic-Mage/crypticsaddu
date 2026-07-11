package com.helucryptic.android.webrtc

import com.helucryptic.android.crypto.IdentityStore
import com.helucryptic.android.crypto.PasetoV4
import com.helucryptic.android.data.datastore.AppSettings
import com.helucryptic.android.data.repository.ContactRepository
import com.helucryptic.android.data.repository.MessageRepository
import com.helucryptic.android.signaling.SignalingClient
import com.helucryptic.android.signaling.SignalingMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    private val signalingClient: SignalingClient,
    private val engine: WebRtcEngine,
    private val roomManager: RoomManager,
    private val p2pManager: P2PChannelManager,
    private val identityStore: IdentityStore,
    private val appSettings: AppSettings,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository
) {
    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentRoom: String? = null
    private var peerCount   = 0

    // Last NAT behaviour probe (best-effort; surfaced for diagnostics/UI). Tells
    // us whether direct/hole-punch traversal can work or a relay is required.
    @Volatile var natProfile: NatDiscovery.Profile? = null
        private set

    init {
        // Initialize WebRTC factory (suspending — runs on IO scope)
        scope.launch { p2pManager.initialize() }

        // After PSK + hello both verified: mark peer connected and initiate P2P as offerer
        engine.onPeerReady = { peerUsername, edPub, xPub ->
            scope.launch {
                contactRepository.upsertFromHello(peerUsername, edPub, xPub)
                peerCount++
                signalingClient.onPeerConnected()
                // Lower username always creates the WebRTC offer to avoid dual-offer collisions
                val myName = identityStore.username ?: ""
                if (myName < peerUsername) p2pManager.createOffer(peerUsername)
            }
        }

        // P2P: forward WebRTC signaling (offer/answer/ICE) through the relay
        p2pManager.onSendSignal = { peer, signalJson ->
            val obj  = JSONObject(signalJson)
            val type = obj.getString("type")
            obj.remove("type")
            signalingClient.send(SignalingMessage.forward(peer, type, obj))
        }

        // P2P messages feed into the crypto engine (same path as relay messages)
        p2pManager.onMessage = { peer, json -> engine.onDataChannelMessage(peer, json) }

        // Self-heal: when a P2P connection hard-fails, tear it down and (if we
        // are the designated offerer by the username tie-break) re-offer after
        // a short grace so both sides finish cleanup first. Messages keep
        // flowing over the relay fallback in the meantime.
        p2pManager.onPeerFailed = { peer ->
            scope.launch {
                p2pManager.close(peer)
                delay(1_500)
                val myName = identityStore.username ?: ""
                if (myName.isNotEmpty() && myName < peer) {
                    android.util.Log.i("ConnectionManager", "Re-offering P2P to $peer after failure")
                    p2pManager.createOffer(peer)
                }
            }
        }

        // Relay outbound E2EE frames
        engine.onSendRaw = { peer, json ->
            scope.launch {
                signalingClient.send(SignalingMessage.forward(peer, "data_channel", json))
            }
        }

        // Delivery receipt → flip the stored message to "delivered".
        engine.onDelivery = { _, msgId ->
            scope.launch { messageRepository.markDelivered(msgId) }
        }

        // Heartbeat says a peer's channel is dead → same self-heal as an ICE
        // hard-failure (close + re-offer if we're the tie-break offerer).
        engine.onPeerStale = { peer ->
            scope.launch {
                p2pManager.close(peer)
                delay(1_500)
                val myName = identityStore.username ?: ""
                if (myName.isNotEmpty() && myName < peer) {
                    android.util.Log.i("ConnectionManager", "Re-offering P2P to $peer after heartbeat timeout")
                    p2pManager.createOffer(peer)
                }
            }
        }

        // Handle incoming relay messages
        scope.launch {
            signalingClient.messages.collect { message -> handleSignalingMessage(message) }
        }

        // Save decrypted messages to DB
        scope.launch {
            engine.incoming.collect { incoming ->
                val destId = currentRoom ?: incoming.sender
                messageRepository.save(
                    roomOrPeerId = destId,
                    sender       = incoming.sender,
                    ciphertext   = "encrypted",
                    plaintext    = incoming.plaintext
                )
            }
        }
    }

    fun connectGlobal() {
        currentRoom = null
        peerCount   = 0
        engine.clearRoom()
        p2pManager.closeAll()
        roomManager.reset()
        connectSignaling(null)
    }

    fun connectRoom(roomCode: String, psk: String, creatorUsername: String) {
        currentRoom = roomCode
        peerCount   = 0
        engine.setRoom(roomCode, psk)
        p2pManager.closeAll()
        roomManager.reset()
        val myUsername = identityStore.username ?: ""
        if (myUsername.isNotEmpty() && myUsername == creatorUsername) {
            roomManager.initAsCreator(myUsername)
        } else if (creatorUsername.isNotEmpty() && creatorUsername != "unknown") {
            // Member side: pin the creator as the only legitimate group_key
            // sender (the engine enforces this).
            roomManager.setCreator(creatorUsername)
        }
        connectSignaling(roomCode)
    }

    fun disconnect() {
        currentRoom = null
        peerCount   = 0
        engine.clearRoom()
        p2pManager.closeAll()
        roomManager.reset()
        signalingClient.disconnect()
    }

    private fun connectSignaling(room: String?) {
        scope.launch {
            if (!identityStore.isInitialized()) return@launch
            val url      = appSettings.signalingUrl.first()
            val password = appSettings.serverPassword.first()
            val username = identityStore.username ?: return@launch
            val payload  = mapOf("username" to username, "iat" to Instant.now().toString())
            val token    = PasetoV4.sign(payload, identityStore.ed25519Priv ?: return@launch)
            signalingClient.connect(url, password, username, token, room)
        }
        // Probe NAT behaviour once per connect (off the main thread). Result is
        // advisory: it tells the diagnostics/UI whether this network can do a
        // direct connection or will fall back to the signaling relay.
        scope.launch {
            natProfile = runCatching { NatDiscovery.discover() }.getOrNull()
            natProfile?.let {
                android.util.Log.i("ConnectionManager", "NAT: ${it.type} — ${it.summary}")
            }
        }
    }

    private fun handleSignalingMessage(message: SignalingMessage) {
        val myUsername = identityStore.username ?: ""
        when (message) {
            is SignalingMessage.PeerLeft -> {
                val promoted = roomManager.removeMember(message.username, myUsername)
                engine.disconnectPeer(message.username)
                peerCount = maxOf(0, peerCount - 1)
                if (peerCount == 0) signalingClient.onPeerDisconnected()
                if (promoted) engine.rebroadcastGroupKey()
            }
            is SignalingMessage.Forward -> {
                when (message.type) {
                    "room_state" -> {
                        val arr = when (val d = message.data) {
                            is JSONArray  -> d
                            is JSONObject -> d.optJSONArray("peers")
                            else          -> null
                        } ?: return
                        for (i in 0 until arr.length()) {
                            val peer = arr.getString(i)
                            if (peer != myUsername) {
                                roomManager.addMember(peer)
                                engine.onChannelOpen(peer)
                            }
                        }
                    }
                    "peer_joined" -> {
                        val peer = message.sender
                        if (peer.isNotEmpty() && peer != myUsername) {
                            roomManager.addMember(peer)
                            engine.onChannelOpen(peer)
                        }
                    }
                    // "peer_left" as a Forward is never reached — the parser returns
                    // SignalingMessage.PeerLeft for that type, handled in the PeerLeft branch above.
                    "data_channel" -> {
                        val json = when (val d = message.data) {
                            is String     -> d
                            is JSONObject -> d.toString()
                            else          -> d?.toString()
                        } ?: return
                        engine.onDataChannelMessage(message.sender, json)
                    }

                    // WebRTC P2P signaling — relayed until the direct channel is open
                    "webrtc_offer" -> {
                        val sdp = (message.data as? JSONObject)?.optString("sdp") ?: return
                        p2pManager.handleOffer(message.sender, sdp)
                    }
                    "webrtc_answer" -> {
                        val sdp = (message.data as? JSONObject)?.optString("sdp") ?: return
                        p2pManager.handleAnswer(message.sender, sdp)
                    }
                    "webrtc_ice" -> {
                        val d    = message.data as? JSONObject ?: return
                        val mid  = d.optString("sdpMid").takeIf { it.isNotEmpty() }
                        val idx  = d.optInt("sdpMLineIndex", 0)
                        val cand = d.optString("candidate").takeIf { it.isNotEmpty() } ?: return
                        p2pManager.handleIce(message.sender, mid, idx, cand)
                    }
                }
            }
            else -> {}
        }
    }
}
