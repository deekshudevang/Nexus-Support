package com.meshlink.app.ui.settings

import androidx.lifecycle.ViewModel
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.location.LocationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _isMasterScanningPaused = MutableStateFlow(false)
    val isMasterScanningPaused: StateFlow<Boolean> = _isMasterScanningPaused

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
