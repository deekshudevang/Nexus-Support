package com.meshlink.app.mesh.transport

import android.app.NotificationManager
import android.content.Context
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.SettingsRepository
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.routing.RoutingResult
import com.meshlink.app.mesh.routing.RoutingTable
import com.meshlink.app.mesh.routing.SeenMessageCache
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake implementation of [MeshTransport] for unit testing routing and settings logic.
 */
class FakeMeshTransport(
    override val transportType: TransportType
) : MeshTransport {
    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    val sentPackets = mutableListOf<MeshPacket>()
    var isStarted = false

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }

    override suspend fun send(packet: MeshPacket, targetAddress: String?): Boolean {
        sentPackets.add(packet)
        return true
    }

    fun emitPacket(packet: MeshPacket) {
        _incomingPackets.tryEmit(packet)
    }
}

class MeshTransportRoutingTest {

    private val myDeviceId = "LOCAL_DEVICE"
    private val routingTable = RoutingTable(myDeviceId)
    private val seenMessageCache = SeenMessageCache()
    private val encryptionService: EncryptionService = mockk(relaxed = true)
    private val eciesService: EciesService = mockk(relaxed = true)
    private val sessionKeyStore: SessionKeyStore = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val pendingMessageRepository: PendingMessageRepository = mockk(relaxed = true)
    private val userProfileManager: UserProfileManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private val useNearbyFlow = MutableStateFlow(true)
    private val useMeshtasticFlow = MutableStateFlow(false)

    private lateinit var meshRouter: MeshRouter

    @Before
    fun setUp() {
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { userProfileManager.getDisplayName() } returns "Local Node"
        every { settingsRepository.useNearby } returns useNearbyFlow
        every { settingsRepository.useMeshtastic } returns useMeshtasticFlow

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
            userProfileManager = userProfileManager,
            seenBeaconCache = SeenMessageCache(),
            clock = { 1000L },
            settingsRepository = settingsRepository
        )
    }

    @Test
    fun `buildBroadcast routes only to Nearby when Meshtastic is disabled`() {
        useNearbyFlow.value = true
        useMeshtasticFlow.value = false

        val connectedPeers = mapOf("ep1" to "PEER_1", "ep2" to "PEER_2")
        val result = meshRouter.buildBroadcast("Emergency Alert", connectedPeers)

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertEquals(2, processed.forwardTargets.size)
        assertTrue(processed.forwardTargets.all { it.transportType == TransportType.NEARBY })
    }

    @Test
    fun `buildBroadcast routes only to Meshtastic when Nearby is disabled and Meshtastic is enabled`() {
        useNearbyFlow.value = false
        useMeshtasticFlow.value = true

        val connectedPeers = mapOf("ep1" to "PEER_1", "ep2" to "PEER_2")
        val result = meshRouter.buildBroadcast("Emergency Alert", connectedPeers)

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertEquals(1, processed.forwardTargets.size)
        val target = processed.forwardTargets.first()
        assertEquals(TransportType.MESHTASTIC, target.transportType)
        assertEquals("meshtastic", target.endpointId)
    }

    @Test
    fun `buildBroadcast routes in parallel to both Nearby and Meshtastic when both are enabled`() {
        useNearbyFlow.value = true
        useMeshtasticFlow.value = true

        val connectedPeers = mapOf("ep1" to "PEER_1")
        val result = meshRouter.buildBroadcast("Emergency Alert", connectedPeers)

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertEquals(2, processed.forwardTargets.size)

        val nearbyTarget = processed.forwardTargets.firstOrNull { it.transportType == TransportType.NEARBY }
        val meshtasticTarget = processed.forwardTargets.firstOrNull { it.transportType == TransportType.MESHTASTIC }

        assertNotNull(nearbyTarget)
        assertNotNull(meshtasticTarget)
        assertEquals("ep1", nearbyTarget?.endpointId)
        assertEquals("meshtastic", meshtasticTarget?.endpointId)
    }

    @Test
    fun `buildSos routes in parallel across both transports when enabled`() {
        useNearbyFlow.value = true
        useMeshtasticFlow.value = true

        val connectedPeers = mapOf("ep1" to "PEER_1")
        val result = meshRouter.buildSos("""{"uuid":"sos-123","severity":5}""", connectedPeers)

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertEquals(2, processed.forwardTargets.size)

        assertTrue(processed.forwardTargets.any { it.transportType == TransportType.NEARBY })
        assertTrue(processed.forwardTargets.any { it.transportType == TransportType.MESHTASTIC })
    }

    @Test
    fun `buildAndRoute falls back to Meshtastic when destination has no Nearby route`() = runTest {
        useNearbyFlow.value = true
        useMeshtasticFlow.value = true

        val destId = "REMOTE_NODE"
        val destDevice = KnownDevice(
            deviceId = destId,
            displayName = "Remote Node",
            publicKey = ByteArray(32) { 1 },
            lastSeen = 1000L
        )
        coEvery { deviceRepository.getDeviceById(destId) } returns destDevice
        every { eciesService.encryptToBase64(any(), any()) } returns "encrypted-payload"

        // No connected Nearby peers
        val connectedPeers = emptyMap<String, String>()
        val result = meshRouter.buildAndRoute(
            finalDestDeviceId = destId,
            plaintext = "Hello via LoRa",
            connectedPeers = connectedPeers
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertEquals(1, processed.forwardTargets.size)
        val target = processed.forwardTargets.first()
        assertEquals(TransportType.MESHTASTIC, target.transportType)
        assertEquals("meshtastic", target.endpointId)
    }

    @Test
    fun `incoming packet from Meshtastic delivers locally when addressed to local device`() = runTest {
        val incoming = MeshPacket(
            messageId = "msg-meshtastic-1",
            senderId = "REMOTE_RADIO",
            receiverId = myDeviceId,
            content = "SOS assistance needed",
            timestamp = 5000L,
            type = MeshPacket.PacketType.ROUTED_CHAT,
            originId = "REMOTE_RADIO",
            finalDestId = myDeviceId,
            hopCount = 1,
            maxHops = 3
        )

        val result = meshRouter.route(
            fromEndpointId = "meshtastic",
            packet = incoming,
            connectedPeers = emptyMap()
        )

        assertTrue(result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed
        assertNotNull(processed.localMessage)
    }
}
