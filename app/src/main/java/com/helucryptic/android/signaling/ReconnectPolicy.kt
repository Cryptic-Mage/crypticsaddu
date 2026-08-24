package com.helucryptic.android.signaling

import javax.inject.Inject

class ReconnectPolicy @Inject constructor() {
    private var attempt = 0
    private val baseMs = 1_000L
    private val maxMs  = 60_000L

    fun nextDelayMs(): Long {
        // Clamp the shift: an unbounded `shl attempt` overflows Long after ~54
        // failures, producing negative delays that collapsed to the 100 ms
        // floor - i.e. a long outage degenerated into hammering the server
        // ten times a second. 2^6 s already exceeds maxMs.
        val exp    = (baseMs shl attempt.coerceAtMost(6)).coerceAtMost(maxMs)
        attempt++
        val jitter = (exp * 0.2 * (Math.random() * 2 - 1)).toLong()
        return (exp + jitter).coerceAtLeast(100L)
    }

    fun reset() { attempt = 0 }
}
