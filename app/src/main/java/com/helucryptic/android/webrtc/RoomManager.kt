package com.helucryptic.android.webrtc

import android.util.Base64
import com.helucryptic.android.crypto.CryptoManager
import com.helucryptic.android.crypto.PasetoV4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomManager @Inject constructor(private val crypto: CryptoManager) {

    private val _members = MutableStateFlow<Set<String>>(emptySet())
    val members: StateFlow<Set<String>> = _members

    var groupKey: ByteArray? = null
        private set
    var roomCreator: String = ""
        private set
    private val joinOrder = mutableListOf<String>()

    fun initAsCreator(myUsername: String) {
        groupKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        roomCreator = myUsername
        joinOrder.clear()
        joinOrder.add(myUsername)
        _members.value = setOf(myUsername)
    }

    fun addMember(username: String) {
        if (username !in joinOrder) joinOrder.add(username)
        _members.value = _members.value + username
    }

    /** Member side: record who the room creator is (trust anchor for group_key). */
    fun setCreator(username: String) {
        if (username.isNotEmpty()) roomCreator = username
    }

    /** Returns true if this client was promoted to creator (must re-broadcast group key). */
    fun removeMember(username: String, myUsername: String): Boolean {
        _members.value = _members.value - username
        joinOrder.remove(username)
        if (username == roomCreator) {
            val next = joinOrder.firstOrNull() ?: return false
            roomCreator = next
            if (next == myUsername) {
                // KEEP the existing group key if we already hold one — generating
                // a fresh key here split the room: peers that already had the old
                // key ignored the re-broadcast and could no longer decrypt the
                // promoted creator's messages. Only generate when orphaned
                // (creator left before we ever received the key). Matches desktop.
                if (groupKey == null) {
                    groupKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
                }
                return true
            }
        }
        return false
    }

    fun encryptGroupKeyFor(sessionKey: ByteArray): String {
        val gk = groupKey ?: error("No group key")
        return PasetoV4.encrypt(
            mapOf("group_key" to Base64.encodeToString(gk, Base64.NO_WRAP)),
            sessionKey
        )
    }

    fun installGroupKey(token: String, sessionKey: ByteArray) {
        val payload = PasetoV4.decrypt(token, sessionKey)
        groupKey = Base64.decode(payload["group_key"] as String, Base64.NO_WRAP)
    }

    /**
     * HMAC-SHA256(key=base64decode(psk), msg="nonce|roomId|responder") — hex.
     *
     * Wire-compatible with desktop webrtc_engine._psk_proof():
     *  - the PSK is a base64-encoded 32-byte key and must be DECODED before
     *    use as the HMAC key (the old code MAC'd the base64 text itself,
     *    which was incompatible with desktop peers);
     *  - "|" separators prevent ambiguous concatenation;
     *  - binding the RESPONDER's username defeats the reflection attack
     *    (replaying a victim's own answer back at them).
     */
    fun pskProof(nonce: String, roomId: String, psk: String, responder: String): String {
        val key = runCatching { Base64.decode(psk, Base64.NO_WRAP) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: psk.toByteArray()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(("$nonce|$roomId|$responder").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun reset() {
        groupKey = null
        roomCreator = ""
        joinOrder.clear()
        _members.value = emptySet()
    }
}
