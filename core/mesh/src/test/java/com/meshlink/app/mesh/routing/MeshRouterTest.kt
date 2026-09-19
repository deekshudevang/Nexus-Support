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
}
