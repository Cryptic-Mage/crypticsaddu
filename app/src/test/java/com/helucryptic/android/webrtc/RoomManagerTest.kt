package com.helucryptic.android.webrtc

import com.helucryptic.android.crypto.CryptoManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoomManagerTest {
    private val rm = RoomManager(CryptoManager())

    @Test
    fun `initAsCreator sets group key`() {
        rm.initAsCreator("alice")
        assertNotNull(rm.groupKey)
        assertEquals("alice", rm.roomCreator)
    }

    @Test
    fun `removeMember promotes next in join order`() {
        rm.initAsCreator("alice")
        rm.addMember("bob")
        val promoted = rm.removeMember("alice", "bob")
        assertTrue(promoted)
        assertEquals("bob", rm.roomCreator)
    }

    @Test
    fun `pskProof is deterministic`() {
        val p1 = rm.pskProof("nonce1", "room1", "secret")
        val p2 = rm.pskProof("nonce1", "room1", "secret")
        assertEquals(p1, p2)
    }

    @Test
    fun `wrong psk gives different proof`() {
        val p1 = rm.pskProof("nonce1", "room1", "secret")
        val p2 = rm.pskProof("nonce1", "room1", "wrong")
        assertNotEquals(p1, p2)
    }
}
