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
import com.meshlink.app.domain.model.SosPacketData
import com.meshlink.app.mesh.transport.SosBeaconAdvertiser
import com.meshlink.app.mesh.transport.SosBeaconCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Named

@HiltViewModel
class SosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("localDeviceId") private val myDeviceId: String,
    private val locationTracker: LocationTracker,
    private val meshRouter: MeshRouter,
    private val nearbyRepository: NearbyRepository,
    private val sosBeaconAdvertiser: SosBeaconAdvertiser
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
            sosBeaconAdvertiser.stopAdvertising()
        } else {
            _isBroadcasting.value = true
            broadcastSos()
        }
    }

    private fun broadcastSos() {
        viewModelScope.launch {
            val incidentStr = if (_incidentType.value != IncidentType.NONE) "[${_incidentType.value.name}] " else ""
            var message = "SOS! ${incidentStr}I need help!"

            var lat = 0.0
            var lon = 0.0

            if (_includeGps.value) {
                val location = locationTracker.getCurrentLocation(timeoutMs = 3000)
                if (location != null && _isBroadcasting.value) {
                    lat = location.latitude
                    lon = location.longitude
                    val mapsUrl = locationTracker.formatLocationUrl(location)
                    if (lat != 0.0 || lon != 0.0) {
                        message += "\nLocation: $mapsUrl"
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
                
                val builder = java.lang.StringBuilder("\nMedical Info:\n")
                if (!bg.isNullOrBlank()) builder.append("Blood: $bg\n")
                if (!allergies.isNullOrBlank()) builder.append("Allergies: $allergies\n")
                if (!meds.isNullOrBlank()) builder.append("Meds: $meds\n")
                
                if (builder.toString() != "\nMedical Info:\n") {
                    message += builder.toString()
                }
            }

            val eType = when (_incidentType.value) {
                IncidentType.MED_EVAC -> SosPacketData.EmergencyType.MEDICAL
                IncidentType.LOST -> SosPacketData.EmergencyType.LOST
                IncidentType.GEAR -> SosPacketData.EmergencyType.OTHER
                IncidentType.SECURITY -> SosPacketData.EmergencyType.SECURITY
                IncidentType.NONE -> SosPacketData.EmergencyType.OTHER
            }

            val packetData = SosPacketData(
                senderId = myDeviceId,
                emergencyType = eType,
                severity = 5,
                latitude = lat,
                longitude = lon,
                message = message.trim()
            )

            // Send via Nearby Mesh (Phase 3 structured SOS)
            nearbyRepository.sendSos(packetData.toJson())

            // Start BLE beacon advertising (Phase 2 compact SOS)
            val beaconEType = when (_incidentType.value) {
                IncidentType.MED_EVAC -> SosBeaconCodec.EmergencyType.MEDICAL
                IncidentType.LOST -> SosBeaconCodec.EmergencyType.LOST
                IncidentType.GEAR -> SosBeaconCodec.EmergencyType.OTHER
                IncidentType.SECURITY -> SosBeaconCodec.EmergencyType.SECURITY
                IncidentType.NONE -> SosBeaconCodec.EmergencyType.OTHER
            }
            val beaconPayload = SosBeaconCodec.encode(
                deviceId = myDeviceId,
                latitude = lat,
                longitude = lon,
                emergencyType = beaconEType,
                severity = 5
            )
            sosBeaconAdvertiser.startAdvertising(beaconPayload)
        }
    }
}
