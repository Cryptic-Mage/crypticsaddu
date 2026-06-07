package com.helucryptic.android.crypto

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PasetoV4Test {

    private lateinit var crypto: CryptoManager

    @BeforeEach fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any<Int>()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            val javaB64 = if (flags and Base64.URL_SAFE != 0)
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            else
                java.util.Base64.getEncoder().encodeToString(bytes)
            javaB64
        }
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            val s = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0)
                java.util.Base64.getUrlDecoder().decode(s)
            else
                java.util.Base64.getDecoder().decode(s)
        }
        crypto = CryptoManager()
    }

    @Test fun `v4 public sign and verify round-trip`() {
        val keys = crypto.generateIdentityKeys()
        val payload = mapOf("username" to "alice", "iat" to "2026-06-07T00:00:00Z")
        val token = PasetoV4.sign(payload, keys.ed25519Priv)
        assertTrue(token.startsWith("v4.public."))
        val decoded = PasetoV4.verify(token, keys.ed25519Pub)
        assertEquals("alice", decoded["username"])
    }

    @Test fun `v4 public verify fails with wrong key`() {
        val a = crypto.generateIdentityKeys()
        val b = crypto.generateIdentityKeys()
        val token = PasetoV4.sign(mapOf("x" to "1"), a.ed25519Priv)
        assertThrows(Exception::class.java) { PasetoV4.verify(token, b.ed25519Pub) }
    }

    @Test fun `v4 local encrypt and decrypt round-trip`() {
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val payload = mapOf("text" to "hello world", "num" to 42)
        val token = PasetoV4.encrypt(payload, key)
        assertTrue(token.startsWith("v4.local."))
        val decoded = PasetoV4.decrypt(token, key)
        assertEquals("hello world", decoded["text"])
    }

    @Test fun `v4 local decrypt fails with wrong key`() {
        val key1 = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val key2 = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val token = PasetoV4.encrypt(mapOf("x" to "1"), key1)
        assertThrows(Exception::class.java) { PasetoV4.decrypt(token, key2) }
    }

    @Test fun `extractClaimedEd25519Pub returns correct key`() {
        val keys = crypto.generateIdentityKeys()
        val token = PasetoV4.sign(mapOf("ed25519_pub" to keys.ed25519Pub, "username" to "bob"), keys.ed25519Priv)
        val claimed = PasetoV4.extractClaimedEd25519Pub(token)
        assertEquals(keys.ed25519Pub, claimed)
    }

    @Test fun `v4 public token contains hello payload fields`() {
        val keys = crypto.generateIdentityKeys()
        val eph = crypto.generateEphemeralX25519()
        val payload = mapOf(
            "username"       to "alice",
            "x25519_pub"     to keys.x25519Pub,
            "ed25519_pub"    to keys.ed25519Pub,
            "eph_x25519_pub" to eph.pub,
            "iat"            to "2026-06-07T00:00:00Z"
        )
        val token = PasetoV4.sign(payload, keys.ed25519Priv)
        val decoded = PasetoV4.verify(token, keys.ed25519Pub)
        assertEquals("alice",         decoded["username"])
        assertEquals(keys.x25519Pub,  decoded["x25519_pub"])
        assertEquals(eph.pub,         decoded["eph_x25519_pub"])
    }
}
