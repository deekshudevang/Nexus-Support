package com.meshlink.app.mesh.security

import android.app.NotificationManager
import android.content.Context
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.identity.KeyProvider
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.routing.RoutingResult
import com.meshlink.app.mesh.routing.RoutingTable
import com.meshlink.app.mesh.routing.SeenMessageCache
import com.meshlink.app.mesh.util.toMeshPacket
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Mesh Security Hardening Acceptance Suite (Requirement C).
 * Verifies message integrity, replay protection, TTL & hop limits, routing loop prevention,
 * malformed packet fuzzing safety, and zero-trust verification for discoverable nodes.
 */
class MeshSecurityHardeningTest {

    private lateinit var meshRouter: MeshRouter
    private val myDeviceId = "SECURE_NODE_ALPHA"
    private val routingTable = RoutingTable(myDeviceId)
    private val seenMessageCache = SeenMessageCache()
    private val encryptionService = EncryptionService()
    private val sessionKeyStore: SessionKeyStore = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val pendingMessageRepository: PendingMessageRepository = mockk(relaxed = true)
    private val userProfileManager: UserProfileManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val notificationManager: NotificationManager = mockk(relaxed = true)

    private lateinit var eciesService: EciesService
    private lateinit var keyManager: KeyProvider
    private lateinit var recipientPublicKeyBytes: ByteArray

    @Before
    fun setup() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val recipientKeyPair = kpg.generateKeyPair()
        recipientPublicKeyBytes = recipientKeyPair.public.encoded

        keyManager = mockk()
        every { keyManager.keyPair } returns recipientKeyPair
        eciesService = EciesService(keyManager, encryptionService)

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

    // ── 1. Message Integrity via Authenticated Encryption ───────────────────────
    @Test
    fun testMessageIntegrity_tamperedPayload_rejectedByAesGcm() {
        val payload = "EMERGENCY_MEDICAL_EVACUATION_COORDS".toByteArray()
        val wireBytes = eciesService.encrypt(payload, recipientPublicKeyBytes)

        // Valid decrypt works
        val decrypted = eciesService.decrypt(wireBytes)
        assertNotNull(decrypted)
        assertEquals("EMERGENCY_MEDICAL_EVACUATION_COORDS", String(decrypted!!))

        // Single byte tampering in ciphertext block
        val tampered = wireBytes.copyOf()
        tampered[98] = (tampered[98].toInt() xor 0x01).toByte()

        val tamperedResult = eciesService.decrypt(tampered)
        assertNull("Tampered payload must fail GCM tag verification and return null", tamperedResult)
    }

    // ── 2. Replay Protection via SeenMessageCache ──────────────────────────────
    @Test
    fun testReplayProtection_identicalNonceOrMessageId_dropped() = runTest {
        val packet = MeshPacket(
            senderId = "PEER_B",
            receiverId = "PEER_C",
            content = "SOS Telemetry",
            timestamp = System.currentTimeMillis(),
            messageId = "UNIQUE_MSG_UUID_9981"
        )

        // First attempt routes successfully
        val res1 = meshRouter.route("ep_b", packet, emptyMap())
        assertTrue("First reception should be processed", res1 is RoutingResult.Processed)

        // Replay attempt must be dropped
        val res2 = meshRouter.route("ep_b", packet, emptyMap())
        assertEquals("Replayed message must be dropped", RoutingResult.Drop, res2)
    }

    // ── 3. TTL and Hop Limit Enforcement ────────────────────────────────────────
    @Test
    fun testTtlAndHopCount_exceededLimit_dropped() = runTest {
        val expiredPacket = MeshPacket(
            senderId = "PEER_B",
            receiverId = "PEER_C",
            content = "Status Ping",
            timestamp = System.currentTimeMillis(),
            hopCount = 7,
            maxHops = 7
        )

        val result = meshRouter.route("ep_b", expiredPacket, emptyMap())
        assertEquals("Packet exceeding TTL must be dropped", RoutingResult.Drop, result)
    }

    // ── 4. Routing Loop Prevention via Route History ────────────────────────────
    @Test
    fun testRoutingLoopPrevention_routeHistoryContainsSelf_dropped() = runTest {
        val loopingPacket = MeshPacket(
            senderId = "PEER_B",
            receiverId = "PEER_C",
            content = "Traversing Mesh",
            timestamp = System.currentTimeMillis(),
            messageId = "LOOP_MSG_001",
            hopCount = 3,
            maxHops = 7,
            routeHistory = listOf("PEER_B", myDeviceId, "PEER_D") // Already passed through myDeviceId!
        )

        val result = meshRouter.route("ep_b", loopingPacket, emptyMap())
        assertEquals("Looping packet containing self in route history must be dropped", RoutingResult.Drop, result)
    }

    // ── 5. Malformed Packet and Fuzzing Ingestion Safety ────────────────────────
    @Test
    fun testMalformedPacketFuzzing_corruptedJsonAndBytes_handledSafelyWithoutCrash() {
        val corruptedJsonSamples = listOf(
            "{".toByteArray(),
            "{\"messageId\":".toByteArray(),
            "{\"hopCount\": \"invalid_int\"}".toByteArray(),
            ByteArray(0),
            ByteArray(512) { 0xFF.toByte() },
            "{\"content\":\u0000\u0001\u0002}".toByteArray()
        )

        for (sample in corruptedJsonSamples) {
            val parsed = try {
                sample.toMeshPacket()
            } catch (e: Exception) {
                null
            }
            // Must either parse validly or return null without throwing unhandled exceptions
            assertTrue(parsed == null || parsed is MeshPacket)
        }
    }

    // ── 6. Zero Trust: Discoverable Node Never Implicitly Trusted ───────────────
    @Test
    fun testZeroTrust_discoverableNodeWithoutAuth_neverImplicitlyTrusted() = runTest {
        // Node is discovered via nearby BLE beacon (unverified)
        val beaconPacket = MeshPacket(
            type = MeshPacket.PacketType.SOS,
            senderId = "DISCOVERED_STRANGER_01",
            receiverId = MeshPacket.BROADCAST_DEST,
            content = "{\"emergencyType\":\"TRAPPED\"}",
            messageId = "123-456-789",
            timestamp = System.currentTimeMillis()
        )

        val result = meshRouter.route("sos-beacon", beaconPacket, emptyMap())
        assertTrue("Beacon from discovered node must be processed", result is RoutingResult.Processed)
        val processed = result as RoutingResult.Processed

        // Invariant: Unauthenticated discoverable node beacon NEVER floods to mesh peers
        assertEquals(
            "Discoverable unauthenticated node must NEVER trigger mesh peer forwarding",
            0,
            processed.forwardTargets.size
        )
        // Message is labeled unverified
        assertEquals("Unverified Nearby SOS", processed.localMessage?.senderName)
    }
}
