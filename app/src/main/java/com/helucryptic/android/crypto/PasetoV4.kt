package com.helucryptic.android.crypto

import android.util.Base64
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * PASETO v4 implementation wire-compatible with pyseto (Python desktop client).
 * v4.public: Ed25519 sign/verify with PAE encoding.
 * v4.local:  XChaCha20 + BLAKE2b-MAC, matching the PASETO v4 spec exactly.
 *
 * v4.local algorithm (from PASETO spec §5.3):
 *   1. n  = random 32 bytes
 *   2. Ek = BLAKE2b-MAC(key=k, msg="paseto-encryption-key" || n, size=32) [first 32 of 56-byte output]
 *   3. n2 = BLAKE2b-MAC(key=k, msg="paseto-encryption-key" || n, size=56)[32:56]
 *   4. Ak = BLAKE2b-MAC(key=k, msg="paseto-auth-key-for-aead" || n, size=32)
 *   5. c  = XChaCha20(key=Ek, nonce=n2, plaintext=m)
 *   6. t  = BLAKE2b-MAC(key=Ak, msg=PAE(h,n,c,f,i), size=32)
 *   7. token = h || base64url(n || c || t)
 */
object PasetoV4 {
    private const val HDR_PUBLIC = "v4.public."
    private const val HDR_LOCAL  = "v4.local."
    private val rng = SecureRandom()

    // ── v4.public ─────────────────────────────────────────────────────────────

