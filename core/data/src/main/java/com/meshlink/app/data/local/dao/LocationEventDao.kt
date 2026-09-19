package com.meshlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meshlink.app.data.local.entity.LocationEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: LocationEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<LocationEventEntity>)

    @Query("SELECT * FROM location_events WHERE peerId = :peerId ORDER BY sequenceNumber DESC LIMIT 1")
    suspend fun getLatestLocationForPeer(peerId: String): LocationEventEntity?

    @Query("SELECT * FROM location_events ORDER BY timestamp DESC")
    fun getAllLocationEventsFlow(): Flow<List<LocationEventEntity>>

    @Query("SELECT * FROM location_events WHERE syncStatus = 0") // 0 = PENDING
    suspend fun getPendingCloudUploads(): List<LocationEventEntity>

    @Query("UPDATE location_events SET syncStatus = 1 WHERE eventId IN (:eventIds)")
    suspend fun markAsUploaded(eventIds: List<String>)

    @Query("SELECT IFNULL(MAX(sequenceNumber), 0) FROM location_events WHERE peerId = :peerId")
    suspend fun getHighestSequenceNumber(peerId: String): Int

    @Query("""
        SELECT peerId, IFNULL((
            SELECT MAX(t2.sequenceNumber) 
            FROM location_events t2 
            WHERE t2.peerId = t1.peerId 
              AND t2.sequenceNumber = (
                  SELECT COUNT(*) 
                  FROM location_events t3 
                  WHERE t3.peerId = t1.peerId 
                    AND t3.sequenceNumber <= t2.sequenceNumber
              )
        ), 0) as sequenceNumber 
        FROM location_events t1 
        GROUP BY peerId
    """)
    suspend fun getVectorClock(): List<com.meshlink.app.domain.model.VectorClock>

    @Query("SELECT * FROM location_events WHERE peerId = :peerId AND sequenceNumber > :sequenceNumber ORDER BY sequenceNumber ASC")
    suspend fun getEventsAfterSequence(peerId: String, sequenceNumber: Int): List<LocationEventEntity>
}
