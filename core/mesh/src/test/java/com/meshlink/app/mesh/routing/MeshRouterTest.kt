package com.meshlink.app.mesh.routing

import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import android.content.Context
import android.app.NotificationManager

class MeshRouterTest {

    private lateinit var meshRouter: MeshRouter
    private val routingTable: RoutingTable = RoutingTable("DEVICE_A")
    private val seenMessageCache: SeenMessageCache = SeenMessageCache()
    private val encryptionService: EncryptionService = mockk()
    private val eciesService: EciesService = mockk()
    private val sessionKeyStore: SessionKeyStore = mockk()
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val pendingMessageRepository: PendingMessageRepository = mockk(relaxed = true)
    private val userProfileManager: UserProfileManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val notificationManager: NotificationManager = mockk(relaxed = true)

    private val myDeviceId = "DEVICE_A"

    @Before
    fun setup() {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        
        meshRouter = MeshRouter(
            context = context,
            myDeviceId = myDeviceId,
            routingTable = routingTable,
            seenMessageCache = seenMessageCache,
            encryptionService = encryptionService,
            eciesService = eciesService,
            sessionKeyStore = sessionKeyStore,
            messageRepository = messageRepository,
            deviceRepository = deviceRepository,
            pendingMessageRepository = pendingMessageRepository,
            userProfileManager = userProfileManager
        )
    }

    @Test
    fun `route drops packet if TTL is exceeded`() = runTest {
        val packet = MeshPacket(
            senderId = "DEVICE_B",
            receiverId = "DEVICE_C",
            content = "Hello",
            timestamp = 1000L,
            hopCount = 7, // TTL limit reached
            maxHops = 7
        )

        val result = meshRouter.route("endpoint_b", packet, emptyMap())
        assertEquals("Packet should be dropped due to TTL", RoutingResult.Drop, result)
    }

    @Test
    fun `route drops duplicate packets using seenMessageCache`() = runTest {
        val packet = MeshPacket(
            senderId = "DEVICE_B",
            receiverId = "DEVICE_C",
            content = "Hello",
            timestamp = 1000L,
            messageId = "MSG_123"
        )

        // First route should process it
        val result1 = meshRouter.route("endpoint_b", packet, emptyMap())
        assertTrue("First packet should be processed", result1 is RoutingResult.Processed)

        // Second route with same messageId should be dropped
        val result2 = meshRouter.route("endpoint_b", packet, emptyMap())
        assertEquals("Duplicate packet should be dropped", RoutingResult.Drop, result2)
    }

    @Test
    fun `route generates ACK when receiving SOS from another peer`() = runTest {
        val sosPacket = MeshPacket(
            senderId = "DEVICE_B",
            receiverId = MeshPacket.BROADCAST_DEST,
            content = "SOS EMERGENCY",
            timestamp = 1000L,
            type = MeshPacket.PacketType.SOS,
            messageId = "SOS_EMERGENCY_123",
            originId = "DEVICE_B",
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount = 0,
            maxHops = 10,
            priority = 10
        )

        val connectedPeers = mapOf("ep_b" to "DEVICE_B")
        val result = meshRouter.route("ep_b", sosPacket, connectedPeers)

        assertTrue("SOS packet should be processed", result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed

        // Verify that an ACK forward target was generated back to DEVICE_B
        val ackTarget = processed.forwardTargets.firstOrNull { it.packet.type == MeshPacket.PacketType.ACK }
        org.junit.Assert.assertNotNull("Peer should generate ACK for incoming SOS", ackTarget)
        assertEquals("ACK content should be SOS messageId", "SOS_EMERGENCY_123", ackTarget!!.packet.content)
        assertEquals("ACK receiverId should be origin peer", "DEVICE_B", ackTarget.packet.receiverId)
        assertEquals("ACK priority should be emergency high", 10, ackTarget.packet.priority)
    }

    @Test
    fun `route handles incoming ACK by updating message repository status to 2`() = runTest {
        val ackPacket = MeshPacket(
            senderId = "DEVICE_B",
            receiverId = myDeviceId,
            content = "SOS_ORIGINAL_ID_456",
            timestamp = 1000L,
            type = MeshPacket.PacketType.ACK,
            messageId = "ACK_789",
            originId = "DEVICE_B",
            finalDestId = myDeviceId
        )

        val result = meshRouter.route("ep_b", ackPacket, mapOf("ep_b" to "DEVICE_B"))

        assertTrue(result is RoutingResult.Processed)
        io.mockk.coVerify { messageRepository.updateMessageStatus("SOS_ORIGINAL_ID_456", 2) }
    }

    @Test
    fun `buildSos inserts message with status 1 and enqueues to pending store-and-forward queue`() = runTest {
        val content = """{"uuid":"SOS_UUID_TEST","message":"Need medical assistance"}"""
        val result = meshRouter.buildSos(content, emptyMap())

        assertTrue(result is RoutingResult.Processed)
        io.mockk.coVerify {
            messageRepository.insertMessage(match { it.id == "SOS_UUID_TEST" && it.status == 1 && it.isSos })
            pendingMessageRepository.enqueue(match { it.packetJson.contains("SOS_UUID_TEST") })
        }
    }

    @Test
    fun `route incoming SOS extracts GPS coordinates from JSON content and updates device location`() = runTest {
        val sosPacket = MeshPacket(
            senderId = "DEVICE_B",
            receiverId = "*",
            content = """{"emergencyType":"MEDICAL","severity":5,"lat":12.9716,"lon":77.5946}""",
            timestamp = 1000L,
            type = MeshPacket.PacketType.SOS,
            messageId = "SOS_LOC_123",
            originId = "DEVICE_B",
            finalDestId = MeshPacket.BROADCAST_DEST
        )

        val result = meshRouter.route("ep_b", sosPacket, mapOf("ep_b" to "DEVICE_B"))
        assertTrue(result is RoutingResult.Processed)
        io.mockk.coVerify { deviceRepository.updateLocation("DEVICE_B", 12.9716, 77.5946) }
    }
}
