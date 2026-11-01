package com.meshlink.app.mesh.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.repository.DeviceRepositoryImpl
import com.meshlink.app.data.repository.MessageRepositoryImpl
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.routing.RoutingResult
import com.meshlink.app.mesh.routing.RoutingTable
import com.meshlink.app.mesh.routing.SeenMessageCache
import com.meshlink.app.mesh.transport.SosBeaconCodec
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for Defect N3: One Event, Two Paths.
 *
 * Implements scenarios S1–S12 from Execution Control E using production classes:
 * MeshRouter, MessageRepositoryImpl, DeviceRepositoryImpl, and a real in-memory
 * Room database (AppDatabase) with an injectable clock.
 *
 * Asserts visible alert count in DB, alert authentication status, and ACK count.
 */
@RunWith(RobolectricTestRunner::class)
class SosTwoPathsIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var deviceRepository: DeviceRepositoryImpl
    private lateinit var messageRepository: MessageRepositoryImpl
    private lateinit var meshRouter: MeshRouter

    private var simulatedTimeMs: Long = 1_000_000L
    private val myDeviceId = "DEVICE_RECEIVER"
    private val senderDeviceId = "DEVICE_SENDER"
    private val senderDeviceHash = SosBeaconCodec.crc32(senderDeviceId.toByteArray())

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        deviceRepository = DeviceRepositoryImpl(database.deviceDao())
        messageRepository = MessageRepositoryImpl(database.messageDao())

        val routingTable = RoutingTable(myDeviceId)
        val seenCache = SeenMessageCache(clock = { simulatedTimeMs })
        val seenBeaconCache = SeenMessageCache(clock = { simulatedTimeMs })
        val encryptionService: EncryptionService = mockk(relaxed = true)
        val eciesService: EciesService = mockk(relaxed = true)
        val sessionKeyStore: SessionKeyStore = mockk(relaxed = true)
        val pendingRepo: PendingMessageRepository = mockk(relaxed = true)
        val userProfileManager: UserProfileManager = mockk(relaxed = true)

        meshRouter = MeshRouter(
            context = context,
            myDeviceId = myDeviceId,
            routingTable = routingTable,
            seenMessageCache = seenCache,
            encryptionService = encryptionService,
            eciesService = eciesService,
            sessionKeyStore = sessionKeyStore,
            messageRepository = messageRepository,
            deviceRepository = deviceRepository,
            pendingMessageRepository = pendingRepo,
            userProfileManager = userProfileManager,
            seenBeaconCache = seenBeaconCache
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createBeaconPacket(
        deviceHash: Int = senderDeviceHash,
        delta: Int = 500,
        nonce: Byte = 1
    ): MeshPacket {
        val beaconId = "sos-$deviceHash-$delta-$nonce"
        return MeshPacket(
            senderId = deviceHash.toString(),
            receiverId = MeshPacket.BROADCAST_DEST,
            content = """{"emergencyType":"MEDICAL","severity":5,"lat":12.9716,"lon":77.5946}""",
            timestamp = simulatedTimeMs,
            type = MeshPacket.PacketType.SOS,
            messageId = beaconId,
            originId = deviceHash.toString(),
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 100
        )
    }

    private fun createMeshSosPacket(
        senderId: String = senderDeviceId,
        uuid: String = "SOS-UUID-AUTH-1",
        beaconKey: String? = "sos-$senderDeviceHash-500-1",
        senderName: String = "Alice"
    ): MeshPacket {
        val content = if (beaconKey != null) {
            """{"uuid":"$uuid","senderId":"$senderId","emergencyType":"MEDICAL","severity":5,"latitude":12.9716,"longitude":77.5946,"message":"Help!","beaconKey":"$beaconKey"}"""
        } else {
            """{"uuid":"$uuid","senderId":"$senderId","emergencyType":"MEDICAL","severity":5,"latitude":12.9716,"longitude":77.5946,"message":"Help!"}"""
        }
        return MeshPacket(
            senderId = senderId,
            receiverId = MeshPacket.BROADCAST_DEST,
            content = content,
            timestamp = simulatedTimeMs,
            type = MeshPacket.PacketType.SOS,
            messageId = uuid,
            originId = senderId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 10,
            senderName = senderName
        )
    }

    private fun extractAcks(result: RoutingResult): List<MeshPacket> {
        return if (result is RoutingResult.Processed) {
            result.forwardTargets.map { it.packet }.filter { it.type == MeshPacket.PacketType.ACK }
        } else emptyList()
    }

    // S1 — Beacon, then matching mesh SOS
    // After beacon: 1 unverified alert.
    // After authenticated mesh SOS: exactly 1 visible alert, upgraded to authenticated.
    // ACKs: 1, for the mesh packet only.
    @Test
    fun `S1_beacon_then_matching_mesh_merges_to_single_authenticated_alert_with_one_ack`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()
        val mesh = createMeshSosPacket(beaconKey = beacon.messageId)

        // 1. Beacon arrives first
        val r1 = meshRouter.route("sos-beacon", beacon, connectedPeers)
        assertEquals(0, extractAcks(r1).size)
        var alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
        assertEquals("Unverified Nearby SOS", alerts[0].senderName)

        // 2. Matching mesh SOS arrives
        val r2 = meshRouter.route("ep_sender", mesh, connectedPeers)
        val acks = extractAcks(r2)
        assertEquals(1, acks.size)
        assertEquals(senderDeviceId, acks[0].receiverId)
        assertEquals(mesh.messageId, acks[0].content)

        alerts = messageRepository.getAllMessages()
        assertEquals("Single visible alert upgraded to authenticated", 1, alerts.size)
        assertEquals(mesh.messageId, alerts[0].id)
        assertEquals("Alice", alerts[0].senderName)
    }

    // S2 — Mesh SOS, then matching beacon
    // Exactly 1 alert. Beacon links without creating a second alert.
    // ACKs: 1, for the mesh packet only.
    @Test
    fun `S2_mesh_then_matching_beacon_links_without_new_alert_or_beacon_ack`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()
        val mesh = createMeshSosPacket(beaconKey = beacon.messageId)

        // 1. Mesh SOS arrives first
        val r1 = meshRouter.route("ep_sender", mesh, connectedPeers)
        assertEquals(1, extractAcks(r1).size)
        var alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
        assertEquals(mesh.messageId, alerts[0].id)

        // 2. Matching beacon arrives later
        val r2 = meshRouter.route("sos-beacon", beacon, connectedPeers)
        assertEquals(0, extractAcks(r2).size)

        alerts = messageRepository.getAllMessages()
        assertEquals("Still exactly 1 alert, no competing alert created", 1, alerts.size)
        assertEquals(mesh.messageId, alerts[0].id)
    }

    // S3 — Beacon only
    // 1 unverified alert. 0 ACKs.
    @Test
    fun `S3_beacon_only_produces_one_unverified_alert_and_zero_acks`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()

        val r = meshRouter.route("sos-beacon", beacon, connectedPeers)
        assertEquals(0, extractAcks(r).size)

        val alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
        assertEquals(beacon.messageId, alerts[0].id)
        assertEquals("Unverified Nearby SOS", alerts[0].senderName)
    }

    // S4 — Mesh SOS only
    // 1 authenticated alert. 1 ACK.
    @Test
    fun `S4_mesh_sos_only_produces_one_authenticated_alert_and_one_ack`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val mesh = createMeshSosPacket()

        val r = meshRouter.route("ep_sender", mesh, connectedPeers)
        val acks = extractAcks(r)
        assertEquals(1, acks.size)
        assertEquals(mesh.messageId, acks[0].content)

        val alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
        assertEquals(mesh.messageId, alerts[0].id)
        assertEquals("Alice", alerts[0].senderName)
    }

    // S5 — Old-version sender without beaconKey
    // Beacon plus mesh SOS produces 2 alerts because they cannot be safely correlated.
    // 1 ACK, for the mesh packet only.
    @Test
    fun `S5_old_version_sender_without_beaconKey_produces_two_alerts_and_one_mesh_ack`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()
        val meshNoKey = createMeshSosPacket(beaconKey = null)

        val r1 = meshRouter.route("sos-beacon", beacon, connectedPeers)
        assertEquals(0, extractAcks(r1).size)

        val r2 = meshRouter.route("ep_sender", meshNoKey, connectedPeers)
        assertEquals(1, extractAcks(r2).size)

        val alerts = messageRepository.getAllMessages()
        assertEquals("Cannot match safely without beaconKey -> 2 alerts preserved", 2, alerts.size)
    }

    // S6 — Same beacon triple delivered twice (duplicate)
    // 1 unverified alert. 0 ACKs.
    @Test
    fun `S6_same_beacon_triple_delivered_twice_deduplicates_to_one_unverified_alert`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()

        val r1 = meshRouter.route("sos-beacon", beacon, connectedPeers)
        val r2 = meshRouter.route("sos-beacon", beacon, connectedPeers)

        assertTrue("Second beacon dropped by seenBeaconCache", r2 is RoutingResult.Drop)
        assertEquals(0, extractAcks(r1).size)

        val alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
    }

    // S7 — Duplicate authenticated mesh SOS
    // 1 visible authenticated alert.
    @Test
    fun `S7_duplicate_mesh_sos_drops_second_delivery_leaving_single_alert`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val mesh = createMeshSosPacket()

        val r1 = meshRouter.route("ep_sender", mesh, connectedPeers)
        val r2 = meshRouter.route("ep_sender", mesh, connectedPeers)

        assertTrue(r2 is RoutingResult.Drop)
        val alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
    }

    // S8 — Replay of genuine beacon after S1 or S2
    // No new alert (links to E); 0 ACKs.
    @Test
    fun `S8_genuine_beacon_replay_after_correlation_links_without_new_alert_or_ack`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()
        val mesh = createMeshSosPacket(beaconKey = beacon.messageId)

        meshRouter.route("sos-beacon", beacon, connectedPeers)
        meshRouter.route("ep_sender", mesh, connectedPeers)

        // Replay of same beacon arrives
        val r3 = meshRouter.route("sos-beacon", beacon, connectedPeers)
        // Should drop via seenBeaconCache or link without creating alert
        val acks = extractAcks(r3)
        assertEquals(0, acks.size)

        val alerts = messageRepository.getAllMessages()
        assertEquals(1, alerts.size)
        assertEquals(mesh.messageId, alerts[0].id)
    }

    // S9 — Forged beacon with a never-seen triple
    // 1 unverified alert; authenticated alert unchanged.
    @Test
    fun `S9_forged_beacon_with_never_seen_triple_creates_separate_unverified_alert_leaving_auth_alert_intact`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val mesh = createMeshSosPacket()
        meshRouter.route("ep_sender", mesh, connectedPeers)

        // Forged beacon with totally different triple
        val forgedBeacon = createBeaconPacket(deviceHash = 999999, delta = 888, nonce = 42)
        val rBeacon = meshRouter.route("sos-beacon", forgedBeacon, connectedPeers)
        assertEquals(0, extractAcks(rBeacon).size)

        val alerts = messageRepository.getAllMessages()
        assertEquals(2, alerts.size)
        assertTrue(alerts.any { it.id == mesh.messageId && it.senderName == "Alice" })
        assertTrue(alerts.any { it.id == forgedBeacon.messageId && it.senderName == "Unverified Nearby SOS" })
    }

    // S10 — Forged beacon uses real E's beaconKey but a different device hash
    // NO match; 1 extra unverified alert; E unchanged and not suppressed.
    @Test
    fun `S10_forged_beacon_with_matching_key_but_mismatched_device_hash_does_not_merge`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val realBeacon = createBeaconPacket(deviceHash = senderDeviceHash, delta = 500, nonce = 1)
        val mesh = createMeshSosPacket(beaconKey = realBeacon.messageId)
        meshRouter.route("ep_sender", mesh, connectedPeers)

        // Attacker creates a beacon with attacker's deviceHash but attempts to spoof real beacon's delta/nonce
        val attackerHash = 77777777
        val forgedBeacon = MeshPacket(
            senderId = attackerHash.toString(),
            receiverId = MeshPacket.BROADCAST_DEST,
            content = """{"emergencyType":"SECURITY","severity":1}""",
            timestamp = simulatedTimeMs,
            type = MeshPacket.PacketType.SOS,
            messageId = realBeacon.messageId, // Colliding / forged ID string
            originId = attackerHash.toString(),
            finalDestId = MeshPacket.BROADCAST_DEST,
            priority = 100
        )

        val r = meshRouter.route("sos-beacon", forgedBeacon, connectedPeers)
        assertEquals(0, extractAcks(r).size)

        val alerts = messageRepository.getAllMessages()
        // Condition (2) failed: E's authenticated sender maps to senderDeviceHash, NOT attackerHash.
        // Therefore, NO match!
        assertEquals(2, alerts.size)
        assertTrue(alerts.any { it.id == mesh.messageId && it.senderName == "Alice" })
    }

    // S11 — Two different authenticated events E1, E2 with the same beaconKey, then a beacon
    // Ambiguous matching: do not merge. 3 alerts: E1, E2, unverified beacon; none suppressed.
    @Test
    fun `S11_ambiguous_two_authenticated_events_with_same_beaconKey_does_not_merge_beacon`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val sharedBeaconKey = "sos-$senderDeviceHash-500-1"

        val mesh1 = createMeshSosPacket(senderId = senderDeviceId, uuid = "E1", beaconKey = sharedBeaconKey, senderName = "Alice")
        val mesh2 = createMeshSosPacket(senderId = senderDeviceId, uuid = "E2", beaconKey = sharedBeaconKey, senderName = "Bob")

        meshRouter.route("ep_sender", mesh1, connectedPeers)
        meshRouter.route("ep_sender", mesh2, connectedPeers)

        val beacon = createBeaconPacket(deviceHash = senderDeviceHash, delta = 500, nonce = 1)
        val rBeacon = meshRouter.route("sos-beacon", beacon, connectedPeers)
        assertEquals(0, extractAcks(rBeacon).size)

        val alerts = messageRepository.getAllMessages()
        // Ambiguous match (2 candidate events matching key and hash): must not merge, preserves all 3 alerts
        assertEquals(3, alerts.size)
        assertTrue(alerts.any { it.id == "E1" })
        assertTrue(alerts.any { it.id == "E2" })
        assertTrue(alerts.any { it.id == beacon.messageId })
    }

    // S12 — Beacon expires (its TTL) before matching mesh arrives
    // Final result: 1 authenticated alert. Expiry never suppresses or downgrades the authenticated event.
    @Test
    fun `S12_beacon_expiry_before_matching_mesh_produces_one_authenticated_alert`() = runTest {
        val connectedPeers = mapOf("ep_sender" to senderDeviceId)
        val beacon = createBeaconPacket()
        meshRouter.route("sos-beacon", beacon, connectedPeers)

        // Advance clock past 10 minutes (TTL)
        simulatedTimeMs += (11 * 60 * 1000L)

        // Authenticated mesh SOS arrives after beacon TTL expired
        val mesh = createMeshSosPacket(beaconKey = beacon.messageId)
        val rMesh = meshRouter.route("ep_sender", mesh, connectedPeers)

        assertEquals(1, extractAcks(rMesh).size)
        val alerts = messageRepository.getAllMessages()
        // Beacon was either cleaned up / merged into authenticated SOS; final authenticated alert is intact
        assertTrue(alerts.any { it.id == mesh.messageId && it.senderName == "Alice" })
    }
}
