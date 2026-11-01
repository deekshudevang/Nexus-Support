package com.meshlink.app.ui.discovery

import app.cash.turbine.test
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.domain.repository.NearbyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var viewModel: DiscoveryViewModel

    private val devicesFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private val connectionStatesFlow = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nearbyRepo = mockk(relaxed = true)

        every { nearbyRepo.discoveredDevices } returns devicesFlow
        every { nearbyRepo.connectionStates } returns connectionStatesFlow

        viewModel = DiscoveryViewModel(nearbyRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `devices reflects discoveredDevices flow`() = runTest {
        val device = DiscoveredDevice("endpoint-1", "Alpha Node")
        viewModel.devices.test {
            assertEquals(emptyList<DiscoveredDevice>(), awaitItem())

            devicesFlow.value = listOf(device)
            assertEquals(listOf(device), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connectionStates reflects repository connection states`() = runTest {
        viewModel.connectionStates.test {
            assertEquals(emptyMap<String, ConnectionState>(), awaitItem())

            connectionStatesFlow.value = mapOf("endpoint-1" to ConnectionState.CONNECTED)
            assertEquals(mapOf("endpoint-1" to ConnectionState.CONNECTED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeviceClick navigates immediately if already CONNECTED`() = runTest {
        val device = DiscoveredDevice("ep-1", "Rescue-Unit")
        connectionStatesFlow.value = mapOf("ep-1" to ConnectionState.CONNECTED)

        viewModel.navigateToChat.test {
            viewModel.onDeviceClick(device)
            testDispatcher.scheduler.advanceUntilIdle()

            val navTarget = awaitItem()
            assertEquals("ep-1", navTarget.first)
            assertEquals("Rescue-Unit", navTarget.second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeviceClick requests connection and navigates upon reaching CONNECTED`() = runTest {
        val device = DiscoveredDevice("ep-2", "Medical-Tent")
        connectionStatesFlow.value = mapOf("ep-2" to ConnectionState.DISCONNECTED)

        viewModel.navigateToChat.test {
            viewModel.onDeviceClick(device)
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 1) { nearbyRepo.requestConnection("ep-2") }

            // Simulate connection success
            connectionStatesFlow.value = mapOf("ep-2" to ConnectionState.CONNECTED)
            testDispatcher.scheduler.advanceUntilIdle()

            val navTarget = awaitItem()
            assertEquals("ep-2", navTarget.first)
            assertEquals("Medical-Tent", navTarget.second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeviceClick does nothing if already CONNECTING`() = runTest {
        val device = DiscoveredDevice("ep-3", "Rover-1")
        connectionStatesFlow.value = mapOf("ep-3" to ConnectionState.CONNECTING)

        val job = backgroundScope.launch { viewModel.connectionStates.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDeviceClick(device)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { nearbyRepo.requestConnection("ep-3") }
        job.cancel()
    }

    @Test
    fun `onDisconnectClick delegates to repository`() = runTest {
        viewModel.onDisconnectClick("ep-4")
        verify(exactly = 1) { nearbyRepo.disconnect("ep-4") }
    }
}
