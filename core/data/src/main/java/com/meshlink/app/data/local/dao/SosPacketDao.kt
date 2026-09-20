package com.meshlink.app.data.local.dao

import androidx.room.*
import com.meshlink.app.data.local.entity.SosPacketEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for structured SOS packet storage.
 * Inspired by RESCUE-MESH's SosPacketDao.
 */
@Dao
interface SosPacketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packet: SosPacketEntity)

    @Query("SELECT * FROM sos_packets ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SosPacketEntity>>

    @Query("SELECT * FROM sos_packets ORDER BY timestamp DESC")
    suspend fun getAllPacketsBlocking(): List<SosPacketEntity>

    @Query("SELECT * FROM sos_packets WHERE emergencyType = :type ORDER BY timestamp DESC")
    fun getByEmergencyType(type: String): Flow<List<SosPacketEntity>>

    @Query("SELECT * FROM sos_packets WHERE senderId = :senderId ORDER BY timestamp DESC")
    fun getBySender(senderId: String): Flow<List<SosPacketEntity>>

    @Query("UPDATE sos_packets SET status = :status WHERE uuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String)

    @Query("DELETE FROM sos_packets WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("SELECT COUNT(*) FROM sos_packets WHERE status = 'PENDING' OR status = 'RELAYED'")
    fun getActiveCount(): Flow<Int>
}
