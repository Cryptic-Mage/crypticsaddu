package com.helucryptic.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val roomCode: String,
    val psk: String,
    val creatorUsername: String,
    val joinedAt: Long
)

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY joinedAt DESC")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE roomCode = :code LIMIT 1")
    suspend fun getRoom(code: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RoomEntity)

    @Query("DELETE FROM rooms WHERE roomCode = :code")
    suspend fun delete(code: String)
}
