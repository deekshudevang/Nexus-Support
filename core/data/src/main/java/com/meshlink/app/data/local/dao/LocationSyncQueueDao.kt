package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshlink.app.data.local.entity.LocationSyncQueueEntity

@Dao
interface LocationSyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(syncQueueEntity: LocationSyncQueueEntity)

    @Query("SELECT * FROM location_sync_queue WHERE status = 0")
    suspend fun getPendingSyncs(): List<LocationSyncQueueEntity>

    @Query("UPDATE location_sync_queue SET status = 1 WHERE id IN (:ids)")
    suspend fun markAsSent(ids: List<Long>)

    @Query("DELETE FROM location_sync_queue WHERE status = 1")
    suspend fun clearSentItems()
}
