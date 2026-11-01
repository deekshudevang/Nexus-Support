package com.meshlink.app.ai.coordinator

import android.content.ComponentCallbacks2
import android.content.Context
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import com.meshlink.app.ai.security.AiLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAiCoordinatorImpl(
    private val modelAssetManager: ModelAssetManager,
    private val localAiEngine: LocalAiEngine,
    private val dispatcher: CoroutineDispatcher,
    private val context: Context? = null
) : OfflineAiCoordinator {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        modelAssetManager: ModelAssetManager,
        localAiEngine: LocalAiEngine
    ) : this(modelAssetManager, localAiEngine, Dispatchers.Default, context)

    constructor(
        modelAssetManager: ModelAssetManager,
        localAiEngine: LocalAiEngine,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(modelAssetManager, localAiEngine, dispatcher, null)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(AiCapabilityState.NOT_INSTALLED)
    override val state: StateFlow<AiCapabilityState> = _state.asStateFlow()

    // Default off per strict safety rules, unless previously enabled by user
    private val _isAiEnabled = MutableStateFlow(false)
    override val isAiEnabled: StateFlow<Boolean> = _isAiEnabled.asStateFlow()

    init {
        if (context != null) {
            val prefs = context.getSharedPreferences("offline_ai_prefs", Context.MODE_PRIVATE)
            val savedEnabled = prefs.getBoolean("ai_enabled", false)
            if (savedEnabled) {
                _isAiEnabled.value = true
            }
        }
    }

    private val isSosActive = AtomicBoolean(false)
    private val killSwitchActive = AtomicBoolean(false)

    private val inferenceMutex = Mutex()
    private var activeInferenceJob: Job? = null

    override suspend fun initialize(): AiCapabilityState = withContext(dispatcher) {
        if (killSwitchActive.get()) {
            _state.value = AiCapabilityState.ERROR
            return@withContext AiCapabilityState.ERROR
        }

        _state.value = AiCapabilityState.VERIFYING
        val modelFile = modelAssetManager.getVerifiedModelFile()
        if (modelFile == null || !modelFile.exists()) {
            AiLogger.i("No verified model found in storage. Transitioning to NOT_INSTALLED.")
            _state.value = AiCapabilityState.NOT_INSTALLED
            return@withContext AiCapabilityState.NOT_INSTALLED
        }

        if (!_isAiEnabled.value) {
            // Model exists but feature flag is off
            _state.value = AiCapabilityState.READY
            return@withContext AiCapabilityState.READY
        }

        _state.value = AiCapabilityState.LOADING
        val initResult = try {
            localAiEngine.initialize(modelFile)
        } catch (e: Throwable) {
            AiLogger.e(e, "Uncaught error during LocalAiEngine initialization")
            AiResult.Failure(AiError.RuntimeError(e.message ?: "Initialization crashed"))
        }

        if (initResult.isSuccess) {
            AiLogger.i("LocalAiEngine initialized successfully. State: READY")
            _state.value = AiCapabilityState.READY
        } else {
            AiLogger.w("LocalAiEngine initialization failed: ${(initResult as AiResult.Failure).error}")
            _state.value = AiCapabilityState.ERROR
        }
        _state.value
    }

    override suspend fun triggerKillSwitch(): Unit = withContext(dispatcher) {
        AiLogger.w("EMERGENCY KILL SWITCH TRIGGERED: Disabling AI and releasing all resources")
        killSwitchActive.set(true)
        _isAiEnabled.value = false
        context?.getSharedPreferences("offline_ai_prefs", Context.MODE_PRIVATE)?.edit()?.putBoolean("ai_enabled", false)?.apply()

        // Cancel running work immediately
        activeInferenceJob?.cancel()
        activeInferenceJob = null

        localAiEngine.unload()
        _state.value = AiCapabilityState.NOT_INSTALLED
    }

    override suspend fun enableAi(): Unit = withContext(dispatcher) {
        killSwitchActive.set(false)
        _isAiEnabled.value = true
        context?.getSharedPreferences("offline_ai_prefs", Context.MODE_PRIVATE)?.edit()?.putBoolean("ai_enabled", true)?.apply()
        initialize()
        Unit
    }

    override suspend fun rollbackModel(): AiResult<Unit> = withContext(dispatcher) {
        if (killSwitchActive.get()) {
            return@withContext AiResult.Failure(AiError.DisabledByKillSwitch)
        }

        inferenceMutex.withLock {
            AiLogger.i("Starting non-destructive model rollback to retained previous slot")
            activeInferenceJob?.cancel()
            activeInferenceJob = null
            localAiEngine.unload()

            val rollbackResult = modelAssetManager.rollbackToPrevious()
            if (rollbackResult.isFailure) {
                AiLogger.w("Rollback failed: ${(rollbackResult as AiResult.Failure).error}")
                initialize()
                return@withContext AiResult.Failure(rollbackResult.error)
            }

            AiLogger.i("Slot rollback successful on disk. Initializing restored model...")
            val initState = initialize()
            if (initState == AiCapabilityState.READY) {
                AiResult.Success(Unit)
            } else {
                AiResult.Failure(AiError.RuntimeError("Failed to initialize restored model (state=$initState)"))
            }
        }
    }

    override suspend fun activateCandidateAndVerify(): AiResult<Unit> = withContext(dispatcher) {
        if (killSwitchActive.get()) {
            return@withContext AiResult.Failure(AiError.DisabledByKillSwitch)
        }

        inferenceMutex.withLock {
            AiLogger.i("Starting candidate model activation and verification...")
            activeInferenceJob?.cancel()
            activeInferenceJob = null
            localAiEngine.unload()

            val activateResult = modelAssetManager.activateStagedModel()
            if (activateResult.isFailure) {
                val err = (activateResult as AiResult.Failure).error
                AiLogger.w("Candidate activation failed: $err")
                initialize()
                return@withContext AiResult.Failure(err)
            }

            // Attempt initialization of candidate model
            val initState = initialize()
            if (initState != AiCapabilityState.READY) {
                AiLogger.w("Candidate model failed initialization (state=$initState). Triggering automatic rollback.")
                modelAssetManager.rollbackToPrevious()
                initialize()
                return@withContext AiResult.Failure(
                    AiError.RuntimeError("Candidate model failed initialization. Rolled back to previous model.")
                )
            }

            // Run mandatory smoke test
            val smokeTestResult = try {
                localAiEngine.generate("test smoke prompt")
            } catch (e: Throwable) {
                AiResult.Failure(AiError.RuntimeError(e.message ?: "Smoke test crashed"))
            }

            if (smokeTestResult.isFailure) {
                AiLogger.w("Candidate model failed mandatory smoke test. Triggering automatic rollback.")
                localAiEngine.unload()
                modelAssetManager.rollbackToPrevious()
                initialize()
                return@withContext AiResult.Failure(
                    AiError.RuntimeError("Candidate model failed mandatory smoke test. Rolled back to previous model.")
                )
            }

            AiLogger.i("Candidate model passed initialization and mandatory smoke test. Activation verified.")
            AiResult.Success(Unit)
        }
    }

    override fun onSosActivated() {

        AiLogger.w("SOS ACTIVATED: Immediately pre-empting all AI inference tasks")
        isSosActive.set(true)
        activeInferenceJob?.cancel()
        activeInferenceJob = null
    }

    override fun onSosDeactivated() {
        AiLogger.i("SOS Deactivated. Resuming normal AI coordinator state.")
        isSosActive.set(false)
    }

    @Suppress("DEPRECATION")
    override suspend fun onTrimMemory(level: Int): Unit = withContext(dispatcher) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AiLogger.w("System memory pressure detected (level=$level). Unloading AI model to protect host app.")
            activeInferenceJob?.cancel()
            localAiEngine.unload()
            _state.value = AiCapabilityState.LOW_MEMORY
        }
    }

    override suspend fun <T> executeInference(
        timeoutMs: Long,
        block: suspend () -> AiResult<T>
    ): AiResult<T> = withContext(dispatcher) {
        if (killSwitchActive.get() || !_isAiEnabled.value) {
            return@withContext AiResult.Failure(AiError.DisabledByKillSwitch)
        }

        if (isSosActive.get()) {
            return@withContext AiResult.Failure(AiError.SosPriorityPreempted)
        }

        if (_state.value == AiCapabilityState.LOW_MEMORY) {
            return@withContext AiResult.Failure(AiError.LowMemory)
        }

        if (_state.value == AiCapabilityState.NOT_INSTALLED) {
            return@withContext AiResult.Failure(AiError.ModelMissing)
        }

        // Single-flight lock
        inferenceMutex.withLock {
            if (isSosActive.get()) {
                return@withContext AiResult.Failure(AiError.SosPriorityPreempted)
            }

            _state.value = AiCapabilityState.RUNNING
            val inferenceJob = Job()
            activeInferenceJob = inferenceJob
            try {
                val result = withContext(inferenceJob) {
                    withTimeoutOrNull(timeoutMs) {
                        block()
                    }
                }

                if (result == null) {
                    AiLogger.w("Inference timed out after ${timeoutMs}ms")
                    _state.value = AiCapabilityState.READY
                    return@withContext AiResult.Failure(AiError.Timeout(timeoutMs))
                }

                _state.value = AiCapabilityState.READY
                result
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = AiCapabilityState.READY
                if (isSosActive.get()) {
                    AiResult.Failure(AiError.SosPriorityPreempted)
                } else {
                    AiResult.Failure(AiError.Cancelled)
                }
            } catch (e: Exception) {
                AiLogger.e(e, "Unexpected error during inference execution")
                _state.value = AiCapabilityState.ERROR
                AiResult.Failure(AiError.RuntimeError(e.message ?: "Unknown error"))
            } finally {
                inferenceJob.complete()
                activeInferenceJob = null
                if (_state.value == AiCapabilityState.RUNNING) {
                    _state.value = AiCapabilityState.READY
                }
            }
        }
    }
}
