package com.meshlink.app.ui.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.location.LocationTracker
import com.meshlink.app.mesh.routing.MeshRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class SosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationTracker: LocationTracker,
    private val meshRouter: MeshRouter,
    private val nearbyRepository: NearbyRepository
) : ViewModel() {

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting

    // New mock state for the Tactical SOS Screen
    private val _satelliteLockStatus = MutableStateFlow("3 SATS [LOCK 3D]")
    val satelliteLockStatus: StateFlow<String> = _satelliteLockStatus

    private val _satelliteSnr = MutableStateFlow(98)
    val satelliteSnr: StateFlow<Int> = _satelliteSnr

    enum class UplinkType { LORA_RF, SATELLITE }
    private val _activeUplink = MutableStateFlow(UplinkType.LORA_RF)
    val activeUplink: StateFlow<UplinkType> = _activeUplink

    enum class IncidentType { MED_EVAC, LOST, GEAR, SECURITY, NONE }
    private val _incidentType = MutableStateFlow(IncidentType.NONE)
    val incidentType: StateFlow<IncidentType> = _incidentType

    private val _includeGps = MutableStateFlow(true)
    val includeGps: StateFlow<Boolean> = _includeGps

    private val _includeMedical = MutableStateFlow(false)
    val includeMedical: StateFlow<Boolean> = _includeMedical

    private val _nextOrbitalWindowSeconds = MutableStateFlow(8)
    val nextOrbitalWindowSeconds: StateFlow<Int> = _nextOrbitalWindowSeconds

    fun setUplink(type: UplinkType) { _activeUplink.value = type }
    fun setIncidentType(type: IncidentType) { _incidentType.value = type }
    fun toggleGps() { _includeGps.value = !_includeGps.value }
    fun toggleMedical() { _includeMedical.value = !_includeMedical.value }

    fun toggleSos() {
        if (_isBroadcasting.value) {
            _isBroadcasting.value = false
        } else {
            _isBroadcasting.value = true
            broadcastSos()
        }
    }

    private fun broadcastSos() {
        viewModelScope.launch {
            val incidentStr = if (_incidentType.value != IncidentType.NONE) "[${_incidentType.value.name}] " else ""
            val initialPayload = "SOS! ${incidentStr}I need help!"
            nearbyRepository.sendSos(initialPayload)

            if (_includeGps.value && _isBroadcasting.value) {
                val location = locationTracker.getCurrentLocation(timeoutMs = 3000)
                if (location != null && _isBroadcasting.value) {
                    val mapsUrl = locationTracker.formatLocationUrl(location)
                    if (location.latitude != 0.0 || location.longitude != 0.0) {
                        val locationPayload = "SOS Location Update: $mapsUrl"
                        nearbyRepository.sendSos(locationPayload)
                    }
                } else if (_isBroadcasting.value) {
                    Timber.w("Failed to get location for SOS")
                }
            }

            if (_includeMedical.value && _isBroadcasting.value) {
                val prefs = context.getSharedPreferences("meshlink_profile", Context.MODE_PRIVATE)
                val bg = prefs.getString("blood_group", "")
                val allergies = prefs.getString("allergies", "")
                val meds = prefs.getString("medications", "")
                
                val builder = java.lang.StringBuilder("SOS Medical Info:\n")
                if (!bg.isNullOrBlank()) builder.append("Blood: $bg\n")
                if (!allergies.isNullOrBlank()) builder.append("Allergies: $allergies\n")
                if (!meds.isNullOrBlank()) builder.append("Meds: $meds\n")
                
                val finalPayload = builder.toString().trim()
                if (finalPayload != "SOS Medical Info:") {
                    nearbyRepository.sendSos(finalPayload)
                }
            }
        }
    }
}
