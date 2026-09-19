package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshlink.app.data.local.entity.ProcessedEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ProcessedEventEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM processed_events WHERE eventId = :eventId)")
    suspend fun isProcessed(eventId: String): Boolean

    @Query("SELECT COUNT(*) FROM processed_events")
    fun getProcessedCountFlow(): Flow<Int>

    @Query("DELETE FROM processed_events WHERE timestamp < :thresholdMillis")
    suspend fun deleteOldEvents(thresholdMillis: Long)
}
