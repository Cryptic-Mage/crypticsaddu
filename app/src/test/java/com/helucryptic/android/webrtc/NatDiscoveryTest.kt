package com.helucryptic.android.webrtc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure classification/prediction logic of [NatDiscovery].
 * (The socket I/O path needs a real network and is exercised on-device.)
 *
 * Mirrors the desktop nat_discovery.py tests so both platforms classify the
 * same observations identically.
 */
class NatDiscoveryTest {

    // ── prediction ───────────────────────────────────────────────────────────

    @Test
    fun `sequential symmetric predicts next port from delta`() {
        val p = NatDiscovery.Profile(
            type = NatDiscovery.NatType.SEQUENTIAL_SYMMETRIC,
            extIp = "203.0.113.5",
            portDelta = 2,
            predictable = true,
            samples = listOf(50000, 50002, 50004),
        )
        assertEquals(50006, NatDiscovery.predictNextPort(p))          // last + delta
        assertEquals(50008, NatDiscovery.predictNextPort(p, lookahead = 2))
    }

    @Test
    fun `random symmetric yields no prediction`() {
        val p = NatDiscovery.Profile(
            type = NatDiscovery.NatType.RANDOM_SYMMETRIC,
            samples = listOf(50000, 51234, 49001),
        )
        assertNull(NatDiscovery.predictNextPort(p))
    }

    @Test
    fun `prediction clamps out-of-range ports`() {
        val p = NatDiscovery.Profile(
            type = NatDiscovery.NatType.SEQUENTIAL_SYMMETRIC,
            portDelta = 5, predictable = true, samples = listOf(65534),
        )
        assertNull(NatDiscovery.predictNextPort(p))   // 65539 > 65535
    }

    // ── profile semantics ─────────────────────────────────────────────────────

    @Test
    fun `needsRelay only for random symmetric or blocked`() {
        assertTrue(NatDiscovery.Profile(NatDiscovery.NatType.RANDOM_SYMMETRIC).needsRelay)
        assertTrue(NatDiscovery.Profile(NatDiscovery.NatType.BLOCKED).needsRelay)
        assertFalse(NatDiscovery.Profile(NatDiscovery.NatType.ENDPOINT_INDEPENDENT).needsRelay)
        assertFalse(NatDiscovery.Profile(NatDiscovery.NatType.SEQUENTIAL_SYMMETRIC).needsRelay)
        assertFalse(NatDiscovery.Profile(NatDiscovery.NatType.OPEN_INTERNET).needsRelay)
    }

    @Test
    fun `summary is human readable for each type`() {
        for (t in NatDiscovery.NatType.values()) {
            assertTrue(NatDiscovery.Profile(t).summary.isNotBlank())
        }
    }
}
