package com.helucryptic.android.ui.settings

import android.util.Base64
import androidx.lifecycle.ViewModel
import com.helucryptic.android.crypto.IdentityKeys
import com.helucryptic.android.crypto.IdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

@HiltViewModel
class KeyBackupViewModel @Inject constructor(
    val identityStore: IdentityStore
) : ViewModel() {

    /**
     * Exports identity keys encrypted with [passphrase].
     * Output JSON format:
     *   { "v": 1, "salt": "<b64>", "iv": "<b64>", "ct": "<b64>" }
     * where ct is AES-256-GCM(PBKDF2(passphrase, salt), plaintext).
     */
    fun exportEncrypted(passphrase: String): String? {
        val username = identityStore.username ?: return null
        val xPriv    = identityStore.x25519Priv ?: return null
        val xPub     = identityStore.x25519Pub  ?: return null
        val edPriv   = identityStore.ed25519Priv ?: return null
        val edPub    = identityStore.ed25519Pub  ?: return null

        val plaintext = JSONObject().apply {
            put("username",        username)
            put("x25519_private",  xPriv)
            put("x25519_public",   xPub)
            put("ed25519_private", edPriv)
            put("ed25519_public",  edPub)
        }.toString().toByteArray(Charsets.UTF_8)

        val rng  = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv   = ByteArray(12).also { rng.nextBytes(it) }
        val key  = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)

        return JSONObject().apply {
            put("v",    1)
            put("salt", b64(salt))
            put("iv",   b64(iv))
            put("ct",   b64(ct))
        }.toString()
    }

    /**
     * Decrypts and imports an encrypted backup produced by [exportEncrypted].
     * Returns true on success, false on wrong passphrase or corrupt data.
     */
    fun importEncrypted(json: String, passphrase: String): Boolean {
        return try {
            val outer = JSONObject(json)
            require(outer.getInt("v") == 1) { "Unsupported backup version" }
            val salt = b64d(outer.getString("salt"))
            val iv   = b64d(outer.getString("iv"))
            val ct   = b64d(outer.getString("ct"))
            val key  = deriveKey(passphrase, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ct)   // throws AEADBadTagException on wrong passphrase

            val inner    = JSONObject(String(plaintext, Charsets.UTF_8))
            val username = inner.getString("username")
            val xPriv    = inner.getString("x25519_private")
            val xPub     = inner.getString("x25519_public")
            val edPriv   = inner.getString("ed25519_private")
            val edPub    = inner.getString("ed25519_public")

            if (username.isBlank() || xPriv.isBlank() || xPub.isBlank() ||
                edPriv.isBlank() || edPub.isBlank()) return false

            identityStore.saveIdentity(
                username = username,
                keys = IdentityKeys(
                    x25519Priv  = xPriv,
                    x25519Pub   = xPub,
                    ed25519Priv = edPriv,
                    ed25519Pub  = edPub
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** PBKDF2-SHA256, 200 000 iterations, 256-bit output key. */
    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec    = PBEKeySpec(passphrase.toCharArray(), salt, 200_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray): String =
        Base64.encodeToString(b, Base64.NO_WRAP)

    private fun b64d(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)
}
