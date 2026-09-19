package com.meshlink.app.location

import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.local.dao.LocationSyncQueueDao
import com.meshlink.app.data.local.dao.ProcessedEventDao
import com.meshlink.app.data.local.entity.LocationEventEntity
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.crypto.CryptoManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import dagger.Lazy

class LocationSyncManagerTest {

    private lateinit var syncManager: LocationSyncManagerImpl
    private val locationEventDao: LocationEventDao = mockk(relaxed = true)
    private val locationSyncQueueDao: LocationSyncQueueDao = mockk(relaxed = true)
    private val processedEventDao: ProcessedEventDao = mockk(relaxed = true)
    private val meshRouter: MeshRouter = mockk(relaxed = true)
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val cryptoManager: CryptoManager = mockk(relaxed = true)
    private val nearbyRepository: NearbyRepository = mockk(relaxed = true)
    
    private val localDeviceId = "local-device"

    @Before
    fun setup() {
        val nearbyLazy = mockk<Lazy<NearbyRepository>>()
        every { nearbyLazy.get() } returns nearbyRepository
        
        syncManager = LocationSyncManagerImpl(
            locationEventDao,
            locationSyncQueueDao,
            processedEventDao,
            meshRouter,
            deviceRepository,
            localDeviceId,
            cryptoManager,
            nearbyLazy
        )

        // Default stubs
        every { cryptoManager.verify(any(), any(), any()) } returns true
        every { processedEventDao.isProcessed(any()) } returns false
    }

    @Test
    fun `test out of order delivery doesn't drop events`() = runTest {
        // Mock that highest known is 3
        coEvery { locationEventDao.getHighestSequenceNumber("peerA") } returns 3

        val payload = JSONArray().apply {
            put(JSONObject().apply {
                put("eventId", "event-2")
                put("peerId", "peerA")
                put("latitude", 0.0)
                put("longitude", 0.0)
                put("accuracy", 0.0)
                put("timestamp", 1000L)
                put("sequenceNumber", 2)
                put("signature", "sig")
                put("publicKey", "key")
                put("ttl", 5)
            })
        }.toString()

        syncManager.processIncomingSync(payload, "sender-node")

        // Because we removed the sequence check, it should insert the event!
        coVerify {
            locationEventDao.insertEvents(match { it.size == 1 && it.first().eventId == "event-2" })
        }
    }

    @Test
    fun `test split horizon prevents broadcast loop`() = runTest {
        // Node B sends us an event. We should broadcast it to everyone EXCEPT Node B.
        val senderId = "peerB"
        
        every { nearbyRepository.getConnectedPeers() } returns mapOf(
            "endpoint-b" to "peerB",
            "endpoint-c" to "peerC"
        )

        val payload = JSONArray().apply {
            put(JSONObject().apply {
                put("eventId", "event-1")
                put("peerId", "peerA")
                put("latitude", 0.0)
                put("longitude", 0.0)
                put("accuracy", 0.0)
                put("timestamp", 1000L)
                put("sequenceNumber", 1)
                put("signature", "sig")
                put("publicKey", "key")
                put("ttl", 5)
            })
        }.toString()

        syncManager.processIncomingSync(payload, senderId)

        // It should build a sync payload targeting ONLY peerC
        verify {
            meshRouter.buildLocationSync(any(), match { it.size == 1 && it.containsValue("peerC") })
        }
    }
}
