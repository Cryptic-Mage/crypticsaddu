package com.helucryptic.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomOrPeerId: String,
    val sender: String,
    val ciphertext: String,
    val plaintextCache: String?,
    val timestamp: Long,
    val status: String
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomOrPeerId = :id ORDER BY timestamp ASC")
    fun observe(id: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(msg: MessageEntity)

    @Query("UPDATE messages SET plaintextCache = NULL WHERE roomOrPeerId = :id")
    suspend fun clearPlaintextCache(id: String)

    /** Flip a sent message to "delivered" when its peer acks it. */
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)
}
