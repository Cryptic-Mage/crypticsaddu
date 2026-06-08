package com.helucryptic.android.ui.settings

import androidx.lifecycle.ViewModel
import com.helucryptic.android.crypto.IdentityKeys
import com.helucryptic.android.crypto.IdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class KeyBackupViewModel @Inject constructor(
    val identityStore: IdentityStore
) : ViewModel() {

    fun exportJson(): String? {
        val username = identityStore.username ?: return null
        val xPriv = identityStore.x25519Priv ?: return null
        val xPub = identityStore.x25519Pub ?: return null
        val edPriv = identityStore.ed25519Priv ?: return null
        val edPub = identityStore.ed25519Pub ?: return null
        
        return JSONObject().apply {
            put("username", username)
            put("x25519_private", xPriv)
            put("x25519_public", xPub)
            put("ed25519_private", edPriv)
            put("ed25519_public", edPub)
        }.toString()
    }

    fun validateAndImportJson(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val username = obj.getString("username")
            val xPriv = obj.getString("x25519_private")
            val xPub = obj.getString("x25519_public")
            val edPriv = obj.getString("ed25519_private")
            val edPub = obj.getString("ed25519_public")
            
            if (username.isBlank() || xPriv.isBlank() || xPub.isBlank() || edPriv.isBlank() || edPub.isBlank()) {
                return false
            }
            
            identityStore.saveIdentity(
                username = username,
                keys = IdentityKeys(
                    x25519Priv = xPriv,
                    x25519Pub = xPub,
                    ed25519Priv = edPriv,
                    ed25519Pub = edPub
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
