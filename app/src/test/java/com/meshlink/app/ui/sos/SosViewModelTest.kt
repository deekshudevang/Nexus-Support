package com.meshlink.app.ui.sos

import android.content.Context
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.NearbyRepository
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context: Context = mockk(relaxed = true)
    private val locationTracker: LocationTracker = mockk(relaxed = true)
    private val meshRouter: MeshRouter = mockk(relaxed = true)
    private val nearbyRepository: NearbyRepository = mockk(relaxed = true)
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

        viewModel = SosViewModel(
            context = context,
            myDeviceId = "test-device-id",
            locationTracker = locationTracker,
            meshRouter = meshRouter,
            nearbyRepository = nearbyRepository,
            sosBeaconAdvertiser = sosBeaconAdvertiser,
            sosNonceGenerator = sosNonceGenerator,
            aiCoordinator = aiCoordinator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        testScheduler.advanceUntilIdle()

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
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { aiCoordinator.onSosActivated() }
    }

    @Test
    fun toggleSos_activation_notifiesAiCoordinatorAndStartsBroadcasting() = testScope.runTest {
        assertFalse(viewModel.isBroadcasting.value)

        viewModel.toggleSos()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.isBroadcasting.value)
        verify(atLeast = 1) { aiCoordinator.onSosActivated() }
    }

    @Test
    fun toggleSos_deactivation_notifiesAiCoordinatorAndStopsAdvertising() = testScope.runTest {
        viewModel.toggleSos()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.isBroadcasting.value)

        viewModel.toggleSos()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.isBroadcasting.value)
        verify(atLeast = 1) { sosBeaconAdvertiser.stopAdvertising() }
        verify(atLeast = 1) { aiCoordinator.onSosDeactivated() }
    }
}
