package com.helucryptic.android.webrtc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom

/**
 * NAT behaviour discovery + port prediction (RFC 5780 / RFC 8489), the Kotlin
 * mirror of the desktop `nat_discovery.py`. Pure UDP sockets — no WebRTC
 * dependency — so it can run before the PeerConnectionFactory is up.
 *
 * Decides which non-relay traversal strategy can work:
 *  - endpoint-independent (cone) → STUN hole-punch works;
 *  - sequential-symmetric → external ports are predictable, so we can advertise
 *    a predicted srflx candidate and punch a symmetric NAT WITHOUT a relay;
 *  - random-symmetric / blocked → must relay (TURN or the signaling fallback).
 *
 * Fully timeout-bounded; any failure yields [NatType.UNKNOWN]/[NatType.BLOCKED]
 * rather than hanging.
 */
object NatDiscovery {

    enum class NatType { OPEN_INTERNET, ENDPOINT_INDEPENDENT, SEQUENTIAL_SYMMETRIC, RANDOM_SYMMETRIC, BLOCKED, UNKNOWN }

    data class Profile(
        val type: NatType,
        val extIp: String = "",
        val portDelta: Int = 0,
        val predictable: Boolean = false,
        val samples: List<Int> = emptyList(),
    ) {
        val needsRelay: Boolean get() = type == NatType.RANDOM_SYMMETRIC || type == NatType.BLOCKED
        val summary: String get() = when (type) {
            NatType.OPEN_INTERNET        -> "Open / no NAT — direct works"
            NatType.ENDPOINT_INDEPENDENT -> "Cone NAT — STUN hole-punch works"
            NatType.SEQUENTIAL_SYMMETRIC -> "Symmetric NAT, sequential ports (Δ≈$portDelta) — prediction possible"
            NatType.RANDOM_SYMMETRIC     -> "Symmetric NAT, random ports — relay required"
            NatType.BLOCKED              -> "STUN blocked — relay required"
            NatType.UNKNOWN              -> "Unknown"
        }
    }

    private const val MAGIC_COOKIE = 0x2112A442.toInt()
    private const val BINDING_REQUEST = 0x0001
    private const val BINDING_SUCCESS = 0x0101
    private const val ATTR_MAPPED = 0x0001
    private const val ATTR_XOR_MAPPED = 0x0020
    private val rng = SecureRandom()

    private val STUN_SERVERS = listOf(
        "stun.l.google.com" to 19302,
        "stun1.l.google.com" to 19302,
        "stun.cloudflare.com" to 3478,
    )

    /** Probe several STUN servers from fresh sockets and classify the NAT. */
    suspend fun discover(timeoutMs: Int = 2000): Profile = withContext(Dispatchers.IO) {
        val resolved = STUN_SERVERS.mapNotNull { (h, p) ->
            runCatching { InetSocketAddress(InetAddress.getByName(h), p) }.getOrNull()
        }.distinctBy { it.address.hostAddress }
        if (resolved.isEmpty()) return@withContext Profile(NatType.BLOCKED)

        val externalPorts = mutableListOf<Int>()
        val samples = mutableListOf<Int>()
        var extIp = ""
        for (server in resolved) {
            val res = stunQuery(server, timeoutMs) ?: continue
            extIp = res.first
            externalPorts += res.second
            samples += res.second
        }
        if (externalPorts.isEmpty()) return@withContext Profile(NatType.BLOCKED)

        val localIp = localIpv4()
        if (localIp != null && extIp == localIp) {
            return@withContext Profile(NatType.OPEN_INTERNET, extIp = extIp, samples = samples)
        }
        if (externalPorts.size < 2 || externalPorts.toSet().size == 1) {
            // One sample, or same external port to different servers → cone.
            return@withContext Profile(NatType.ENDPOINT_INDEPENDENT, extIp = extIp, samples = samples)
        }
        classifySymmetric(extIp, externalPorts, samples)
    }

    /** Predict the external port for the NEXT new mapping (sequential NAT only). */
    fun predictNextPort(profile: Profile, lookahead: Int = 1): Int? {
        if (!profile.predictable || profile.samples.isEmpty()) return null
        val predicted = profile.samples.max() + profile.portDelta * lookahead
        return predicted.takeIf { it in 1024..65535 }
    }

    // ── internals ──────────────────────────────────────────────────────────