    fun sign(payload: Map<String, Any>, ed25519PrivB64: String): String {
        val m   = JSONObject(payload).toString().toByteArray(Charsets.UTF_8)
        // PAE: [header, message, footer="", implicit_assertion=""] - 4 pieces per PASETO v4 spec
        val pae = pae(HDR_PUBLIC.toByteArray(), m, ByteArray(0), ByteArray(0))
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(b64d(ed25519PrivB64), 0))
        signer.update(pae, 0, pae.size)
        val sig = signer.generateSignature()
        return HDR_PUBLIC + b64url(m + sig)
    }

    fun verify(token: String, ed25519PubB64: String): Map<String, Any> {
        require(token.startsWith(HDR_PUBLIC)) { "Not a v4.public token" }
        val raw = b64urlD(token.removePrefix(HDR_PUBLIC))
        require(raw.size > 64) { "Token too short" }
        val m   = raw.copyOf(raw.size - 64)
        val sig = raw.copyOfRange(raw.size - 64, raw.size)
        // PAE: [header, message, footer="", implicit_assertion=""] - 4 pieces per PASETO v4 spec
        val pae = pae(HDR_PUBLIC.toByteArray(), m, ByteArray(0), ByteArray(0))
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(b64d(ed25519PubB64), 0))
        verifier.update(pae, 0, pae.size)
        require(verifier.verifySignature(sig)) { "Ed25519 signature verification failed" }
        return jsonToMap(JSONObject(String(m, Charsets.UTF_8)))
    }

    /** Extract the ed25519_pub field from a v4.public token WITHOUT verifying the signature.
     *  Used during TOFU first-contact to get the claimed key for verification. */
    fun extractClaimedEd25519Pub(token: String): String? {
        return try {
            require(token.startsWith(HDR_PUBLIC))
            val raw = b64urlD(token.removePrefix(HDR_PUBLIC))
            val m = raw.copyOf(raw.size - 64)
            JSONObject(String(m, Charsets.UTF_8)).optString("ed25519_pub").takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    // ── v4.local ──────────────────────────────────────────────────────────────

    fun encrypt(payload: Map<String, Any>, key: ByteArray): String {
        require(key.size == 32) { "Key must be 32 bytes" }
        val n = ByteArray(32).also { rng.nextBytes(it) }
        val m = JSONObject(payload).toString().toByteArray(Charsets.UTF_8)

        val (ek, n2) = deriveEncKey(key, n)
        val ak = deriveAuthKey(key, n)

        val c   = xChaCha20(ek, n2, m, encrypt = true)
        val pae = pae(HDR_LOCAL.toByteArray(), n, c, ByteArray(0), ByteArray(0))
        val t   = blake2bMac(ak, pae, 32)

        return HDR_LOCAL + b64url(n + c + t)
    }

    fun decrypt(token: String, key: ByteArray): Map<String, Any> {
        require(token.startsWith(HDR_LOCAL)) { "Not a v4.local token" }
        require(key.size == 32) { "Key must be 32 bytes" }
        val raw = b64urlD(token.removePrefix(HDR_LOCAL))
        // raw = n(32) + c(variable) + t(32)
        require(raw.size > 64) { "Token too short" }
        val n = raw.copyOf(32)
        val c = raw.copyOfRange(32, raw.size - 32)
        val t = raw.copyOfRange(raw.size - 32, raw.size)

        val ak  = deriveAuthKey(key, n)
        val pae = pae(HDR_LOCAL.toByteArray(), n, c, ByteArray(0), ByteArray(0))
        val t2  = blake2bMac(ak, pae, 32)
        require(constantTimeEquals(t, t2)) { "BLAKE2b authentication tag mismatch" }

        val (ek, n2) = deriveEncKey(key, n)
        val pt = xChaCha20(ek, n2, c, encrypt = false)
        return jsonToMap(JSONObject(String(pt, Charsets.UTF_8)))
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** PAE: Pre-Authentication Encoding per PASETO spec. */
    private fun pae(vararg pieces: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(le64(pieces.size.toLong()).toList())
        for (p in pieces) {
            out.addAll(le64(p.size.toLong()).toList())
            out.addAll(p.toList())
        }
        return out.toByteArray()
    }

    private fun le64(n: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array()

    /**
     * BLAKE2b keyed MAC: hashlib.blake2b(key=key, digest_size=size).update(msg).
     * BouncyCastle constructor: Blake2bDigest(key, digestLengthBytes, salt, personalization).
     */
    private fun blake2bMac(key: ByteArray, msg: ByteArray, sizeBytes: Int): ByteArray {
        val digest = Blake2bDigest(key, sizeBytes, null, null)
        digest.update(msg, 0, msg.size)
        return ByteArray(sizeBytes).also { digest.doFinal(it, 0) }
    }

    /**
     * Derives Ek (32 bytes) and n2 (24 bytes) from key k and nonce n.
     * tmp = BLAKE2b-MAC(key=k, msg="paseto-encryption-key"||n, size=56)
     * Ek = tmp[0:32], n2 = tmp[32:56]
     */
    private fun deriveEncKey(key: ByteArray, n: ByteArray): Pair<ByteArray, ByteArray> {
        val info = "paseto-encryption-key".toByteArray(Charsets.UTF_8) + n
        val tmp  = blake2bMac(key, info, 56)
        return Pair(tmp.copyOf(32), tmp.copyOfRange(32, 56))
    }

    /**
     * Derives Ak (32 bytes) from key k and nonce n.
     * Ak = BLAKE2b-MAC(key=k, msg="paseto-auth-key-for-aead"||n, size=32)
     */
    private fun deriveAuthKey(key: ByteArray, n: ByteArray): ByteArray {
        val info = "paseto-auth-key-for-aead".toByteArray(Charsets.UTF_8) + n
        return blake2bMac(key, info, 32)
    }

    /**
     * XChaCha20 stream cipher (plain, no AEAD tag).
     * nonce is 24 bytes. Internally: HChaCha20 subkey derivation + ChaCha20 (7539).
     *
     * HChaCha20 spec (RFC draft):
     *   - Input: key (32 bytes), nonce[0:16] (first 16 bytes of XChaCha20 nonce)
     *   - Output: 32-byte subkey from the first and last 128-bit words of the ChaCha20 block
     * Then: ChaCha7539 with subkey and 12-byte nonce = [0,0,0,0] || nonce[16:24]
     */
    private fun xChaCha20(key: ByteArray, nonce24: ByteArray, input: ByteArray, encrypt: Boolean): ByteArray {
        require(nonce24.size == 24) { "XChaCha20 nonce must be 24 bytes" }
        val subkey    = hChaCha20(key, nonce24.copyOf(16))
        val nonce12   = ByteArray(12)
        // counter = 0 (first 4 bytes), then nonce[16:24] as bytes 4-11
        System.arraycopy(nonce24, 16, nonce12, 4, 8)
        return chaCha7539(subkey, nonce12, input)
    }

    /**
     * HChaCha20: produces a 32-byte subkey from key+nonce[0:16].
     * Implements the ChaCha20 block function but returns words 0-3 and 12-15
     * (the first and last 4 words, i.e. output[0:16] || output[48:64]).
     */
    private fun hChaCha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        require(key.size == 32)
        require(nonce16.size == 16)

        // ChaCha20 constants "expand 32-byte k"
        val state = IntArray(16)
        state[0]  = 0x61707865
        state[1]  = 0x3320646e
        state[2]  = 0x79622d32
        state[3]  = 0x6b206574

        // Key (8 words)
        for (i in 0..7) {
            state[4 + i] = leInt(key, i * 4)
        }

        // Counter = 0, nonce (4 words from nonce16)
        state[12] = 0
        state[13] = 0
        state[14] = leInt(nonce16, 0)
        state[15] = leInt(nonce16, 4)
        // Note: nonce16 is 16 bytes but ChaCha20 block uses words 12-15:
        // word12=counter(low), word13=counter(high), word14=nonce[0:4], word15=nonce[4:8]
        // For HChaCha20, the full 16 nonce bytes fill words 12-15
        state[12] = leInt(nonce16, 0)
        state[13] = leInt(nonce16, 4)
        state[14] = leInt(nonce16, 8)
        state[15] = leInt(nonce16, 12)

        val working = state.copyOf()
        repeat(20) { i ->
            if (i % 2 == 0) {
                // Column round
                quarterRound(working, 0, 4, 8, 12)
                quarterRound(working, 1, 5, 9, 13)
                quarterRound(working, 2, 6, 10, 14)
                quarterRound(working, 3, 7, 11, 15)
            } else {
                // Diagonal round
                quarterRound(working, 0, 5, 10, 15)
                quarterRound(working, 1, 6, 11, 12)
                quarterRound(working, 2, 7, 8, 13)
                quarterRound(working, 3, 4, 9, 14)
            }
        }

        // HChaCha20 output: words 0-3 and 12-15 (NOT added to initial state)
        val out = ByteArray(32)
        for (i in 0..3) {
            putLeInt(out, i * 4, working[i])
        }
        for (i in 0..3) {
            putLeInt(out, 16 + i * 4, working[12 + i])
        }
        return out
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] = s[a] + s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] = s[c] + s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] = s[a] + s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] = s[c] + s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun putLeInt(b: ByteArray, off: Int, v: Int) {
        b[off]     = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    /** ChaCha20 (IETF/7539 variant): 32-byte key, 12-byte nonce. */
    private fun chaCha7539(key: ByteArray, nonce12: ByteArray, input: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce12))
        val out = ByteArray(input.size)
        engine.processBytes(input, 0, input.size, out, 0)
        return out
    }

    /** Constant-time byte array comparison to prevent timing attacks. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun b64url(b: ByteArray): String =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun b64urlD(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP)

    private fun b64d(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)

    @Suppress("UNCHECKED_CAST")
    private fun jsonToMap(obj: JSONObject): Map<String, Any> =
        obj.keys().asSequence().associateWith { obj.get(it) }
}
