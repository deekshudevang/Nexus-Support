package com.meshlink.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meshlink.app.crypto.identity.KeyProvider
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.domain.model.SosPacketData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPair

@RunWith(RobolectricTestRunner::class)
class SosRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var sosPacketDao: SosPacketDao
    private lateinit var repository: SosRepositoryImpl

    private class FakeKeyProvider(override val deviceId: String = "local-node-1") : KeyProvider {
        override val keyPair: KeyPair get() = throw UnsupportedOperationException()
        override val publicKeyBytes: ByteArray get() = ByteArray(0)
        override fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray = ByteArray(0)
        override fun sign(data: ByteArray): String = ""
        override fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = true
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sosPacketDao = db.sosPacketDao()
        repository = SosRepositoryImpl(sosPacketDao, FakeKeyProvider("local-node-1"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun enqueueSos_newPacket_insertsSuccessfully() = runTest {
        val packet = SosPacketData(
            uuid = "sos-101",
            senderId = "local-node-1",
            emergencyType = SosPacketData.EmergencyType.MEDICAL,
            severity = 5,
            message = "Need immediate evacuation"
        )

        val result = repository.enqueueSos(packet)
        assertTrue(result)

        val retrieved = repository.getSosByUuid("sos-101")
        assertNotNull(retrieved)
        assertEquals("sos-101", retrieved?.uuid)
        assertEquals("Need immediate evacuation", retrieved?.message)
        assertEquals(SosPacketData.EmergencyType.MEDICAL, retrieved?.emergencyType)
    }

    @Test
    fun enqueueSos_duplicateUuid_suppressesInsertion() = runTest {
        val packet = SosPacketData(
            uuid = "sos-duplicate",
            senderId = "local-node-1",
            emergencyType = SosPacketData.EmergencyType.MEDICAL,
            severity = 5
        )

        val first = repository.enqueueSos(packet)
        assertTrue(first)

        val second = repository.enqueueSos(packet)
        assertFalse(second)

        val all = repository.getAllSosPackets().first()
        assertEquals(1, all.size)
    }

    @Test
    fun enqueueSos_atCapacity_prunesOldestBeforeInsertion() = runTest {
        // Enqueue MAX_SOS_CAPACITY items
        for (i in 1..SosRepositoryImpl.MAX_SOS_CAPACITY) {
            val p = SosPacketData(
                uuid = "sos-$i",
                senderId = "local-node-1",
                timestamp = i.toLong(),
                emergencyType = SosPacketData.EmergencyType.OTHER
            )
            repository.enqueueSos(p)
        }

        val totalBefore = sosPacketDao.getTotalCount()
        assertEquals(SosRepositoryImpl.MAX_SOS_CAPACITY, totalBefore)

        // Enqueue one more item
        val overflow = SosPacketData(
            uuid = "sos-overflow",
            senderId = "local-node-1",
            timestamp = 99999L,
            emergencyType = SosPacketData.EmergencyType.SECURITY
        )
        val enqueued = repository.enqueueSos(overflow)
        assertTrue(enqueued)

        // Count stays capped at MAX_SOS_CAPACITY
        val totalAfter = sosPacketDao.getTotalCount()
        assertEquals(SosRepositoryImpl.MAX_SOS_CAPACITY, totalAfter)

        // Oldest item ("sos-1") was pruned
        assertNull(repository.getSosByUuid("sos-1"))
        // New item exists
        assertNotNull(repository.getSosByUuid("sos-overflow"))
    }

    @Test
    fun acknowledgeSos_existingPacket_updatesStatus() = runTest {
        val packet = SosPacketData(
            uuid = "sos-ack",
            senderId = "local-node-1",
            emergencyType = SosPacketData.EmergencyType.LOST,
            status = SosPacketData.SosStatus.SENT
        )
        repository.enqueueSos(packet)

        val success = repository.acknowledgeSos("sos-ack")
        assertTrue(success)

        val updated = repository.getSosByUuid("sos-ack")
        assertEquals(SosPacketData.SosStatus.ACKNOWLEDGED, updated?.status)
    }

    @Test
    fun acknowledgeSos_nonExistentPacket_returnsFalse() = runTest {
        val success = repository.acknowledgeSos("non-existent")
        assertFalse(success)
    }

    @Test
    fun getActiveLocalSos_returnsMappedDomainModel() = runTest {
        val packet = SosPacketData(
            uuid = "active-sos",
            senderId = "local-node-1",
            emergencyType = SosPacketData.EmergencyType.FIRE,
            severity = 4,
            latitude = 37.7749,
            longitude = -122.4194,
            timestamp = 5000L,
            status = SosPacketData.SosStatus.PENDING
        )
        repository.enqueueSos(packet)

        val active = repository.getActiveLocalSos()
        assertNotNull(active)
        assertEquals("active-sos", active?.uuid)
        assertEquals(SosPacketData.EmergencyType.FIRE, active?.emergencyType)
        assertEquals(SosPacketData.SosStatus.PENDING, active?.status)
    }

    @Test
    fun cancelSos_updatesStatusToCancelled() = runTest {
        val packet = SosPacketData(
            uuid = "sos-cancel-1",
            senderId = "local-node-1",
            status = SosPacketData.SosStatus.SENT
        )
        repository.enqueueSos(packet)

        repository.cancelSos("sos-cancel-1")
        val updated = repository.getSosByUuid("sos-cancel-1")
        assertEquals(SosPacketData.SosStatus.CANCELLED, updated?.status)
    }

    @Test
    fun deleteOlderThan_removesExpiredPackets() = runTest {
        val oldPacket = SosPacketData(
            uuid = "sos-old",
            senderId = "local-node-1",
            timestamp = 1000L
        )
        val newPacket = SosPacketData(
            uuid = "sos-new",
            senderId = "local-node-1",
            timestamp = 9000L
        )
        repository.enqueueSos(oldPacket)
        repository.enqueueSos(newPacket)

        repository.deleteOlderThan(5000L)

        assertNull(repository.getSosByUuid("sos-old"))
        assertNotNull(repository.getSosByUuid("sos-new"))
    }
}