    private fun classifySymmetric(extIp: String, ports: List<Int>, samples: List<Int>): Profile {
        val ordered = ports.sorted()
        val deltas = ordered.zipWithNext { a, b -> b - a }
        if (deltas.isEmpty()) return Profile(NatType.RANDOM_SYMMETRIC, extIp = extIp, samples = samples)
        val maxDelta = deltas.max()
        val meanDelta = Math.round(deltas.average()).toInt()
        return if (maxDelta in 1..16) {
            Profile(NatType.SEQUENTIAL_SYMMETRIC, extIp = extIp,
                portDelta = maxOf(1, meanDelta), predictable = true, samples = samples)
        } else {
            Profile(NatType.RANDOM_SYMMETRIC, extIp = extIp, samples = samples)
        }
    }

    /** One STUN BINDING request on a fresh socket → (externalIp, externalPort). */
    private fun stunQuery(server: InetSocketAddress, timeoutMs: Int): Pair<String, Int>? {
        val sock = runCatching { DatagramSocket() }.getOrNull() ?: return null
        return try {
            sock.soTimeout = timeoutMs
            val txid = ByteArray(12).also { rng.nextBytes(it) }
            val req = buildBindingRequest(txid)
            sock.send(DatagramPacket(req, req.size, server.address, server.port))
            val buf = ByteArray(512)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            parseMappedAddress(buf.copyOf(resp.length), txid)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { sock.close() }
        }
    }

    private fun buildBindingRequest(txid: ByteArray): ByteArray {
        val b = ByteArray(20)
        putU16(b, 0, BINDING_REQUEST)
        putU16(b, 2, 0)                 // length 0
        putU32(b, 4, MAGIC_COOKIE)
        System.arraycopy(txid, 0, b, 8, 12)
        return b
    }

    private fun parseMappedAddress(data: ByteArray, txid: ByteArray): Pair<String, Int>? {
        if (data.size < 20) return null
        val msgType = getU16(data, 0)
        val msgLen = getU16(data, 2)
        if (msgType != BINDING_SUCCESS) return null
        for (i in 0 until 12) if (data[8 + i] != txid[i]) return null
        var off = 20
        val end = minOf(20 + msgLen, data.size)
        var result: Pair<String, Int>? = null
        while (off + 4 <= end) {
            val atype = getU16(data, off)
            val alen = getU16(data, off + 4 - 2)
            val vStart = off + 4
            if (vStart + alen > data.size) break
            val value = data.copyOfRange(vStart, vStart + alen)
            when (atype) {
                ATTR_XOR_MAPPED -> decodeXorMapped(value, txid)?.let { result = it }
                ATTR_MAPPED     -> if (result == null) decodePlainMapped(value)?.let { result = it }
            }
            off = vStart + alen + ((4 - alen % 4) % 4)   // 32-bit alignment
        }
        return result
    }

    private fun decodeXorMapped(v: ByteArray, txid: ByteArray): Pair<String, Int>? {
        if (v.size < 8) return null
        val family = v[1].toInt() and 0xFF
        val xport = getU16(v, 2) xor (MAGIC_COOKIE ushr 16 and 0xFFFF)
        return when (family) {
            0x01 -> {
                val xip = getU32(v, 4) xor MAGIC_COOKIE
                val ip = "${xip ushr 24 and 0xFF}.${xip ushr 16 and 0xFF}.${xip ushr 8 and 0xFF}.${xip and 0xFF}"
                ip to xport
            }
            0x02 -> {
                if (v.size < 20) return null
                val cookieTxid = ByteArray(16)
                putU32(cookieTxid, 0, MAGIC_COOKIE)
                System.arraycopy(txid, 0, cookieTxid, 4, 12)
                val raw = ByteArray(16) { (v[4 + it].toInt() xor cookieTxid[it].toInt()).toByte() }
                InetAddress.getByAddress(raw).hostAddress to xport
            }
            else -> null
        }
    }

    private fun decodePlainMapped(v: ByteArray): Pair<String, Int>? {
        if (v.size < 8) return null
        val family = v[1].toInt() and 0xFF
        val port = getU16(v, 2)
        return when (family) {
            0x01 -> "${v[4].toInt() and 0xFF}.${v[5].toInt() and 0xFF}.${v[6].toInt() and 0xFF}.${v[7].toInt() and 0xFF}" to port
            0x02 -> if (v.size >= 20) InetAddress.getByAddress(v.copyOfRange(4, 20)).hostAddress to port else null
            else -> null
        }
    }

    private fun localIpv4(): String? = runCatching {
        DatagramSocket().use { s ->
            s.connect(InetAddress.getByName("8.8.8.8"), 53)
            s.localAddress.hostAddress
        }
    }.getOrNull()

    // big-endian helpers
    private fun putU16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
    private fun putU32(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte()
        b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
    }
    private fun getU16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun getU32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
