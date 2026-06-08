package com.helucryptic.android.data.repository

import com.helucryptic.android.crypto.Fingerprint
import com.helucryptic.android.data.db.ContactDao
import com.helucryptic.android.data.db.ContactEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(private val dao: ContactDao) {

    fun observeAll(): Flow<List<ContactEntity>> = dao.observeAll()

    suspend fun upsertFromHello(username: String, ed25519Pub: String, x25519Pub: String) {
        val fingerprint = Fingerprint.compute(x25519Pub)
        val existing = dao.get(username)
        val keyChanged = existing != null && existing.ed25519Pub.isNotEmpty() && existing.ed25519Pub != ed25519Pub
        dao.upsert(
            ContactEntity(
                username    = username,
                ed25519Pub  = ed25519Pub,
                x25519Pub   = x25519Pub,
                fingerprint = fingerprint,
                verified    = if (keyChanged) false else (existing?.verified ?: false),
                addedAt     = existing?.addedAt ?: System.currentTimeMillis(),
                keyChanged  = keyChanged || (existing?.keyChanged ?: false)
            )
        )
    }

    suspend fun addContact(username: String) {
        val existing = dao.get(username)
        if (existing == null) {
            dao.upsert(
                ContactEntity(
                    username    = username,
                    ed25519Pub  = "",
                    x25519Pub   = "",
                    fingerprint = "",
                    verified    = false,
                    addedAt     = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun markVerified(username: String) = dao.markVerified(username)

    suspend fun hasKeyChanged(username: String, newEd25519Pub: String): Boolean {
        val existing = dao.get(username) ?: return false
        return existing.ed25519Pub.isNotEmpty() && existing.ed25519Pub != newEd25519Pub
    }

    suspend fun get(username: String): ContactEntity? = dao.get(username)
}
