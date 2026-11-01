package com.meshlink.app.ui.broadcast

import app.cash.turbine.test
import com.meshlink.app.domain.repository.NearbyRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var viewModel: BroadcastViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nearbyRepo = mockk(relaxed = true)
        viewModel = BroadcastViewModel(nearbyRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onMessageChanged updates message and clears error`() {
        viewModel.sendBroadcast() // Trigger blank error
        assertEquals("Message cannot be empty", viewModel.state.value.error)

        viewModel.onMessageChanged("Evacuate zone B")
        assertEquals("Evacuate zone B", viewModel.state.value.message)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `sendBroadcast fails when message is blank`() {
        viewModel.onMessageChanged("   ")
        viewModel.sendBroadcast()

        assertEquals("Message cannot be empty", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSending)
    }

    @Test
    fun `sendBroadcast fails when no peers are connected`() {
        every { nearbyRepo.getConnectedPeers() } returns emptyMap()

        viewModel.onMessageChanged("Wildfire approaching")
        viewModel.sendBroadcast()

        assertEquals("No peers connected", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSending)
    }

    @Test
    fun `sendBroadcast succeeds when peers are connected`() = runTest {
        every { nearbyRepo.getConnectedPeers() } returns mapOf("ep-1" to "Peer 1")

        viewModel.state.test {
            assertEquals("", awaitItem().message) // initial empty state

            viewModel.onMessageChanged("Supply drop at sector 4")
            assertEquals("Supply drop at sector 4", awaitItem().message)

            viewModel.sendBroadcast()
            testDispatcher.scheduler.runCurrent()

            // Intermediate sending state
            val sendingState = awaitItem()
            assertTrue(sendingState.isSending)

            testDispatcher.scheduler.advanceUntilIdle()

            // State after send completes
            val sentState = awaitItem()
            assertTrue(sentState.isSent)
            assertFalse(sentState.isSending)
            assertNull(sentState.error)

            coVerify(exactly = 1) { nearbyRepo.sendBroadcast("Supply drop at sector 4") }

            // Auto-reset delay(3000)
            testDispatcher.scheduler.advanceTimeBy(3100)

            val resetState = awaitItem()
            assertFalse(resetState.isSent)
            assertEquals("", resetState.message)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
