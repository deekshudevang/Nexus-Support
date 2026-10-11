package com.meshlink.app.ui.settings

import androidx.lifecycle.ViewModel
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.location.LocationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
                    _importStatus.value = "Model imported successfully. Verifying..."
                    aiCoordinator.initialize()
                    _importStatus.value = "Ready: ${result.data.name}"
                }
                is com.meshlink.app.ai.model.AiResult.Failure -> {
                    _importStatus.value = "Import failed: ${result.error.userRecoveryText}"
                }
            }
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
