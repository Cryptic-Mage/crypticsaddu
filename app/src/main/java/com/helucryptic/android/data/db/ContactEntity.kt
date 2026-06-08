package com.helucryptic.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val username: String,
    val ed25519Pub: String,
    val x25519Pub: String,
    val fingerprint: String,
    val verified: Boolean,
    val addedAt: Long,
    val keyChanged: Boolean = false
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY username ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE username = :u LIMIT 1")
    suspend fun get(u: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ContactEntity)

    @Query("UPDATE contacts SET verified = 1 WHERE username = :u")
    suspend fun markVerified(u: String)
}
