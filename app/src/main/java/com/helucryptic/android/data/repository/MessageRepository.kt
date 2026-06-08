package com.helucryptic.android.data.repository

import com.helucryptic.android.data.db.MessageDao
import com.helucryptic.android.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(private val dao: MessageDao) {

    fun observe(id: String): Flow<List<MessageEntity>> = dao.observe(id)

    suspend fun save(
        roomOrPeerId: String,
        sender: String,
        ciphertext: String,
        plaintext: String?,
        status: String = "sent"
    ) {
        dao.upsert(
            MessageEntity(
                id            = UUID.randomUUID().toString(),
                roomOrPeerId  = roomOrPeerId,
                sender        = sender,
                ciphertext    = ciphertext,
                plaintextCache = plaintext,
                timestamp     = System.currentTimeMillis(),
                status        = status
            )
        )
    }

    suspend fun clearCache(id: String) = dao.clearPlaintextCache(id)
}
