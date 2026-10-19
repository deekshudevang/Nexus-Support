package com.meshlink.app.ui.settings

import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.SettingsRepository
import com.meshlink.app.location.LocationTracker
import com.meshlink.app.mesh.battery.AdaptiveScanController
import com.meshlink.app.mesh.battery.BatteryMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockNearbyRepository: NearbyRepository = mockk(relaxed = true)
    private val mockLocationTracker: LocationTracker = mockk(relaxed = true)
    private val mockCoordinator: OfflineAiCoordinator = mockk(relaxed = true)
    private val mockAssetManager: ModelAssetManager = mockk(relaxed = true)
    private val mockSettingsRepository: SettingsRepository = mockk(relaxed = true)
    private val mockAdaptiveScanController: AdaptiveScanController = mockk(relaxed = true)
    private val mockBatteryMonitor: BatteryMonitor = mockk(relaxed = true)

    private val useNearbyFlow = MutableStateFlow(true)
    private val useMeshtasticFlow = MutableStateFlow(false)
    private val batteryOptFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockCoordinator.state } returns MutableStateFlow(AiCapabilityState.READY)
        every { mockCoordinator.isAiEnabled } returns MutableStateFlow(true)
        every { mockSettingsRepository.useNearby } returns useNearbyFlow
        every { mockSettingsRepository.useMeshtastic } returns useMeshtasticFlow
        every { mockSettingsRepository.batteryOptimization } returns batteryOptFlow
        every { mockBatteryMonitor.getBatteryLevel() } returns 85
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
            modelAssetManager = mockAssetManager,
            settingsRepository = mockSettingsRepository,
            adaptiveScanController = mockAdaptiveScanController,
            batteryMonitor = mockBatteryMonitor
        )
    }

    @Test
    fun initialValues_matchRepositoryFlows() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.useNearby.value)
        assertFalse(vm.useMeshtastic.value)
        assertTrue(vm.batteryOptimization.value)
    }

    @Test
    fun setUseNearby_whenFalse_persistsAndPausesNearby() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setUseNearby(false)
        advanceUntilIdle()

        coVerify { mockSettingsRepository.setUseNearby(false) }
        verify { mockNearbyRepository.pauseScanning() }
    }

    @Test
    fun setUseNearby_whenTrue_persistsAndResumesNearby() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setUseNearby(true)
        advanceUntilIdle()

        coVerify { mockSettingsRepository.setUseNearby(true) }
        verify { mockNearbyRepository.resumeScanning() }
    }

    @Test
    fun setUseMeshtastic_persistsValue() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setUseMeshtastic(true)
        advanceUntilIdle()

        coVerify { mockSettingsRepository.setUseMeshtastic(true) }
    }

    @Test
    fun setBatteryOptimization_persistsAndUpdatesScanController() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setBatteryOptimization(false)
        advanceUntilIdle()

        coVerify { mockSettingsRepository.setBatteryOptimization(false) }
        verify { mockAdaptiveScanController.updateSettings(85, false) }
    }

    @Test
    fun toggleMasterScanning_pausesAndResumesScanningAndLocation() = runTest(testDispatcher) {
        val vm = createViewModel()
        
        vm.toggleMasterScanning(true)
        assertTrue(vm.isMasterScanningPaused.value)
        verify { mockNearbyRepository.pauseScanning() }
        verify { mockLocationTracker.setManualPause(true) }

        vm.toggleMasterScanning(false)
        assertFalse(vm.isMasterScanningPaused.value)
        verify { mockNearbyRepository.resumeScanning() }
        verify { mockLocationTracker.setManualPause(false) }
    }
}
