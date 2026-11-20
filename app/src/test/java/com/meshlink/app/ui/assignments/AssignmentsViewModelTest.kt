package com.meshlink.app.ui.assignments

import android.content.Context
import app.cash.turbine.test
import com.meshlink.app.domain.model.AssignmentData
import com.meshlink.app.domain.repository.AssignmentRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val repository: AssignmentRepository = mockk(relaxed = true)

    private val sampleAssignments = listOf(
        AssignmentData(
            id = "asgn-001",
            incidentId = "inc-001",
            responderId = "resp-001",
            role = "LEAD",
            status = "DISPATCHED",
            notes = "Evacuate sector 4",
            description = "Wildfire approaching",
            latitude = 37.77,
            longitude = -122.42,
            priority = 4,
            severity = 8,
            offeredAt = 1000L,
            respondedAt = null,
            synced = false
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllAssignments() } returns flowOf(sampleAssignments)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `assignments flow emits repository data`() = runTest {
        val viewModel = AssignmentsViewModel(context, repository)
        viewModel.assignments.test {
            assertEquals(emptyList<AssignmentData>(), awaitItem())
            assertEquals(sampleAssignments, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `acceptAssignment delegates to repository`() = runTest {
        coEvery { repository.acceptAssignment("asgn-001") } just Runs

        val viewModel = AssignmentsViewModel(context, repository)
        viewModel.acceptAssignment("asgn-001")
        testScheduler.advanceUntilIdle()

        coVerify { repository.acceptAssignment("asgn-001") }
    }

    @Test
    fun `updateStatus delegates to repository with newStatus`() = runTest {
        coEvery { repository.updateAssignmentStatus("asgn-001", "ON_SCENE") } just Runs

        val viewModel = AssignmentsViewModel(context, repository)
        viewModel.updateStatus("asgn-001", "ON_SCENE")
        testScheduler.advanceUntilIdle()

        coVerify { repository.updateAssignmentStatus("asgn-001", "ON_SCENE") }
    }
}
