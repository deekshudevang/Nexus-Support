package com.meshlink.app.ui.settings

import androidx.lifecycle.ViewModel
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.location.LocationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.model.AiCapabilityState
import kotlinx.coroutines.launch

import com.meshlink.app.ai.domain.ModelAssetManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository,
    private val locationTracker: LocationTracker,
    val aiCoordinator: OfflineAiCoordinator,
    val modelAssetManager: ModelAssetManager
) : ViewModel() {

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

    fun triggerAiKillSwitch() {
        viewModelScope.launch {
            aiCoordinator.triggerKillSwitch()
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
            _pendingHashConfirmation.value = null
            _importStatus.value = "Model hash confirmed by user. Initializing..."
            aiCoordinator.initialize()
            _importStatus.value = "Ready: on-device model active"
        }
    }

    fun cancelPendingModelHash() {
        viewModelScope.launch {
            _pendingHashConfirmation.value = null
            modelAssetManager.deleteInstalledModel()
            _importStatus.value = "Model import cancelled"
        }
    }

    fun clearAiData() {
        viewModelScope.launch {
            aiCoordinator.triggerKillSwitch()
            modelAssetManager.deleteInstalledModel()
            _importStatus.value = "AI model and local cache cleared"
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
}
