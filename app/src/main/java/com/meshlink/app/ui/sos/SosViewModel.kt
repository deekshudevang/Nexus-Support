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
    private val sosBeaconAdvertiser: SosBeaconAdvertiser,
    private val sosNonceGenerator: com.meshlink.app.mesh.transport.SosNonceGenerator,
    private val aiCoordinator: com.meshlink.app.ai.domain.OfflineAiCoordinator
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

    enum class SosDeliveryState {
        IDLE,
        TRANSMITTING,
        PEER_ACKNOWLEDGED
    }

    private val _deliveryStatus = MutableStateFlow(SosDeliveryState.IDLE)
    val deliveryStatus: StateFlow<SosDeliveryState> = _deliveryStatus

    @Volatile private var activeSosMessageId: String? = null

    init {
        viewModelScope.launch {
            nearbyRepository.incomingPackets.collect { packet ->
                if (packet.type == com.meshlink.app.domain.model.MeshPacket.PacketType.SOS) {
                    try {
                        aiCoordinator.onSosActivated()
                    } catch (t: Throwable) {
                        Timber.w(t, "Failed to notify aiCoordinator of incoming SOS; continuing SOS processing")
                    }
                } else if (packet.type == com.meshlink.app.domain.model.MeshPacket.PacketType.ACK) {
                    val activeId = activeSosMessageId
                    if (activeId != null && packet.content == activeId) {
                        Timber.i("SosViewModel: Received peer ACK for active SOS beacon $activeId")
                        _deliveryStatus.value = SosDeliveryState.PEER_ACKNOWLEDGED
                    }
                }
            }
        }
    }

    fun setUplink(type: UplinkType) { _activeUplink.value = type }
    fun setIncidentType(type: IncidentType) { _incidentType.value = type }
    fun toggleGps() { _includeGps.value = !_includeGps.value }
    fun toggleMedical() { _includeMedical.value = !_includeMedical.value }

    fun toggleSos() {
        if (_isBroadcasting.value) {
            _isBroadcasting.value = false
            _deliveryStatus.value = SosDeliveryState.IDLE
            activeSosMessageId = null
            nearbyRepository.cancelSos()
            sosBeaconAdvertiser.stopAdvertising()
            try {
                aiCoordinator.onSosDeactivated()
            } catch (t: Throwable) {
                Timber.w(t, "Failed to notify aiCoordinator of SOS deactivation")
            }
        } else {
            _isBroadcasting.value = true
            _deliveryStatus.value = SosDeliveryState.TRANSMITTING
            try {
                aiCoordinator.onSosActivated()
            } catch (t: Throwable) {
                Timber.w(t, "Failed to notify aiCoordinator of SOS activation; continuing SOS broadcast")
            }
            broadcastSos()
        }
    }

    private fun broadcastSos() {
        viewModelScope.launch {
            val incidentStr = if (_incidentType.value != IncidentType.NONE) "[${_incidentType.value.name}] " else ""
            var message = "SOS! ${incidentStr}I need help!"

            var lat: Double? = null
            var lon: Double? = null

            if (_includeGps.value) {
                val location = locationTracker.getCurrentLocation(timeoutMs = 3000)
                if (location != null && _isBroadcasting.value) {
                    val candidateLat = location.latitude
                    val candidateLon = location.longitude
                    if (!candidateLat.isNaN() && !candidateLon.isNaN() &&
                        candidateLat in -90.0..90.0 && candidateLon in -180.0..180.0 &&
                        (candidateLat != 0.0 || candidateLon != 0.0)) {
                        lat = candidateLat
                        lon = candidateLon
                        val mapsUrl = locationTracker.formatLocationUrl(location)
                        message += "\nLocation: $mapsUrl"
                    } else {
                        message += "\nLocation: unavailable"
                    }
                } else if (_isBroadcasting.value) {
                    Timber.w("Failed to get location for SOS")
                    message += "\nLocation: unavailable"
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

            activeSosMessageId = packetData.uuid

            // Send via Nearby Mesh (nearbyRepository.sendSos also starts authoritative BLE beacon advertising)
            nearbyRepository.sendSos(packetData.toJson())
        }
    }
}
