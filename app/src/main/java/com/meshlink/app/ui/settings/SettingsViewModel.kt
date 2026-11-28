package com.meshlink.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.meshlink.app.data.BuildConfig as DataBuildConfig
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.data.remote.MeshBackendService
import com.meshlink.app.domain.model.SyncTelemetry
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.SettingsRepository
import com.meshlink.app.location.LocationTracker
import com.meshlink.app.sync.GatewaySyncWorker
import com.meshlink.app.worker.CloudSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import kotlinx.coroutines.launch

import com.meshlink.app.ai.domain.ModelAssetManager

import com.meshlink.app.mesh.battery.AdaptiveScanController
import com.meshlink.app.mesh.battery.BatteryMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLException

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val summary: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository,
    private val locationTracker: LocationTracker,
    val aiCoordinator: OfflineAiCoordinator,
    val modelAssetManager: ModelAssetManager,
    private val settingsRepository: SettingsRepository,
    private val adaptiveScanController: AdaptiveScanController,
    private val batteryMonitor: BatteryMonitor,
    private val backendService: MeshBackendService,
    private val sosPacketDao: SosPacketDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val useNearby: StateFlow<Boolean> = settingsRepository.useNearby
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val useMeshtastic: StateFlow<Boolean> = settingsRepository.useMeshtastic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val batteryOptimization: StateFlow<Boolean> = settingsRepository.batteryOptimization
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val crashReportingOptIn: StateFlow<Boolean> = settingsRepository.crashReportingOptIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val backendBaseUrl: StateFlow<String> = settingsRepository.backendBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataBuildConfig.BACKEND_BASE_URL)

    val gatewayKey: StateFlow<String> = settingsRepository.gatewayKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataBuildConfig.GATEWAY_KEY)

    val syncTelemetry: StateFlow<SyncTelemetry> = settingsRepository.syncTelemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncTelemetry())

    val pendingSosCount: StateFlow<Int> = sosPacketDao.getPendingCloudSyncCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    val isAiEnabled: StateFlow<Boolean> = aiCoordinator.isAiEnabled
    val aiState: StateFlow<AiCapabilityState> = aiCoordinator.state

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus

    private val _isMasterScanningPaused = MutableStateFlow(false)
    val isMasterScanningPaused: StateFlow<Boolean> = _isMasterScanningPaused

    fun toggleAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                aiCoordinator.enableAi()
            } else {
                aiCoordinator.triggerKillSwitch()
            }
        }
    }

    private val _pendingHashConfirmation = MutableStateFlow<String?>(null)
    val pendingHashConfirmation: StateFlow<String?> = _pendingHashConfirmation.asStateFlow()

    private val _canRollback = MutableStateFlow(false)
    val canRollback: StateFlow<Boolean> = _canRollback.asStateFlow()

    init {
        refreshRollbackState()
        viewModelScope.launch {
            settingsRepository.batteryOptimization.collect { enabled ->
                val level = batteryMonitor.getBatteryLevel()
                adaptiveScanController.updateSettings(level, enabled)
            }
        }
    }

    fun refreshRollbackState() {
        viewModelScope.launch {
            _canRollback.value = modelAssetManager.getPreviousSlot() != null
        }
    }

    fun triggerAiKillSwitch() {
        viewModelScope.launch {
            aiCoordinator.triggerKillSwitch()
        }
    }

    fun rollbackModel() {
        viewModelScope.launch {
            _importStatus.value = "Rolling back to previous model..."
            val result = aiCoordinator.rollbackModel()
            when (result) {
                is com.meshlink.app.ai.model.AiResult.Success -> {
                    _importStatus.value = "Rollback successful: previous model active"
                    refreshRollbackState()
                }
                is com.meshlink.app.ai.model.AiResult.Failure -> {
                    _importStatus.value = "Rollback failed: ${result.error.userRecoveryText}"
                }
            }
        }
    }

    fun importModel(uri: android.net.Uri, expectedSha256: String = "") {
        viewModelScope.launch {
            _importStatus.value = "Importing model binary..."
            val result = modelAssetManager.importModelFromUri(uri, expectedSha256)
            when (result) {
                is com.meshlink.app.ai.model.AiResult.Success -> {
                    if (expectedSha256.isNotBlank()) {
                        _importStatus.value = "Model verified with pinned SHA-256. Initializing..."
                        aiCoordinator.initialize()
                        _importStatus.value = "Ready: ${result.data.name}"
                        refreshRollbackState()
                    } else {
                        val computed = modelAssetManager.computeSha256(result.data)
                        _pendingHashConfirmation.value = computed
                        _importStatus.value = "Awaiting confirmation for computed hash: $computed"
                    }
                }
                is com.meshlink.app.ai.model.AiResult.Failure -> {
                    _importStatus.value = "Import failed: ${result.error.userRecoveryText}"
                }
            }
        }
    }

    fun confirmPendingModelHash() {
        val hash = _pendingHashConfirmation.value ?: return
        viewModelScope.launch {
            modelAssetManager.confirmModelHash(hash)
            val activateResult = modelAssetManager.activateStagedModel()
            _pendingHashConfirmation.value = null
            if (activateResult.isFailure) {
                _importStatus.value = "Activation failed. Rolled back to previous model."
                refreshRollbackState()
                return@launch
            }
            _importStatus.value = "Model hash confirmed by user. Initializing..."
            val initState = aiCoordinator.initialize()
            if (initState != com.meshlink.app.ai.model.AiCapabilityState.READY) {
                _importStatus.value = "Initialization failed ($initState). Rolling back to previous model..."
                aiCoordinator.rollbackModel()
                _importStatus.value = "Rolled back to previous verified model due to initialization failure."
            } else {
                _importStatus.value = "Ready: on-device model active"
            }
            refreshRollbackState()
        }
    }

    fun cancelPendingModelHash() {
        viewModelScope.launch {
            _pendingHashConfirmation.value = null
            modelAssetManager.deleteInstalledModel()
            _importStatus.value = "Model import cancelled"
            refreshRollbackState()
        }
    }

    fun clearAiData() {
        viewModelScope.launch {
            aiCoordinator.triggerKillSwitch()
            modelAssetManager.deleteInstalledModel()
            _importStatus.value = "AI model and local cache cleared"
            refreshRollbackState()
        }
    }


    fun toggleMasterScanning(paused: Boolean) {
        _isMasterScanningPaused.value = paused
        if (paused) {
            nearbyRepository.pauseScanning()
            locationTracker.setManualPause(true)
        } else {
            nearbyRepository.resumeScanning()
            locationTracker.setManualPause(false)
        }
    }

    fun setUseNearby(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseNearby(enabled)
            if (!enabled) {
                nearbyRepository.pauseScanning()
            } else if (!_isMasterScanningPaused.value) {
                nearbyRepository.resumeScanning()
            }
        }
    }

    fun setUseMeshtastic(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseMeshtastic(enabled)
        }
    }

    fun setBatteryOptimization(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBatteryOptimization(enabled)
            val level = batteryMonitor.getBatteryLevel()
            adaptiveScanController.updateSettings(level, enabled)
        }
    }

    fun setCrashReportingOptIn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashReportingOptIn(enabled)
        }
    }

    fun saveAndTestConnection(urlInput: String, gatewayKeyInput: String) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            try {
                settingsRepository.setBackendBaseUrl(urlInput)
                settingsRepository.setGatewayKey(gatewayKeyInput)
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState.Error(e.message ?: "Invalid backend configuration")
                return@launch
            }

            try {
                val health = backendService.checkHealth()
                val readyResp = backendService.checkReadiness()
                val dbStatus = if (health.database_connected) "DB OK" else "DB Degraded"
                val head = health.migration_head ?: "unknown"
                if (readyResp.isSuccessful) {
                    val lag = readyResp.body()?.outbox_lag ?: 0
                    _connectionTestState.value = ConnectionTestState.Success(
                        "Connected (${health.environment ?: "unknown"}) • $dbStatus • Head $head • Outbox lag: $lag"
                    )
                } else {
                    _connectionTestState.value = ConnectionTestState.Error(
                        "Health OK (${health.status}), readiness returned HTTP ${readyResp.code()}"
                    )
                }
            } catch (e: HttpException) {
                _connectionTestState.value = ConnectionTestState.Error("HTTP ${e.code()}: ${e.message()}")
            } catch (e: SSLException) {
                _connectionTestState.value = ConnectionTestState.Error("TLS error: ${e.message}")
            } catch (e: IOException) {
                _connectionTestState.value = ConnectionTestState.Error("Unreachable: ${e.message ?: e::class.java.simpleName}")
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState.Error(e.message ?: "Connection test failed")
            }
        }
    }

    fun triggerManualSync() {
        try {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniqueWork(
                GatewaySyncWorker.WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                GatewaySyncWorker.buildWorkRequest(pullDownlinks = true)
            )
            wm.enqueue(OneTimeWorkRequestBuilder<CloudSyncWorker>().build())
        } catch (t: Throwable) {
            _connectionTestState.value = ConnectionTestState.Error("Could not schedule sync: ${t.message}")
        }
    }
}
