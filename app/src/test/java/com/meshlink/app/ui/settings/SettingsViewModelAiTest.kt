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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun importModel_withPinnedSha256_verifiesAndInitializesDirectly() = runTest(testDispatcher) {
        val vm = createViewModel()
        val mockUri: Uri = mockk()
        val targetFile = File("gemma.bin")
        val pinnedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        coEvery { mockAssetManager.importModelFromUri(mockUri, pinnedHash) } returns AiResult.Success(targetFile)

        vm.importModel(mockUri, expectedSha256 = pinnedHash)
        advanceUntilIdle()

        coVerify { mockCoordinator.initialize() }
        assertNull(vm.pendingHashConfirmation.value)
        assertTrue(vm.importStatus.value!!.contains("Ready: gemma.bin"))
    }

    @Test
    fun importModel_withoutPinnedSha256_requestsConfirmation_thenConfirmsAndInitializes() = runTest(testDispatcher) {
        val vm = createViewModel()
        val mockUri: Uri = mockk()
        val targetFile = File("gemma.bin")
        val computedHash = "abc123computedhash"

        coEvery { mockAssetManager.importModelFromUri(mockUri, "") } returns AiResult.Success(targetFile)
        coEvery { mockAssetManager.computeSha256(targetFile) } returns computedHash
        coEvery { mockAssetManager.confirmModelHash(computedHash) } returns Unit

        vm.importModel(mockUri, expectedSha256 = "")
        advanceUntilIdle()

        // Confirmation dialog state is populated
        assertEquals(computedHash, vm.pendingHashConfirmation.value)
        assertTrue(vm.importStatus.value!!.contains("Awaiting confirmation"))

        // User confirms dialog
        vm.confirmPendingModelHash()
        advanceUntilIdle()

        coVerify { mockAssetManager.confirmModelHash(computedHash) }
        coVerify { mockCoordinator.initialize() }
        assertNull(vm.pendingHashConfirmation.value)
        assertTrue(vm.importStatus.value!!.contains("Ready: on-device model active"))
    }

    @Test
    fun cancelPendingModelHash_deletesInstalledModelAndClearsPending() = runTest(testDispatcher) {
        val vm = createViewModel()
        val mockUri: Uri = mockk()
        val targetFile = File("gemma.bin")
        val computedHash = "abc123computedhash"

        coEvery { mockAssetManager.importModelFromUri(mockUri, "") } returns AiResult.Success(targetFile)
        coEvery { mockAssetManager.computeSha256(targetFile) } returns computedHash
        coEvery { mockAssetManager.deleteInstalledModel() } returns AiResult.Success(Unit)

        vm.importModel(mockUri)
        advanceUntilIdle()

        assertNotNull(vm.pendingHashConfirmation.value)

        vm.cancelPendingModelHash()
        advanceUntilIdle()

        assertNull(vm.pendingHashConfirmation.value)
        coVerify { mockAssetManager.deleteInstalledModel() }
        assertEquals("Model import cancelled", vm.importStatus.value)
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

    @Test
    fun rollbackModel_whenSuccessful_updatesStatusAndRefreshesState() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { mockCoordinator.rollbackModel() } returns AiResult.Success(Unit)

        vm.rollbackModel()
        advanceUntilIdle()

        coVerify { mockCoordinator.rollbackModel() }
        assertTrue(vm.importStatus.value!!.contains("Rollback successful"))
    }
}
