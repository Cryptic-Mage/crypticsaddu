package com.helucryptic.android.crypto

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CryptoManagerTest {

    private lateinit var crypto: CryptoManager

    @BeforeEach fun setUp() {
        // Mock Android Base64 with Java's Base64 for JVM tests
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        crypto = CryptoManager()
    }

    @Test fun `generateIdentityKeys returns non-empty keys`() {
        val keys = crypto.generateIdentityKeys()
        assertTrue(keys.x25519Priv.isNotEmpty())
        assertTrue(keys.x25519Pub.isNotEmpty())
        assertTrue(keys.ed25519Priv.isNotEmpty())
        assertTrue(keys.ed25519Pub.isNotEmpty())
    }

    @Test fun `generateIdentityKeys returns 32-byte keys`() {
        val keys = crypto.generateIdentityKeys()
        assertEquals(32, java.util.Base64.getDecoder().decode(keys.x25519Priv).size)
        assertEquals(32, java.util.Base64.getDecoder().decode(keys.ed25519Priv).size)
    }

    @Test fun `deriveSessionKeyV2 is symmetric between two peers`() {
        val a = crypto.generateIdentityKeys()
        val b = crypto.generateIdentityKeys()
        val aEph = crypto.generateEphemeralX25519()
        val bEph = crypto.generateEphemeralX25519()
        val keyAB = crypto.deriveSessionKeyV2(a.x25519Priv, aEph.priv, b.x25519Pub, bEph.pub)
        val keyBA = crypto.deriveSessionKeyV2(b.x25519Priv, bEph.priv, a.x25519Pub, aEph.pub)
        assertArrayEquals(keyAB, keyBA)
    }

    @Test fun `aesGcmEncryptDecrypt round-trips plaintext`() {
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val plain = """{"text":"hello world"}""".toByteArray()
        val ct = crypto.aeadEncrypt(plain, key)
        val pt = crypto.aeadDecrypt(ct, key)
        assertArrayEquals(plain, pt)
    }

    @Test fun `different keys produce different ciphertext`() {
        val key1 = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val key2 = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val plain = "test".toByteArray()
        assertFalse(crypto.aeadEncrypt(plain, key1).contentEquals(crypto.aeadEncrypt(plain, key2)))
    }

    @Test fun `ed25519 sign and verify succeeds with correct key`() {
        val keys = crypto.generateIdentityKeys()
        val msg = "test message".toByteArray()
        val sig = crypto.ed25519Sign(msg, keys.ed25519Priv)
        assertTrue(crypto.ed25519Verify(msg, sig, keys.ed25519Pub))
    }

    @Test fun `ed25519 verify fails with wrong key`() {
        val a = crypto.generateIdentityKeys()
        val b = crypto.generateIdentityKeys()
        val msg = "test".toByteArray()
        val sig = crypto.ed25519Sign(msg, a.ed25519Priv)
        assertFalse(crypto.ed25519Verify(msg, sig, b.ed25519Pub))
    }

    @Test fun `fingerprint is 79 chars (64 hex + 15 spaces)`() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        val keys = crypto.generateIdentityKeys()
        val fp = Fingerprint.compute(keys.x25519Pub)
        assertEquals(79, fp.length)
        assertTrue(fp.matches(Regex("[0-9A-F]{4}( [0-9A-F]{4}){15}")))
    }
}
