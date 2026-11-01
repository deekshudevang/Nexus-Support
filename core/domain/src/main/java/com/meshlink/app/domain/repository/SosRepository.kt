package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.SosPacketData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for durable SOS emergency packet persistence,
 * state management, queue bounded retention, and process-recreation recovery.
 */
interface SosRepository {

    /** Returns a flow of all persisted SOS packets ordered by timestamp descending. */
    fun getAllSosPackets(): Flow<List<SosPacketData>>

    /** Retrieves an SOS packet by its idempotent UUID. */
    suspend fun getSosByUuid(uuid: String): SosPacketData?

    /** Retrieves the active outgoing SOS packet created by the local device, if any. */
    suspend fun getActiveLocalSos(): SosPacketData?

    /** Observable flow of the active outgoing local SOS packet. */
    fun getActiveLocalSosFlow(): Flow<SosPacketData?>

    /**
     * Enqueues an outgoing SOS packet into durable storage.
     * Enforces queue capacity limits and idempotency deduplication.
     * @return true if enqueued; false if duplicate detected.
     */
    suspend fun enqueueSos(sos: SosPacketData): Boolean

    /** Updates the delivery lifecycle status of an SOS packet. */
    suspend fun updateSosStatus(uuid: String, status: SosPacketData.SosStatus)

    /** Marks an active SOS packet as acknowledged by peer / mesh gateway. */
    suspend fun acknowledgeSos(uuid: String): Boolean

    /** Cancels an active local SOS broadcast. */
    suspend fun cancelSos(uuid: String)

    /** Deletes expired SOS packets older than [cutoffTimestamp]. */
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
