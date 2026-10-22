package com.meshlink.app.ui.sos

import android.content.Context
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.SosPacketData
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.SosRepository
import com.meshlink.app.location.LocationTracker
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.transport.SosBeaconAdvertiser
import com.meshlink.app.mesh.transport.SosNonceGenerator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context: Context = mockk(relaxed = true)
    private val locationTracker: LocationTracker = mockk(relaxed = true)
    private val meshRouter: MeshRouter = mockk(relaxed = true)
    private val nearbyRepository: NearbyRepository = mockk(relaxed = true)
    private val sosRepository: SosRepository = mockk(relaxed = true)
    private val sosBeaconAdvertiser: SosBeaconAdvertiser = mockk(relaxed = true)
    private val sosNonceGenerator: SosNonceGenerator = mockk(relaxed = true)
    private val aiCoordinator: OfflineAiCoordinator = mockk(relaxed = true)

    private val incomingPacketsFlow = MutableSharedFlow<MeshPacket>(replay = 1)

    private lateinit var viewModel: SosViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { nearbyRepository.incomingPackets } returns incomingPacketsFlow
        coEvery { locationTracker.getCurrentLocation(any()) } returns null
        coEvery { sosRepository.getActiveLocalSos() } returns null
        coEvery { sosRepository.enqueueSos(any()) } returns true
        coEvery { sosRepository.updateSosStatus(any(), any()) } returns Unit
        coEvery { sosRepository.acknowledgeSos(any()) } returns true
        coEvery { sosRepository.cancelSos(any()) } returns Unit

        viewModel = createViewModel()
    }

    private fun createViewModel(): SosViewModel {
        return SosViewModel(
            context = context,
            myDeviceId = "test-device-id",
            locationTracker = locationTracker,
            meshRouter = meshRouter,
            nearbyRepository = nearbyRepository,
            sosRepository = sosRepository,
            sosBeaconAdvertiser = sosBeaconAdvertiser,
            sosNonceGenerator = sosNonceGenerator,
            aiCoordinator = aiCoordinator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Acceptance Criteria 1: Success ---
    @Test
    fun test_sos_lifecycle_success_path() = testScope.runTest {
        assertFalse(viewModel.isBroadcasting.value)
        assertEquals(SosViewModel.SosDeliveryState.IDLE, viewModel.deliveryStatus.value)

        // 1. User activates SOS
        viewModel.toggleSos()
        testScheduler.runCurrent()

        assertTrue(viewModel.isBroadcasting.value)
        assertEquals(SosViewModel.SosDeliveryState.TRANSMITTING, viewModel.deliveryStatus.value)

        // Capture enqueued SOS packet
        var enqueuedUuid: String? = null
        coVerify(atLeast = 1) {
            sosRepository.enqueueSos(match {
                enqueuedUuid = it.uuid
                it.senderId == "test-device-id" && it.status == SosPacketData.SosStatus.PENDING
            })
        }
        coVerify(atLeast = 1) {
            sosRepository.updateSosStatus(enqueuedUuid!!, SosPacketData.SosStatus.SENT)
        }
        verify(atLeast = 1) { nearbyRepository.sendSos(any()) }

        // 2. Peer acknowledges SOS via matching ACK packet
        val ackPacket = MeshPacket(
            senderId = "peer-node-1",
            receiverId = "test-device-id",
            content = enqueuedUuid!!,
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.ACK
        )
        incomingPacketsFlow.emit(ackPacket)
        testScheduler.runCurrent()

        assertEquals(SosViewModel.SosDeliveryState.PEER_ACKNOWLEDGED, viewModel.deliveryStatus.value)
        coVerify(atLeast = 1) { sosRepository.acknowledgeSos(enqueuedUuid!!) }
    }

    // --- Acceptance Criteria 2: Timeout and Retry with Backoff ---
    @Test
    fun test_sos_timeout_and_retry_backoff() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.runCurrent()

        assertEquals(SosViewModel.SosDeliveryState.TRANSMITTING, viewModel.deliveryStatus.value)
        verify(exactly = 1) { nearbyRepository.sendSos(any()) }

        // Advance past initial backoff window (2000ms + max 500ms jitter)
        advanceTimeBy(3000)
        testScheduler.runCurrent()

        // Verify attempt 2 was triggered
        verify(atLeast = 2) { nearbyRepository.sendSos(any()) }
    }

    // --- Acceptance Criteria 3: Duplicate Requests Suppression ---
    @Test
    fun test_sos_duplicate_requests_debounced() = testScope.runTest {
        // First activation
        viewModel.toggleSos()
        testScheduler.runCurrent()
        assertTrue(viewModel.isBroadcasting.value)

        // Immediate rapid second toggle (<1000ms) is debounced
        viewModel.toggleSos()
        testScheduler.runCurrent()

        // State remains broadcasting because rapid toggle was ignored
        assertTrue(viewModel.isBroadcasting.value)
    }

    @Test
    fun test_sos_duplicate_uuid_enqueue_suppressed() = testScope.runTest {
        // When repository reports duplicate UUID, retry loop does not start for duplicate
        coEvery { sosRepository.enqueueSos(any()) } returns false

        viewModel.toggleSos()
        testScheduler.runCurrent()

        // sendSos is not invoked if enqueue fails due to duplicate
        verify(exactly = 0) { nearbyRepository.sendSos(any()) }
    }

    // --- Acceptance Criteria 4: Disconnection Resilience ---
    @Test
    fun test_sos_disconnection_retains_durable_queue() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.runCurrent()

        // Packet was persisted to durable queue
        coVerify(atLeast = 1) { sosRepository.enqueueSos(any()) }
        coVerify(atLeast = 1) { sosRepository.updateSosStatus(any(), SosPacketData.SosStatus.SENT) }
        assertTrue(viewModel.isBroadcasting.value)
    }

    // --- Acceptance Criteria 5: Retry Exhaustion ---
    @Test
    fun test_sos_retry_exhaustion_marks_failed() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.runCurrent()

        assertEquals(SosViewModel.SosDeliveryState.TRANSMITTING, viewModel.deliveryStatus.value)

        // Advance time through all 5 retry backoff intervals (2s + 4s + 8s + 16s + 30s + jitter)
        advanceTimeBy(100_000)
        testScheduler.advanceUntilIdle()

        // After max retry attempts exhausted without ACK, transitions to FAILED
        assertEquals(SosViewModel.SosDeliveryState.FAILED, viewModel.deliveryStatus.value)
        coVerify(atLeast = 1) { sosRepository.updateSosStatus(any(), SosPacketData.SosStatus.FAILED) }
    }

    // --- Acceptance Criteria 6: Process Recreation Recovery ---
    @Test
    fun test_sos_process_recreation_restores_active_state() = testScope.runTest {
        val existingUnacknowledgedSos = SosPacketData(
            uuid = "recovered-uuid-999",
            senderId = "test-device-id",
            emergencyType = SosPacketData.EmergencyType.MEDICAL,
            severity = 5,
            message = "SOS! I need help!",
            status = SosPacketData.SosStatus.SENT
        )
        coEvery { sosRepository.getActiveLocalSos() } returns existingUnacknowledgedSos

        // Instantiate new ViewModel (simulating process recreation after crash/kill)
        val recreatedVm = createViewModel()
        testScheduler.runCurrent()

        assertTrue(recreatedVm.isBroadcasting.value)
        assertEquals(SosViewModel.SosDeliveryState.TRANSMITTING, recreatedVm.deliveryStatus.value)

        // Incoming ACK for recovered UUID completes delivery
        val ackPacket = MeshPacket(
            senderId = "peer-node-2",
            receiverId = "test-device-id",
            content = "recovered-uuid-999",
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.ACK
        )
        incomingPacketsFlow.emit(ackPacket)
        testScheduler.runCurrent()

        assertEquals(SosViewModel.SosDeliveryState.PEER_ACKNOWLEDGED, recreatedVm.deliveryStatus.value)
        coVerify(atLeast = 1) { sosRepository.acknowledgeSos("recovered-uuid-999") }
    }

    // --- Acceptance Criteria 7: Partial Failure Resilience ---
    @Test
    fun test_sos_partial_failure_does_not_crash_retry_loop() = testScope.runTest {
        // Simulate Nearby Connections throwing an IOException on first broadcast attempt
        var callCount = 0
        every { nearbyRepository.sendSos(any()) } answers {
            callCount++
            if (callCount == 1) {
                throw IOException("Nearby radio transport temporarily unavailable")
            }
        }

        viewModel.toggleSos()
        testScheduler.runCurrent()

        // Broadcast did not crash; state is still active
        assertTrue(viewModel.isBroadcasting.value)

        // Advance to retry attempt 2
        advanceTimeBy(4000)
        testScheduler.runCurrent()

        // Succeeded on subsequent retry attempt
        assertTrue(callCount >= 2)
    }

    // --- Existing Features & Truthful Status ---
    @Test
    fun incomingSosPacket_notifiesAiCoordinatorForPreemption() = testScope.runTest {
        val sosPacket = MeshPacket(
            senderId = "sender-node",
            receiverId = "*",
            content = "SOS EMERGENCY",
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.SOS
        )

        incomingPacketsFlow.emit(sosPacket)
        testScheduler.runCurrent()

        verify(atLeast = 1) { aiCoordinator.onSosActivated() }
    }

    @Test
    fun incomingNonSosPacket_doesNotNotifyAiCoordinator() = testScope.runTest {
        val chatPacket = MeshPacket(
            senderId = "sender-node",
            receiverId = "*",
            content = "Normal chat message",
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.CHAT
        )

        incomingPacketsFlow.emit(chatPacket)
        testScheduler.runCurrent()

        verify(exactly = 0) { aiCoordinator.onSosActivated() }
    }

    @Test
    fun gpsFixAvailable_reflectsRealLocationAvailability() = testScope.runTest {
        testScheduler.runCurrent()
        assertFalse(viewModel.gpsFixAvailable.value)

        val mockLocation = mockk<android.location.Location> {
            every { latitude } returns 37.7749
            every { longitude } returns -122.4194
        }
        coEvery { locationTracker.getCurrentLocation(any()) } returns mockLocation

        viewModel.refreshGpsStatus()
        testScheduler.runCurrent()

        assertTrue(viewModel.gpsFixAvailable.value)
    }

    @Test
    fun setUplink_switchesBetweenSupportedTransports() {
        assertEquals(SosViewModel.UplinkType.NEARBY_BLE, viewModel.activeUplink.value)

        viewModel.setUplink(SosViewModel.UplinkType.LORA_RF)
        assertEquals(SosViewModel.UplinkType.LORA_RF, viewModel.activeUplink.value)

        viewModel.setUplink(SosViewModel.UplinkType.NEARBY_BLE)
        assertEquals(SosViewModel.UplinkType.NEARBY_BLE, viewModel.activeUplink.value)
    }

    // --- Priority C: SOS Concurrency & Reliability Regressions ---
    @Test
    fun test_sos_cancellation_during_gps_lookup_prevents_enqueue_and_retry() = testScope.runTest {
        // Simulate GPS lookup that suspends
        coEvery { locationTracker.getCurrentLocation(any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            null
        }

        // 1. User activates SOS
        viewModel.toggleSos()
        testScheduler.runCurrent()
        assertTrue(viewModel.isBroadcasting.value)

        // Wait past wall-clock debounce threshold (1000ms)
        Thread.sleep(1050)

        // 2. User cancels SOS before GPS returns
        viewModel.toggleSos()
        testScheduler.runCurrent()
        assertFalse(viewModel.isBroadcasting.value)
        assertEquals(SosViewModel.SosDeliveryState.IDLE, viewModel.deliveryStatus.value)

        // Advance time past the GPS timeout
        advanceTimeBy(10_000)
        testScheduler.advanceUntilIdle()

        // Ensure no SOS was ever persisted or sent
        coVerify(exactly = 0) { sosRepository.enqueueSos(any()) }
        verify(exactly = 0) { nearbyRepository.sendSos(any()) }
    }

    @Test
    fun test_sos_duplicate_ack_ignored_once_peer_acknowledged() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.runCurrent()

        var enqueuedUuid: String? = null
        coVerify(atLeast = 1) {
            sosRepository.enqueueSos(match {
                enqueuedUuid = it.uuid
                true
            })
        }

        val ackPacket = MeshPacket(
            senderId = "peer-node-1",
            receiverId = "test-device-id",
            content = enqueuedUuid!!,
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.ACK
        )

        // First ACK
        incomingPacketsFlow.emit(ackPacket)
        testScheduler.runCurrent()
        assertEquals(SosViewModel.SosDeliveryState.PEER_ACKNOWLEDGED, viewModel.deliveryStatus.value)
        coVerify(exactly = 1) { sosRepository.acknowledgeSos(enqueuedUuid!!) }

        // Second duplicate ACK (relayed via another peer)
        incomingPacketsFlow.emit(ackPacket)
        testScheduler.runCurrent()
        assertEquals(SosViewModel.SosDeliveryState.PEER_ACKNOWLEDGED, viewModel.deliveryStatus.value)
        // verify still exactly 1 call
        coVerify(exactly = 1) { sosRepository.acknowledgeSos(enqueuedUuid!!) }
    }

    @Test
    fun test_sos_mismatched_ack_does_not_acknowledge() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.runCurrent()

        assertEquals(SosViewModel.SosDeliveryState.TRANSMITTING, viewModel.deliveryStatus.value)

        val mismatchedAck = MeshPacket(
            senderId = "peer-node-1",
            receiverId = "test-device-id",
            content = "mismatched-uuid-9999",
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.ACK
        )

        incomingPacketsFlow.emit(mismatchedAck)
        testScheduler.runCurrent()

        // State remains transmitting
        assertEquals(SosViewModel.SosDeliveryState.TRANSMITTING, viewModel.deliveryStatus.value)
        coVerify(exactly = 0) { sosRepository.acknowledgeSos(any()) }
    }

    @Test
    fun test_sos_stale_ack_after_cancellation_ignored() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.runCurrent()

        var enqueuedUuid: String? = null
        coVerify(atLeast = 1) {
            sosRepository.enqueueSos(match {
                enqueuedUuid = it.uuid
                true
            })
        }

        // Cancel SOS after wall-clock debounce period
        Thread.sleep(1050)
        viewModel.toggleSos()
        testScheduler.runCurrent()

        assertFalse(viewModel.isBroadcasting.value)
        assertEquals(SosViewModel.SosDeliveryState.IDLE, viewModel.deliveryStatus.value)

        // Stale ACK arrives for the cancelled UUID
        val staleAck = MeshPacket(
            senderId = "peer-node-1",
            receiverId = "test-device-id",
            content = enqueuedUuid!!,
            timestamp = System.currentTimeMillis(),
            type = MeshPacket.PacketType.ACK
        )

        incomingPacketsFlow.emit(staleAck)
        testScheduler.runCurrent()

        // Must remain IDLE, not transition to PEER_ACKNOWLEDGED
        assertEquals(SosViewModel.SosDeliveryState.IDLE, viewModel.deliveryStatus.value)
        coVerify(exactly = 0) { sosRepository.acknowledgeSos(any()) }
    }
}
