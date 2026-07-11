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

    /** Save a message. Returns the row id so the caller can track delivery
     *  (the same id is sent on the wire and echoed back in the peer's ack). */
    suspend fun save(
        roomOrPeerId: String,
        sender: String,
        ciphertext: String,
        plaintext: String?,
        status: String = "sent",
        id: String = UUID.randomUUID().toString()
    ): String {
        dao.upsert(
            MessageEntity(
                id            = id,
                roomOrPeerId  = roomOrPeerId,
                sender        = sender,
                ciphertext    = ciphertext,
                plaintextCache = plaintext,
                timestamp     = System.currentTimeMillis(),
                status        = status
            )
        )
        return id
    }

    /** Mark a previously-sent message delivered (peer acknowledged receipt). */
    suspend fun markDelivered(id: String) = dao.setStatus(id, "delivered")

    suspend fun clearCache(id: String) = dao.clearPlaintextCache(id)
}
