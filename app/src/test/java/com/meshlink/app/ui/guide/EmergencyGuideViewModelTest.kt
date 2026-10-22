package com.meshlink.app.ui.guide

import app.cash.turbine.test
import com.meshlink.app.domain.model.EmergencyGuide
import com.meshlink.app.domain.repository.EmergencyGuideRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class EmergencyGuideViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: EmergencyGuideRepository
    private lateinit var viewModel: EmergencyGuideViewModel

    private val sampleGuide = EmergencyGuide(
        id = "cpr",
        title = "CPR Instructions",
        icon = "heart",
        steps = listOf("Check breathing", "Begin chest compressions"),
        warnings = listOf("Do not stop until EMS arrives"),
        keywords = listOf("cpr", "cardiac", "resuscitation")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.searchGuides("") } returns flowOf(listOf(sampleGuide))
        every { repository.searchGuides("cpr") } returns flowOf(listOf(sampleGuide))
        every { repository.searchGuides("burn") } returns flowOf(emptyList())

        viewModel = EmergencyGuideViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state emits all guides and empty query`() = runTest {
        assertEquals("", viewModel.searchQuery.value)

        viewModel.guides.test {
            assertEquals(emptyList<EmergencyGuide>(), awaitItem()) // initial stateIn value

            testDispatcher.scheduler.advanceTimeBy(350) // pass 300ms debounce
            assertEquals(listOf(sampleGuide), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSearchQueryChanged updates query and filters guides`() = runTest {
        viewModel.guides.test {
            awaitItem() // initial

            testDispatcher.scheduler.advanceTimeBy(350)
            assertEquals(listOf(sampleGuide), awaitItem())

            viewModel.onSearchQueryChanged("burn")
            assertEquals("burn", viewModel.searchQuery.value)

            testDispatcher.scheduler.advanceTimeBy(350)
            assertEquals(emptyList<EmergencyGuide>(), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
