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

    /** Returns true if this client was promoted to creator (must re-broadcast group key). */
    fun removeMember(username: String, myUsername: String): Boolean {
        _members.value = _members.value - username
        joinOrder.remove(username)
        if (username == roomCreator) {
            val next = joinOrder.firstOrNull() ?: return false
            roomCreator = next
            if (next == myUsername) {
                groupKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
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

    /** HMAC-SHA256(key=psk, msg=nonce||roomId) — hex string. */
    fun pskProof(nonce: String, roomId: String, psk: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(psk.toByteArray(), "HmacSHA256"))
        return mac.doFinal((nonce + roomId).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun reset() {
        groupKey = null
        roomCreator = ""
        joinOrder.clear()
        _members.value = emptySet()
    }
}
