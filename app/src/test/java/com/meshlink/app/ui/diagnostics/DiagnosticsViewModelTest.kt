package com.meshlink.app.ui.diagnostics

import app.cash.turbine.test
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DomainLink
import com.meshlink.app.domain.model.ScanStrategy
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var pendingMessageRepo: PendingMessageRepository
    private lateinit var viewModel: DiagnosticsViewModel

    private val connStatesFlow = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    private val graphFlow = MutableStateFlow<Map<String, Map<String, DomainLink>>>(emptyMap())
    private val scanStrategyFlow = MutableStateFlow(ScanStrategy.CONTINUOUS)
    private val pendingCountFlow = MutableStateFlow(0)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nearbyRepo = mockk(relaxed = true)
        pendingMessageRepo = mockk(relaxed = true)

        every { nearbyRepo.connectionStates } returns connStatesFlow
        every { nearbyRepo.graphFlow } returns graphFlow
        every { nearbyRepo.scanStrategy } returns scanStrategyFlow
        every { pendingMessageRepo.getPendingCountFlow() } returns pendingCountFlow

        viewModel = DiagnosticsViewModel(nearbyRepo, pendingMessageRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState computes active connections, known nodes, links, and pending count`() = runTest {
        viewModel.uiState.test {
            // Initial item from stateIn initialValue
            val initial = awaitItem()
            assertEquals(0, initial.activeConnections)
            assertEquals(0, initial.knownNodesCount)
            assertEquals(0, initial.totalLinks)
            assertEquals(0, initial.pendingMessagesCount)
            assertEquals("UNKNOWN", initial.scanStrategyName)

            // Initial combination event
            val combinedInitial = awaitItem()
            assertEquals("CONTINUOUS", combinedInitial.scanStrategyName)

            // Update connection states (1 CONNECTED, 1 AUTHENTICATED, 1 CONNECTING)
            connStatesFlow.value = mapOf(
                "nodeA" to ConnectionState.CONNECTED,
                "nodeB" to ConnectionState.AUTHENTICATED,
                "nodeC" to ConnectionState.CONNECTING
            )

            // Update graph topology
            val link1 = DomainLink("nodeA", "nodeB", 90)
            val link2 = DomainLink("nodeB", "nodeA", 85)
            graphFlow.value = mapOf(
                "nodeA" to mapOf("nodeB" to link1),
                "nodeB" to mapOf("nodeA" to link2)
            )

            // Update pending count
            pendingCountFlow.value = 5

            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(2, updated.activeConnections)
            assertEquals(2, updated.knownNodesCount)
            assertEquals(2, updated.totalLinks)
            assertEquals(5, updated.pendingMessagesCount)
            assertEquals("CONTINUOUS", updated.scanStrategyName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates strategy name dynamically`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            scanStrategyFlow.value = ScanStrategy.PAUSED
            testDispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem()
            assertEquals("PAUSED", state.scanStrategyName)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
