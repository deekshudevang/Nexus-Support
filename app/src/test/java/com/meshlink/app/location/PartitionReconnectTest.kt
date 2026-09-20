package com.meshlink.app.location

import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.local.dao.LocationSyncQueueDao
import com.meshlink.app.data.local.dao.ProcessedEventDao
import com.meshlink.app.data.local.entity.LocationEventEntity
import com.meshlink.app.data.local.entity.ProcessedEventEntity
import com.meshlink.app.domain.model.VectorClock
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.crypto.CryptoManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import dagger.Lazy

/**
 * Regression tests for the CRDT partition+reconnect bug.
 *
 * Scenario: Node A and Node B are partitioned. Each generates location events
 * independently. On reconnect, both nodes must converge — neither side loses events.
 *
 * Bug fixed:
 *  1. getVectorClock() used contiguous-sequence math that reported a lower clock
 *     than reality when events arrived out-of-order during partition.
 *  2. handleVectorClockRequest() did not reply with its own clock, so only one
 *     direction of the partition was synced.
 */
class PartitionReconnectTest {

    private val locationEventDao = mockk<LocationEventDao>(relaxed = true)
    private val locationSyncQueueDao = mockk<LocationSyncQueueDao>(relaxed = true)
    private val processedEventDao = mockk<ProcessedEventDao>(relaxed = true)
    private val meshRouter = mockk<MeshRouter>(relaxed = true)
    private val deviceRepository = mockk<DeviceRepository>(relaxed = true)
    private val cryptoManager = mockk<CryptoManager>(relaxed = true)
    private val nearbyRepository = mockk<NearbyRepository>(relaxed = true)
    private val nearbyLazy = mockk<dagger.Lazy<NearbyRepository>>()

    private val localDeviceId = "node-A"
    private lateinit var syncManager: LocationSyncManagerImpl

    @Before
    fun setup() {
        every { nearbyLazy.get() } returns nearbyRepository
        every { cryptoManager.verify(any(), any(), any()) } returns true
        coEvery { processedEventDao.isProcessed(any()) } returns false

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
    }

    /**
     * After partition: Node A has seq 1,2,3. Node B has seq 1,2.
     * When B reconnects and sends its VectorClock {A:2}, A must send event seq=3 to B.
     */
    @Test
    fun `partition reconnect - A sends delta to B for missing sequence`() = runTest {
        // Node A's DB: has sequences 1, 2, 3 for peerA
        coEvery { locationEventDao.getVectorClock() } returns listOf(
            VectorClock("node-A", 3)
        )
        coEvery { locationEventDao.getEventsAfterSequence("node-A", 2) } returns listOf(
            fakeEvent("node-A", seq = 3)
        )
        every { nearbyRepository.getConnectedPeers() } returns mapOf("ep-B" to "node-B")
        every { meshRouter.buildLocationSync(any(), any()) } returns
            com.meshlink.app.mesh.routing.RoutingResult.Drop

        // Node B connects and sends its clock: {A:2} — it's missing seq 3
        val bClock = JSONObject().apply {
            put("type", "vector_clock")
            put("sourceDeviceId", "node-B")
            put("clocks", JSONArray().apply {
                put(JSONObject().apply {
                    put("peerId", "node-A")
                    put("sequenceNumber", 2)
                })
            })
        }.toString()

        syncManager.processIncomingSync(bClock, "node-B")

        // Verify A fetched and attempted to send the missing event (seq 3) to B
        coVerify { locationEventDao.getEventsAfterSequence("node-A", 2) }
        verify { meshRouter.buildLocationSync(any(), match { it.values.contains("node-B") }) }
    }

    /**
     * After partition: Node B has events A generated during partition (seq 4,5).
     * When A reconnects, B sends delta. A must accept them without dropping.
     */
    @Test
    fun `partition reconnect - out-of-order events accepted, not dropped`() = runTest {
        // Simulate A receiving events with seq=4,5 while A only knows up to seq=3
        coEvery { locationEventDao.getHighestSequenceNumber("node-A") } returns 3

        val eventsPayload = JSONArray().apply {
            put(buildEventJson("node-A", seq = 4, eventId = "evt-4"))
            put(buildEventJson("node-A", seq = 5, eventId = "evt-5"))
        }.toString()

        syncManager.processIncomingSync(eventsPayload, "node-B")

        // Both events must be inserted — not dropped for being "out of order"
        coVerify {
            locationEventDao.insertEvents(match { events ->
                events.any { it.sequenceNumber == 4 } && events.any { it.sequenceNumber == 5 }
            })
        }
    }

    /**
     * Proves that when B sends its VectorClock, A replies with its own clock
     * so B can compute A's missing events too (bidirectional handshake).
     */
    @Test
    fun `partition reconnect - bidirectional A replies with own clock after receiving B clock`() = runTest {
        coEvery { locationEventDao.getVectorClock() } returns listOf(VectorClock("node-A", 5))
        coEvery { locationEventDao.getEventsAfterSequence(any(), any()) } returns emptyList()
        every { nearbyRepository.getConnectedPeers() } returns mapOf("ep-B" to "node-B")
        every { meshRouter.buildLocationSync(any(), any()) } returns
            com.meshlink.app.mesh.routing.RoutingResult.Drop

        val bClock = JSONObject().apply {
            put("type", "vector_clock")
            put("sourceDeviceId", "node-B")
            put("clocks", JSONArray().apply {
                put(JSONObject().apply {
                    put("peerId", "node-A")
                    put("sequenceNumber", 3) // B thinks A is at 3, but A is at 5
                })
            })
        }.toString()

        syncManager.processIncomingSync(bClock, "node-B")

        // A must send back a vector_clock reply to B (containing "node-A": 5)
        // This triggers B to compute that it's missing A's events 4 and 5
        verify(atLeast = 2) {
            meshRouter.buildLocationSync(
                match { it.contains("\"type\":\"vector_clock\"") },
                match { it.values.contains("node-B") }
            )
        }
    }


    private fun fakeEvent(peerId: String, seq: Int) = LocationEventEntity(
        eventId = "evt-$seq",
        peerId = peerId,
        latitude = 12.9,
        longitude = 77.6,
        accuracy = 5f,
        timestamp = System.currentTimeMillis(),
        sequenceNumber = seq,
        signature = "sig",
        publicKey = "pk",
        syncStatus = 0
    )

    private fun buildEventJson(peerId: String, seq: Int, eventId: String): JSONObject =
        JSONObject().apply {
            put("eventId", eventId)
            put("peerId", peerId)
            put("sequenceNumber", seq)
            put("latitude", 12.9)
            put("longitude", 77.6)
            put("accuracy", 5.0)
            put("timestamp", System.currentTimeMillis())
            put("signature", "valid-sig")
            put("publicKey", "pk")
            put("ttl", 3)
        }
}
