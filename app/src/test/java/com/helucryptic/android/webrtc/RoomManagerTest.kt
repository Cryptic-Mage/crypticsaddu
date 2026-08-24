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
    fun `promotion keeps an existing group key (no room split)`() {
        // bob already holds the room key when the creator leaves - promotion
        // must NOT regenerate it, or the remaining members (still on the old
        // key) could no longer decrypt bob's messages.
        rm.initAsCreator("alice")            // gives this instance a key
        val keyBefore = rm.groupKey!!.copyOf()
        rm.addMember("bob")
        val promoted = rm.removeMember("alice", myUsername = "bob")
        assertTrue(promoted)
        assertArrayEquals(keyBefore, rm.groupKey)
    }

    @Test
    fun `setCreator pins the trust anchor`() {
        rm.setCreator("carol")
        assertEquals("carol", rm.roomCreator)
        rm.setCreator("")                    // empty must not clobber
        assertEquals("carol", rm.roomCreator)
    }

    @Test
    fun `pskProof is deterministic`() {
        val p1 = rm.pskProof("nonce1", "room1", "c2VjcmV0", responder = "alice")
        val p2 = rm.pskProof("nonce1", "room1", "c2VjcmV0", responder = "alice")
        assertEquals(p1, p2)
    }

    @Test
    fun `wrong psk gives different proof`() {
        val p1 = rm.pskProof("nonce1", "room1", "c2VjcmV0", responder = "alice")
        val p2 = rm.pskProof("nonce1", "room1", "d3Jvbmc=", responder = "alice")
        assertNotEquals(p1, p2)
    }

    @Test
    fun `proof is bound to the responder identity (anti-reflection)`() {
        // A proof produced by alice answering a challenge can never satisfy a
        // verifier expecting mallory as the responder - this is what stops an
        // attacker reflecting alice's own nonce back at her and replaying her
        // answer as their response.
        val asAlice   = rm.pskProof("NONCE", "room1", "c2VjcmV0", responder = "alice")
        val asMallory = rm.pskProof("NONCE", "room1", "c2VjcmV0", responder = "mallory")
        assertNotEquals(asAlice, asMallory)
    }

    @Test
    fun `proof is bound to the room id`() {
        val p1 = rm.pskProof("NONCE", "ROOM-AB12", "c2VjcmV0", responder = "alice")
        val p2 = rm.pskProof("NONCE", "ROOM-ZZ99", "c2VjcmV0", responder = "alice")
        assertNotEquals(p1, p2)
    }
}
