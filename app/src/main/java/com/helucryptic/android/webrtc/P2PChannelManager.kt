package com.helucryptic.android.webrtc

import android.content.Context
import com.helucryptic.android.data.datastore.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2PChannelManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val appSettings: AppSettings
) {
    private var factory: PeerConnectionFactory? = null
    private val initMutex = Mutex()
    // Multiple independent STUN providers so gathering still works if one is
    // blocked. An IPv6 path (gathered automatically when the device has a
    // routable v6 address) means NO NAT at all — the biggest non-relay win.
    private var cachedIce: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )

    private val connections  = mutableMapOf<String, PeerConnection>()
    private val dataChannels = mutableMapOf<String, DataChannel>()
    private val openPeers    = mutableSetOf<String>()   // data channel is OPEN

    /** Deliver a P2P-received frame to the crypto engine. */
    var onMessage:     ((peer: String, json: String) -> Unit)? = null
    /** Send WebRTC signaling (offer/answer/ICE) through the relay. json = full object. */
    var onSendSignal:  ((peer: String, json: String) -> Unit)? = null
    /** Called when data channel transitions to OPEN. */
    var onChannelOpen: ((peer: String) -> Unit)? = null
    /** Called when a peer connection FAILS — lets ConnectionManager self-heal. */
    var onPeerFailed:  ((peer: String) -> Unit)? = null

    suspend fun initialize() = initMutex.withLock {
        if (factory != null) return@withLock
        // REQUIRED before any factory/PeerConnection use: loads the native
        // WebRTC library and initialises globals. Skipping this made
        // createPeerConnectionFactory() throw on first P2P attempt.
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        val turnUrl  = appSettings.turnUrl.first()
        val turnUser = appSettings.turnUsername.first()
        val turnPass = appSettings.turnPassword.first()
        if (turnUrl.isNotEmpty()) {
            cachedIce = cachedIce + PeerConnection.IceServer.builder(turnUrl)
                .setUsername(turnUser)
                .setPassword(turnPass)
                .createIceServer()
        }
    }

    /** Existing peer offers to the newly joined peer. */
    fun createOffer(peer: String) {
        val pc = getOrCreate(peer) ?: return
        val init = DataChannel.Init().apply { ordered = true }
        val dc = pc.createDataChannel("data", init)
        dataChannels[peer] = dc
        dc.registerObserver(dcObserver(peer))

        pc.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SdpAdapter(), sdp)
                signal(peer, "webrtc_offer", org.json.JSONObject().put("sdp", sdp.description))
            }
        }, MediaConstraints())
    }

    fun handleOffer(peer: String, sdp: String) {
        val pc = getOrCreate(peer) ?: return
        pc.setRemoteDescription(SdpAdapter(),
            SessionDescription(SessionDescription.Type.OFFER, sdp))
        pc.createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(answer: SessionDescription) {
                pc.setLocalDescription(SdpAdapter(), answer)
                signal(peer, "webrtc_answer", org.json.JSONObject().put("sdp", answer.description))
            }
        }, MediaConstraints())
    }

    fun handleAnswer(peer: String, sdp: String) {
        connections[peer]?.setRemoteDescription(SdpAdapter(),
            SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun handleIce(peer: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        connections[peer]?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    /** Returns true if the message was sent via P2P. */
    fun send(peer: String, json: String): Boolean {
        if (peer !in openPeers) return false
        val dc = dataChannels[peer]?.takeIf { it.state() == DataChannel.State.OPEN } ?: return false
        val buf = DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false)
        return dc.send(buf)
    }

    fun close(peer: String) {
        openPeers.remove(peer)
        dataChannels.remove(peer)?.dispose()
        connections.remove(peer)?.close()
    }

    fun closeAll() {
        openPeers.clear()
        val peers = connections.keys.toList()
        peers.forEach { close(it) }
    }

    private fun getOrCreate(peer: String): PeerConnection? {
        connections[peer]?.let { return it }
        val f = factory ?: return null
        val config = PeerConnection.RTCConfiguration(cachedIce).apply {
            sdpSemantics   = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy   = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy  = PeerConnection.RtcpMuxPolicy.REQUIRE
            // Gather ALL interfaces incl. IPv6, and TCP candidates so we can
            // still connect where UDP is fully blocked (some captive/corporate
            // networks). Continual gathering lets ICE recover after a network
            // change (Wi-Fi↔cellular) instead of dying on the old candidates.
            candidateNetworkPolicy   = PeerConnection.CandidateNetworkPolicy.ALL
            tcpCandidatePolicy       = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType        = PeerConnection.IceTransportsType.ALL
        }
        val pc = f.createPeerConnection(config, pcObserver(peer)) ?: return null
        connections[peer] = pc
        return pc
    }

    private fun pcObserver(peer: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            signal(peer, "webrtc_ice", org.json.JSONObject()
                .put("sdpMid", c.sdpMid)
                .put("sdpMLineIndex", c.sdpMLineIndex)
                .put("candidate", c.sdp))
        }
        override fun onDataChannel(dc: DataChannel) {
            // Answerer receives the offerer's data channel here
            dataChannels[peer] = dc
            dc.registerObserver(dcObserver(peer))
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.DISCONNECTED ||
                state == PeerConnection.IceConnectionState.CLOSED) {
                openPeers.remove(peer)
            }
            if (state == PeerConnection.IceConnectionState.FAILED) {
                // Hard failure (DISCONNECTED can recover on its own; messages
                // already fall back to the relay) — let the manager rebuild.
                onPeerFailed?.invoke(peer)
            }
        }
        override fun onSignalingChange(s: PeerConnection.SignalingState?)          {}
        override fun onIceConnectionReceivingChange(b: Boolean)                    {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?)    {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?)           {}
        override fun onAddStream(s: MediaStream?)                                  {}
        override fun onRemoveStream(s: MediaStream?)                               {}
        override fun onRenegotiationNeeded()                                       {}
        override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?)       {}
    }

    private fun dcObserver(peer: String) = object : DataChannel.Observer {
        override fun onBufferedAmountChange(p: Long) {}
        override fun onStateChange() {
            val state = dataChannels[peer]?.state() ?: return
            if (state == DataChannel.State.OPEN) {
                openPeers.add(peer)
                onChannelOpen?.invoke(peer)
            } else {
                openPeers.remove(peer)
            }
        }
        override fun onMessage(buf: DataChannel.Buffer) {
            val bytes = ByteArray(buf.data.remaining())
            buf.data.get(bytes)
            onMessage?.invoke(peer, String(bytes, Charsets.UTF_8))
        }
    }

    private fun signal(peer: String, type: String, data: org.json.JSONObject) {
        onSendSignal?.invoke(peer, data.put("type", type).toString())
    }
}

private open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(p: SessionDescription) {}
    override fun onSetSuccess()                          {}
    override fun onCreateFailure(e: String?)             {}
    override fun onSetFailure(e: String?)                {}
}
