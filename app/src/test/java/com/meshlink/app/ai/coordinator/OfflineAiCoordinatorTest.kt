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
}
