package com.meshlink.app.ui.settings

import android.net.Uri
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.location.LocationTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelAiTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockNearbyRepository: NearbyRepository = mockk(relaxed = true)
    private val mockLocationTracker: LocationTracker = mockk(relaxed = true)
    private val mockCoordinator: OfflineAiCoordinator = mockk(relaxed = true)
    private val mockAssetManager: ModelAssetManager = mockk(relaxed = true)

    private val coordinatorState = MutableStateFlow(AiCapabilityState.READY)
    private val isAiEnabled = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockCoordinator.state } returns coordinatorState
        every { mockCoordinator.isAiEnabled } returns isAiEnabled
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            nearbyRepository = mockNearbyRepository,
            locationTracker = mockLocationTracker,
            aiCoordinator = mockCoordinator,
            modelAssetManager = mockAssetManager
        )
    }

    @Test
    fun toggleAiEnabled_whenTrue_callsEnableAi() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toggleAiEnabled(true)
        advanceUntilIdle()

        coVerify { mockCoordinator.enableAi() }
    }

    @Test
    fun toggleAiEnabled_whenFalse_callsKillSwitch() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toggleAiEnabled(false)
        advanceUntilIdle()

        coVerify { mockCoordinator.triggerKillSwitch() }
    }

    @Test
    fun triggerAiKillSwitch_triggersCoordinatorKillSwitch() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.triggerAiKillSwitch()
        advanceUntilIdle()

        coVerify { mockCoordinator.triggerKillSwitch() }
    }

    @Test
    fun clearAiData_triggersKillSwitchDeletesModelAndUpdatesStatus() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { mockAssetManager.deleteInstalledModel() } returns AiResult.Success(Unit)

        vm.clearAiData()
        advanceUntilIdle()

        coVerify { mockCoordinator.triggerKillSwitch() }
        coVerify { mockAssetManager.deleteInstalledModel() }
        assertEquals("AI model and local cache cleared", vm.importStatus.value)
    }

    @Test
    fun importModel_onSuccess_initializesCoordinator() = runTest(testDispatcher) {
        val vm = createViewModel()
        val mockUri: Uri = mockk()
        val targetFile = File("gemma.bin")
        coEvery { mockAssetManager.importModelFromUri(mockUri, any()) } returns AiResult.Success(targetFile)

        vm.importModel(mockUri)
        advanceUntilIdle()

        coVerify { mockCoordinator.initialize() }
        assertTrue(vm.importStatus.value!!.contains("Ready: gemma.bin"))
    }

    @Test
    fun importModel_onFailure_updatesStatusWithError() = runTest(testDispatcher) {
        val vm = createViewModel()
        val mockUri: Uri = mockk()
        coEvery { mockAssetManager.importModelFromUri(mockUri, any()) } returns AiResult.Failure(AiError.ModelCorrupt("Hash mismatch"))

        vm.importModel(mockUri)
        advanceUntilIdle()

        assertTrue(vm.importStatus.value!!.contains("Import failed"))
    }
}
