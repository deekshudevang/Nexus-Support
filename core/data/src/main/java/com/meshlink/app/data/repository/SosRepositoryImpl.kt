package com.meshlink.app.data.repository

import com.meshlink.app.crypto.identity.KeyProvider
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.data.local.mapper.toDomain
import com.meshlink.app.data.local.mapper.toEntity
import com.meshlink.app.domain.model.SosPacketData
import com.meshlink.app.domain.repository.SosRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SosRepositoryImpl @Inject constructor(
    private val sosPacketDao: SosPacketDao,
    private val keyProvider: KeyProvider
) : SosRepository {

    private val localDeviceId: String
        get() = keyProvider.deviceId

    override fun getAllSosPackets(): Flow<List<SosPacketData>> =
        sosPacketDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getSosByUuid(uuid: String): SosPacketData? =
        sosPacketDao.getByUuid(uuid)?.toDomain()

    override suspend fun getActiveLocalSos(): SosPacketData? =
        sosPacketDao.getActiveLocal(localDeviceId)?.toDomain()

    override fun getActiveLocalSosFlow(): Flow<SosPacketData?> =
        sosPacketDao.getActiveLocalFlow(localDeviceId).map { it?.toDomain() }

    override suspend fun enqueueSos(sos: SosPacketData): Boolean {
        // Idempotency check: If an entry with this UUID already exists, suppress duplicate insertion
        val existing = sosPacketDao.getByUuid(sos.uuid)
        if (existing != null) {
            Timber.d("SosRepositoryImpl: duplicate SOS detected for uuid=${sos.uuid}; suppressing duplicate insertion")
            return false
        }

        // Bounded queue enforcement: enforce max capacity
        val count = sosPacketDao.getTotalCount()
        if (count >= MAX_SOS_CAPACITY) {
            val overflow = (count - MAX_SOS_CAPACITY) + 1
            Timber.w("SosRepositoryImpl: queue capacity ($MAX_SOS_CAPACITY) reached; pruning $overflow oldest records")
            sosPacketDao.deleteOldest(overflow)
        }

        sosPacketDao.insert(sos.toEntity())
        return true
    }

    override suspend fun updateSosStatus(uuid: String, status: SosPacketData.SosStatus) {
        sosPacketDao.updateStatus(uuid, status.name)
    }

    override suspend fun acknowledgeSos(uuid: String): Boolean {
        val existing = sosPacketDao.getByUuid(uuid) ?: return false
        sosPacketDao.updateStatus(uuid, SosPacketData.SosStatus.ACKNOWLEDGED.name)
        return true
    }

    override suspend fun cancelSos(uuid: String) {
        sosPacketDao.updateStatus(uuid, SosPacketData.SosStatus.CANCELLED.name)
    }

    override suspend fun deleteOlderThan(cutoffTimestamp: Long) {
        sosPacketDao.deleteOlderThan(cutoffTimestamp)
    }

    companion object {
        const val MAX_SOS_CAPACITY = 50
    }
}
