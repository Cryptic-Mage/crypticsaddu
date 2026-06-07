package com.helucryptic.android.crypto

import android.util.Base64
import java.security.MessageDigest

object Fingerprint {
    /** SHA-256 of x25519 public key bytes, hex upper-case, chunked every 4 chars.
     *  Matches desktop crypto.py compute_fingerprint(x25519_pub_b64). */
    fun compute(x25519PubB64: String): String {
        val raw = Base64.decode(x25519PubB64, Base64.NO_WRAP)
        val hex = MessageDigest.getInstance("SHA-256").digest(raw)
            .joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString(" ")
    }
}
