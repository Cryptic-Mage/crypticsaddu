package com.helucryptic.android.crypto

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class IdentityKeys(
    val x25519Priv: String,
    val x25519Pub: String,
    val ed25519Priv: String,
    val ed25519Pub: String
)

data class EphemeralKey(val priv: String, val pub: String)

@Singleton
class CryptoManager @Inject constructor() {
    private val rng = SecureRandom()

    fun generateIdentityKeys(): IdentityKeys {
        val xPriv = X25519PrivateKeyParameters(rng)
        val ePriv = Ed25519PrivateKeyParameters(rng)
        return IdentityKeys(
            x25519Priv  = xPriv.encoded.b64(),
            x25519Pub   = xPriv.generatePublicKey().encoded.b64(),
            ed25519Priv = ePriv.encoded.b64(),
            ed25519Pub  = ePriv.generatePublicKey().encoded.b64()
        )
    }

    fun generateEphemeralX25519(): EphemeralKey {
        val priv = X25519PrivateKeyParameters(rng)
        return EphemeralKey(
            priv = priv.encoded.b64(),
            pub  = priv.generatePublicKey().encoded.b64()
        )
    }

    /** helucryptic-session-v2: 3-DH with ephemeral keys for forward secrecy.
     *  Matches desktop crypto.py derive_session_key_v2(). */
    fun deriveSessionKeyV2(
        myXPrivB64: String, myEphPrivB64: String,
        peerXPubB64: String, peerEphPubB64: String
    ): ByteArray {
        val myXPriv    = X25519PrivateKeyParameters(myXPrivB64.b64d(), 0)
        val myEphPriv  = X25519PrivateKeyParameters(myEphPrivB64.b64d(), 0)
        val peerXPub   = X25519PublicKeyParameters(peerXPubB64.b64d(), 0)
        val peerEphPub = X25519PublicKeyParameters(peerEphPubB64.b64d(), 0)

        fun dh(priv: X25519PrivateKeyParameters, pub: X25519PublicKeyParameters): ByteArray {
            val out = ByteArray(32)
            val agr = X25519Agreement()
            agr.init(priv)
            agr.calculateAgreement(pub, out, 0)
            return out
        }

        val dhEE = dh(myEphPriv, peerEphPub)
        val myXPubRaw   = myXPriv.generatePublicKey().encoded
        val peerXPubRaw = peerXPub.encoded
        val dhA: ByteArray
        val dhB: ByteArray
        if (myXPubRaw.lexLt(peerXPubRaw)) {
            dhA = dh(myXPriv,   peerEphPub)   // low_static × high_eph
            dhB = dh(myEphPriv, peerXPub)     // low_eph × high_static
        } else {
            dhA = dh(myEphPriv, peerXPub)     // low_static × high_eph (from high's perspective)
            dhB = dh(myXPriv,   peerEphPub)   // low_eph × high_static (from high's perspective)
        }
        return hkdf(dhEE + dhA + dhB, "helucryptic-session-v2".toByteArray())
    }

    /** AES-256-GCM encrypt. Output = 12-byte nonce || ciphertext+tag. */
    fun aeadEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return nonce + out
    }

    /** AES-256-GCM decrypt. Input = 12-byte nonce || ciphertext+tag. */
    fun aeadDecrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ciphertext.copyOf(12)
        val ct    = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(ct.size))
        val len = cipher.processBytes(ct, 0, ct.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    fun ed25519Sign(message: ByteArray, privB64: String): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privB64.b64d(), 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(message: ByteArray, sig: ByteArray, pubB64: String): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(pubB64.b64d(), 0))
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(sig)
    }

    fun hkdf(ikm: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, null, info))
        return ByteArray(length).also { gen.generateBytes(it, 0, it.size) }
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    fun String.b64d() = Base64.decode(this, Base64.NO_WRAP)

    private fun ByteArray.lexLt(other: ByteArray): Boolean {
        for (i in indices) {
            val c = (this[i].toInt() and 0xFF).compareTo(other[i].toInt() and 0xFF)
            if (c != 0) return c < 0
        }
        return false
    }
}
