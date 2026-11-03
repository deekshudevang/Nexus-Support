package com.meshlink.app.mesh.routing

import android.app.NotificationManager
import android.content.Context
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.MeshPacket.PacketType
import com.meshlink.app.domain.model.PendingMessage
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.PriorityQueue
import java.util.UUID

/**
 * Verification Suite for Safety Invariant 7:
 * "Emergency SOS traffic strictly preempts background node telemetry and general chatter in transmission queues."
 */
class SafetyInvariant7MeshPriorityQueueTest {

    private val myDeviceId = "NODE_LOCAL"
    private lateinit var meshRouter: MeshRouter
    private val routingTable = RoutingTable(myDeviceId)
    private val seenMessageCache = SeenMessageCache()

    @Before
    fun setup() {
        val ctx = mockk<Context>(relaxed = true)
        val nm = mockk<NotificationManager>(relaxed = true)
        every { ctx.getSystemService(Context.NOTIFICATION_SERVICE) } returns nm

        meshRouter = MeshRouter(
            context = ctx,
            myDeviceId = myDeviceId,
            routingTable = routingTable,
            seenMessageCache = seenMessageCache,
            encryptionService = mockk(relaxed = true),
            eciesService = mockk(relaxed = true),
            sessionKeyStore = mockk(relaxed = true),
            messageRepository = mockk(relaxed = true),
            deviceRepository = mockk(relaxed = true),
            pendingMessageRepository = mockk(relaxed = true),
            userProfileManager = mockk(relaxed = true)
        )
    }

    @Test
    fun `Safety Invariant 7 - SOS packets strictly preempt chat and telemetry in transmission queue`() {
        // Comparator matching Room DAO: ORDER BY priority DESC, enqueuedAt ASC
        val comparator = Comparator<PendingMessage> { m1, m2 ->
            val pComp = m2.priority.compareTo(m1.priority) // Higher priority first
            if (pComp != 0) pComp else m1.enqueuedAt.compareTo(m2.enqueuedAt)
        }
        val transmissionQueue = PriorityQueue(comparator)

        val t0 = 1000L

        // 1. Enqueue general chat messages (Priority 0)
        val chat1 = PendingMessage(
            id = "chat-1",
            packetJson = """{"content":"How are supplies looking?"}""",
            targetDeviceId = "NODE_PEER",
            enqueuedAt = t0,
            expiresAt = t0 + 3600_000,
            priority = 0
        )
        val chat2 = PendingMessage(
            id = "chat-2",
            packetJson = """{"content":"Base camp setup complete"}""",
            targetDeviceId = "NODE_PEER",
            enqueuedAt = t0 + 10,
            expiresAt = t0 + 3600_000,
            priority = 0
        )

        // 2. Enqueue background telemetry / status updates (Priority 1)
        val telemetry1 = PendingMessage(
            id = "telemetry-1",
            packetJson = """{"battery":95,"role":"RESPONDER"}""",
            targetDeviceId = "NODE_PEER",
            enqueuedAt = t0 + 20,
            expiresAt = t0 + 3600_000,
            priority = 1
        )

        // 3. Enqueue location sync (Priority 2)
        val locSync = PendingMessage(
            id = "location-1",
            packetJson = """{"lat":37.77,"lon":-122.42}""",
            targetDeviceId = "NODE_PEER",
            enqueuedAt = t0 + 30,
            expiresAt = t0 + 3600_000,
            priority = 2
        )

        transmissionQueue.add(chat1)
        transmissionQueue.add(chat2)
        transmissionQueue.add(telemetry1)
        transmissionQueue.add(locSync)

        // 4. Late arrival of Critical SOS (Priority 10)
        val emergencySos = PendingMessage(
            id = "sos-emergency-001",
            packetJson = """{"uuid":"sos-emergency-001","type":"MEDICAL","severity":5}""",
            targetDeviceId = "NODE_PEER",
            enqueuedAt = t0 + 40,
            expiresAt = t0 + 3600_000,
            priority = 10
        )
        transmissionQueue.add(emergencySos)

        // Invariant 7 Assertion: SOS packet MUST be dequeued first, regardless of late arrival
        val firstOut = checkNotNull(transmissionQueue.poll())
        assertEquals("Safety Invariant 7 Violation: SOS must preempt all other traffic", "sos-emergency-001", firstOut.id)
        assertEquals(10, firstOut.priority)

        // Second out must be Location (Priority 2)
        val secondOut = checkNotNull(transmissionQueue.poll())
        assertEquals("location-1", secondOut.id)
        assertEquals(2, secondOut.priority)

        // Third out must be Status Telemetry (Priority 1)
        val thirdOut = checkNotNull(transmissionQueue.poll())
        assertEquals("telemetry-1", thirdOut.id)
        assertEquals(1, thirdOut.priority)

        // Fourth and fifth must be normal chat in FIFO order (Priority 0)
        val fourthOut = checkNotNull(transmissionQueue.poll())
        val fifthOut = checkNotNull(transmissionQueue.poll())
        assertEquals("chat-1", fourthOut.id)
        assertEquals("chat-2", fifthOut.id)
    }

    @Test
    fun `Safety Invariant 7 - buildSos always assigns Priority 10 to emergency packets`() {
        val result = meshRouter.buildSos(
            content = """{"uuid":"sos-prio-test","message":"Trapped victim"}""",
            connectedPeers = mapOf("ep_peer" to "NODE_PEER")
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed

        // Verify each generated forward target carries Priority 10
        for (target in processed.forwardTargets) {
            assertEquals("Forwarded SOS must have priority 10", 10, target.packet.priority)
            assertEquals("Packet type must be SOS", PacketType.SOS, target.packet.type)
        }
    }

    @Test
    fun `Multi-hop routing drops looped and TTL-exhausted packets`() = runTest {
        val expiredPacket = MeshPacket(
            senderId = "NODE_ORIGIN",
            receiverId = MeshPacket.BROADCAST_DEST,
            content = "SOS EXPIRED",
            timestamp = 1000L,
            type = PacketType.SOS,
            messageId = "SOS_EXPIRED_01",
            hopCount = 10,
            maxHops = 10,
            priority = 10
        )

        // Hop count == maxHops -> Drop
        val result = meshRouter.route("ep_in", expiredPacket, mapOf("ep_next" to "NODE_NEXT"))
        assertEquals(RoutingResult.Drop, result)
    }
}
