package com.helucryptic.android.data.repository

import com.helucryptic.android.data.db.RoomDao
import com.helucryptic.android.data.db.RoomEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepository @Inject constructor(private val dao: RoomDao) {

    fun observeAll(): Flow<List<RoomEntity>> = dao.observeAll()

    suspend fun getRoom(roomCode: String): RoomEntity? = dao.getRoom(roomCode)

    suspend fun upsert(roomCode: String, psk: String, creatorUsername: String) {
        dao.upsert(
            RoomEntity(
                roomCode        = roomCode,
                psk             = psk,
                creatorUsername = creatorUsername,
                joinedAt        = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(roomCode: String) = dao.delete(roomCode)
}
