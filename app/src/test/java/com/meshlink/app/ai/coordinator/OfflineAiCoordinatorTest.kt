package com.meshlink.app.ai.coordinator

import android.content.ComponentCallbacks2
import com.meshlink.app.ai.fakes.FakeLocalAiEngine
import com.meshlink.app.ai.fakes.FakeModelAssetManager
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineAiCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeAssetManager: FakeModelAssetManager
    private lateinit var fakeEngine: FakeLocalAiEngine
    private lateinit var coordinator: OfflineAiCoordinatorImpl

    @Before
    fun setUp() {
        fakeAssetManager = FakeModelAssetManager()
        fakeEngine = FakeLocalAiEngine()
        coordinator = OfflineAiCoordinatorImpl(
            modelAssetManager = fakeAssetManager,
            localAiEngine = fakeEngine,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun initialState_defaultsToNotInstalledAndDisabled() {
        assertEquals(AiCapabilityState.NOT_INSTALLED, coordinator.state.value)
        assertFalse(coordinator.isAiEnabled.value)
    }

    @Test
    fun initialize_whenNoModelExists_remainsNotInstalled() = testScope.runTest {
        fakeAssetManager.modelFileToReturn = null

        val state = coordinator.initialize()

        assertEquals(AiCapabilityState.NOT_INSTALLED, state)
        assertEquals(AiCapabilityState.NOT_INSTALLED, coordinator.state.value)
    }

    @Test
    fun initialize_whenModelExists_transitionsToReady() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile

        val state = coordinator.initialize()

        assertEquals(AiCapabilityState.READY, state)
        assertEquals(AiCapabilityState.READY, coordinator.state.value)
    }

    @Test
    fun executeInference_whenAiDisabled_returnsDisabledError() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.initialize()

        val result = coordinator.executeInference {
            AiResult.Success("output")
        }

        assertTrue(result.isFailure)
        assertEquals(AiError.DisabledByKillSwitch, (result as AiResult.Failure).error)
    }

    @Test
    fun executeInference_whenEnabled_succeeds() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        val result = coordinator.executeInference {
            AiResult.Success("output")
        }

        assertTrue(result.isSuccess)
        assertEquals("output", (result as AiResult.Success).data)
        assertEquals(AiCapabilityState.READY, coordinator.state.value)
    }

    @Test
    fun killSwitch_immediatelyDisablesAiAndUnloadsEngine() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        coordinator.triggerKillSwitch()

        assertFalse(coordinator.isAiEnabled.value)
        assertEquals(AiCapabilityState.NOT_INSTALLED, coordinator.state.value)
        assertTrue(fakeEngine.unloadCalled)

        val result = coordinator.executeInference {
            AiResult.Success("should not run")
        }
        assertTrue(result.isFailure)
        assertEquals(AiError.DisabledByKillSwitch, (result as AiResult.Failure).error)
    }

    @Test
    fun onSosActivated_preemptsActiveInferenceImmediately() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        var inferenceResult: AiResult<String>? = null

        // Launch long-running inference in background
        val job = launch {
            inferenceResult = coordinator.executeInference(timeoutMs = 10000L) {
                delay(5000L)
                AiResult.Success("done")
            }
        }

        testScheduler.advanceTimeBy(100L) // start execution
        coordinator.onSosActivated() // Trigger emergency SOS preemption
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(inferenceResult != null)
        assertTrue(inferenceResult!!.isFailure)
        assertEquals(AiError.SosPriorityPreempted, (inferenceResult as AiResult.Failure).error)
    }

    @Test
    @Suppress("DEPRECATION")
    fun onTrimMemory_whenRunningLow_unloadsEngineAndTransitionsToLowMemory() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        coordinator.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertEquals(AiCapabilityState.LOW_MEMORY, coordinator.state.value)
        assertTrue(fakeEngine.unloadCalled)

        val result = coordinator.executeInference {
            AiResult.Success("output")
        }
        assertTrue(result.isFailure)
        assertEquals(AiError.LowMemory, (result as AiResult.Failure).error)
    }

    @Test
    @Suppress("DEPRECATION")
    fun onTrimMemory_whenModerate_doesNotUnloadEngineAndRemainsReady() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        coordinator.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        assertEquals(AiCapabilityState.READY, coordinator.state.value)
        assertFalse(fakeEngine.unloadCalled)

        val result = coordinator.executeInference {
            AiResult.Success("output")
        }
        assertTrue(result.isSuccess)
    }

    @Test
    @Suppress("DEPRECATION")
    fun onTrimMemory_whenCritical_unloadsEngine() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        coordinator.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        assertEquals(AiCapabilityState.LOW_MEMORY, coordinator.state.value)
        assertTrue(fakeEngine.unloadCalled)
    }

    @Test
    fun executeInference_timesOutProperly() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        val result = coordinator.executeInference(timeoutMs = 500L) {
            delay(1000L)
            AiResult.Success("done")
        }

        assertTrue(result.isFailure)
        assertTrue((result as AiResult.Failure).error is AiError.Timeout)
    }

    @Test
    fun rollbackModel_whenSuccessful_unloadsAndReinitializesRestoredModel() = testScope.runTest {
        val originalFile = File.createTempFile("original_model", ".bin")
        val candidateFile = File.createTempFile("candidate_model", ".bin")
        fakeAssetManager.modelFileToReturn = candidateFile
        fakeAssetManager.previousSlotToReturn = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "baseline",
            file = originalFile,
            sha256 = "orig_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.PREVIOUS
        )
        coordinator.enableAi()

        val rollbackResult = coordinator.rollbackModel()

        assertTrue(rollbackResult.isSuccess)
        assertTrue(fakeEngine.unloadCalled)
        assertEquals(AiCapabilityState.READY, coordinator.state.value)
    }

    @Test
    fun rollbackModel_whenKillSwitchActive_returnsDisabledError() = testScope.runTest {
        coordinator.triggerKillSwitch()

        val result = coordinator.rollbackModel()

        assertTrue(result.isFailure)
        assertEquals(AiError.DisabledByKillSwitch, (result as AiResult.Failure).error)
    }

    @Test
    fun initialize_whenEngineFails_transitionsToErrorAndNeverReportsReady() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        fakeEngine.shouldFailInit = true
        val state = coordinator.initialize()

        assertEquals(AiCapabilityState.ERROR, state)
        assertEquals(AiCapabilityState.ERROR, coordinator.state.value)
    }

    @Test
    fun initialize_whenEngineThrowsUncaughtException_transitionsToError() = testScope.runTest {
        val tempFile = File.createTempFile("test_gemma", ".bin")
        tempFile.deleteOnExit()
        fakeAssetManager.modelFileToReturn = tempFile
        coordinator.enableAi()

        val throwingEngine = object : com.meshlink.app.ai.domain.LocalAiEngine {
            override suspend fun initialize(modelFile: File): AiResult<Unit> = throw IllegalStateException("Native crash")
            override suspend fun generate(prompt: String): AiResult<String> = AiResult.Failure(AiError.RuntimeError("crash"))
            override fun generateStream(prompt: String) = kotlinx.coroutines.flow.emptyFlow<AiResult<String>>()
            override suspend fun unload() {}
            override fun isReady(): Boolean = false
        }

        val testCoordinator = OfflineAiCoordinatorImpl(
            modelAssetManager = fakeAssetManager,
            localAiEngine = throwingEngine,
            dispatcher = testDispatcher
        )
        testCoordinator.enableAi()

        val state = testCoordinator.initialize()
        assertEquals(AiCapabilityState.ERROR, state)
        assertEquals(AiCapabilityState.ERROR, testCoordinator.state.value)
    }

    @Test
    fun activateCandidateAndVerify_whenInitializationFails_rollsBackAutomatically() = testScope.runTest {
        val originalFile = File.createTempFile("original_model", ".bin")
        val candidateFile = File.createTempFile("candidate_model", ".bin")
        fakeAssetManager.modelFileToReturn = originalFile
        val baselineSlot = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "baseline",
            file = originalFile,
            sha256 = "orig_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.ACTIVE
        )
        fakeAssetManager.activeSlotToReturn = baselineSlot
        fakeAssetManager.previousSlotToReturn = baselineSlot.copy(role = com.meshlink.app.ai.domain.ModelSlotRole.PREVIOUS)
        fakeAssetManager.stagedSlotToReturn = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "staged",
            file = candidateFile,
            sha256 = "candidate_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.STAGED
        )
        coordinator.enableAi()

        // Candidate fails initialization, but baseline succeeds on rollback
        fakeEngine.failInitPredicate = { it.name == candidateFile.name }

        val activateResult = coordinator.activateCandidateAndVerify()

        assertTrue("Candidate activation must fail if candidate fails init", activateResult.isFailure)
        assertTrue(fakeEngine.unloadCalled)
        // Automatic rollback occurred: active slot is restored to previous
        assertEquals("baseline", fakeAssetManager.getActiveSlot()?.slotId)
    }

    @Test
    fun activateCandidateAndVerify_whenSmokeTestFails_rollsBackAutomatically() = testScope.runTest {
        val originalFile = File.createTempFile("original_model", ".bin")
        val candidateFile = File.createTempFile("candidate_model", ".bin")
        fakeAssetManager.modelFileToReturn = originalFile
        val baselineSlot = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "baseline",
            file = originalFile,
            sha256 = "orig_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.ACTIVE
        )
        fakeAssetManager.activeSlotToReturn = baselineSlot
        fakeAssetManager.previousSlotToReturn = baselineSlot.copy(role = com.meshlink.app.ai.domain.ModelSlotRole.PREVIOUS)
        fakeAssetManager.stagedSlotToReturn = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "staged",
            file = candidateFile,
            sha256 = "candidate_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.STAGED
        )
        coordinator.enableAi()

        // Mock engine that passes initialization but fails smoke test generation
        val failingGenEngine = object : com.meshlink.app.ai.domain.LocalAiEngine {
            override suspend fun initialize(modelFile: File): AiResult<Unit> = AiResult.Success(Unit)
            override suspend fun generate(prompt: String): AiResult<String> = AiResult.Failure(AiError.RuntimeError("Smoke test OOM"))
            override fun generateStream(prompt: String) = kotlinx.coroutines.flow.emptyFlow<AiResult<String>>()
            override suspend fun unload() {}
            override fun isReady(): Boolean = true
        }

        val testCoordinator = OfflineAiCoordinatorImpl(
            modelAssetManager = fakeAssetManager,
            localAiEngine = failingGenEngine,
            dispatcher = testDispatcher
        )
        testCoordinator.enableAi()

        val result = testCoordinator.activateCandidateAndVerify()
        assertTrue("Activation must fail when smoke test fails", result.isFailure)
        assertEquals("baseline", fakeAssetManager.getActiveSlot()?.slotId)
    }

    @Test
    fun activateCandidateAndVerify_whenSmokeTestSucceeds_activatesSuccessfully() = testScope.runTest {
        val originalFile = File.createTempFile("original_model", ".bin")
        val candidateFile = File.createTempFile("candidate_model", ".bin")
        fakeAssetManager.modelFileToReturn = originalFile
        val baselineSlot = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "baseline",
            file = originalFile,
            sha256 = "orig_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.ACTIVE
        )
        fakeAssetManager.activeSlotToReturn = baselineSlot
        fakeAssetManager.previousSlotToReturn = baselineSlot.copy(role = com.meshlink.app.ai.domain.ModelSlotRole.PREVIOUS)
        fakeAssetManager.stagedSlotToReturn = com.meshlink.app.ai.domain.ModelSlot(
            slotId = "staged",
            file = candidateFile,
            sha256 = "candidate_sha",
            sizeBytes = 100L,
            format = "bin",
            role = com.meshlink.app.ai.domain.ModelSlotRole.STAGED
        )
        coordinator.enableAi()

        fakeEngine.shouldFailInit = false
        val result = coordinator.activateCandidateAndVerify()

        assertTrue("Activation must succeed", result.isSuccess)
        assertEquals(AiCapabilityState.READY, coordinator.state.value)
        assertEquals("slot_active", fakeAssetManager.getActiveSlot()?.slotId)
    }
}
