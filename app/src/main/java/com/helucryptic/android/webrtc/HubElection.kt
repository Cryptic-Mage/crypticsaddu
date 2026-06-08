package com.helucryptic.android.webrtc

object HubElection {
    /** Highest tier wins; alphabetical ascending on tie. */
    fun elect(tiers: Map<String, Int>): String =
        tiers.entries
            .maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key }   // "alice" > "bob" in descending → alice wins tie
            )!!
            .key

    /** Android is tier 0 (NAT) unless TURN is configured → tier 1. */
    fun myTier(turnUrl: String): Int = if (turnUrl.isNotEmpty()) 1 else 0
}
