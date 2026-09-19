package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshlink.app.data.local.entity.PendingMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingMessageEntity)

    @Query("SELECT * FROM pending_messages WHERE targetDeviceId = :targetDeviceId AND expiresAt > :now ORDER BY priority DESC, enqueuedAt ASC")
    suspend fun getPendingFor(targetDeviceId: String, now: Long): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE expiresAt > :now")
    suspend fun getAllPending(now: Long): List<PendingMessageEntity>

    @Query("SELECT COUNT(*) FROM pending_messages")
    fun getPendingCountFlow(): Flow<Int>

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_messages WHERE expiresAt <= :beforeEpochMillis")
    suspend fun deleteExpired(beforeEpochMillis: Long)
}
