package com.helucryptic.android.webrtc

object HubElection {
    /**
     * Elects a hub from [tiers] (username → tier).
     * Highest tier wins. On a tie, the lexicographically smallest username wins
     * (consistent with the server-side tie-break: ascending alphabetical order).
     * Returns null if [tiers] is empty.
     */
    fun elect(tiers: Map<String, Int>): String? =
        tiers.entries
            .maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key }  // descending key = smallest username wins (e.g. "alice" > "bob" descending)
            )
            ?.key

    /** Android is tier 0 (NAT) unless TURN is configured → tier 1. */
    fun myTier(turnUrl: String): Int = if (turnUrl.isNotEmpty()) 1 else 0
}
