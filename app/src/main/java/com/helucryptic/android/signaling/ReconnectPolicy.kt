package com.helucryptic.android.signaling

import javax.inject.Inject

class ReconnectPolicy @Inject constructor() {
    private var attempt = 0
    private val baseMs = 1_000L
    private val maxMs  = 60_000L

    fun nextDelayMs(): Long {
        val exp    = (baseMs shl attempt).coerceAtMost(maxMs)
        attempt++
        val jitter = (exp * 0.2 * (Math.random() * 2 - 1)).toLong()
        return (exp + jitter).coerceAtLeast(100L)
    }

    fun reset() { attempt = 0 }
}
