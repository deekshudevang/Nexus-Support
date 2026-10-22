package com.meshlink.app.ui.sos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.SosPacketData
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.SosRepository
import com.meshlink.app.location.LocationTracker
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.mesh.transport.SosBeaconAdvertiser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Random
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("localDeviceId") private val myDeviceId: String,
    private val locationTracker: LocationTracker,
    private val meshRouter: MeshRouter,
    private val nearbyRepository: NearbyRepository,
    private val sosRepository: SosRepository,
    private val sosBeaconAdvertiser: SosBeaconAdvertiser,
    private val sosNonceGenerator: com.meshlink.app.mesh.transport.SosNonceGenerator,
    private val aiCoordinator: com.meshlink.app.ai.domain.OfflineAiCoordinator
) : ViewModel() {

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting

    // Truthful GPS fix detection
    private val _gpsFixAvailable = MutableStateFlow(false)
    val gpsFixAvailable: StateFlow<Boolean> = _gpsFixAvailable

    enum class UplinkType { NEARBY_BLE, LORA_RF }
    private val _activeUplink = MutableStateFlow(UplinkType.NEARBY_BLE)
    val activeUplink: StateFlow<UplinkType> = _activeUplink

    enum class IncidentType { MED_EVAC, LOST, GEAR, SECURITY, NONE }
    private val _incidentType = MutableStateFlow(IncidentType.NONE)
    val incidentType: StateFlow<IncidentType> = _incidentType

    private val _includeGps = MutableStateFlow(true)
    val includeGps: StateFlow<Boolean> = _includeGps

    private val _includeMedical = MutableStateFlow(false)
    val includeMedical: StateFlow<Boolean> = _includeMedical

    enum class SosDeliveryState {
        IDLE,
        PENDING,
        TRANSMITTING,
        DELIVERED,
        PEER_ACKNOWLEDGED,
        FAILED,
        EXPIRED
    }

    private val _deliveryStatus = MutableStateFlow(SosDeliveryState.IDLE)
    val deliveryStatus: StateFlow<SosDeliveryState> = _deliveryStatus

    @Volatile private var activeSosMessageId: String? = null
    private var retryJob: Job? = null
    @Volatile private var lastToggleTimestampMs: Long = 0L

    private val random = Random()

    init {
        // Collect incoming packets for SOS notification and peer ACKs
        viewModelScope.launch {
            nearbyRepository.incomingPackets.collect { packet ->
                if (packet.type == MeshPacket.PacketType.SOS) {
                    try {
                        aiCoordinator.onSosActivated()
                    } catch (t: Throwable) {
                        Timber.w(t, "Failed to notify aiCoordinator of incoming SOS; continuing SOS processing")
                    }
                } else if (packet.type == MeshPacket.PacketType.ACK) {
                    val activeId = activeSosMessageId
                    if (activeId != null && packet.content == activeId) {
                        Timber.i("SosViewModel: Received peer ACK for active SOS beacon $activeId")
                        _deliveryStatus.value = SosDeliveryState.PEER_ACKNOWLEDGED
                        retryJob?.cancel()
                        retryJob = null
                        sosRepository.acknowledgeSos(activeId)
                    }
                }
            }
        }

        // Process recreation / app restart recovery: check durable storage for unacknowledged active SOS
        viewModelScope.launch {
            try {
                val active = sosRepository.getActiveLocalSos()
                if (active != null && (active.status == SosPacketData.SosStatus.PENDING || active.status == SosPacketData.SosStatus.SENT)) {
                    Timber.i("SosViewModel: Recovered active unacknowledged SOS from durable storage: uuid=${active.uuid}")
                    activeSosMessageId = active.uuid
                    _isBroadcasting.value = true
                    _deliveryStatus.value = if (active.status == SosPacketData.SosStatus.SENT) SosDeliveryState.TRANSMITTING else SosDeliveryState.PENDING
                    startRetryLoop(active)
                }
            } catch (t: Throwable) {
                Timber.w(t, "SosViewModel: failed to query durable storage for process recovery")
            }
        }

        refreshGpsStatus()
    }

    fun refreshGpsStatus() {
        viewModelScope.launch {
            val location = locationTracker.getCurrentLocation(timeoutMs = 1500)
            _gpsFixAvailable.value = (location != null &&
                !location.latitude.isNaN() && !location.longitude.isNaN() &&
                (location.latitude != 0.0 || location.longitude != 0.0))
        }
    }

    fun setUplink(type: UplinkType) { _activeUplink.value = type }
    fun setIncidentType(type: IncidentType) { _incidentType.value = type }
    fun toggleGps() { _includeGps.value = !_includeGps.value }
    fun toggleMedical() { _includeMedical.value = !_includeMedical.value }

    fun toggleSos() {
        val now = System.currentTimeMillis()
        // Rate-limiting safeguard: debounce rapid clicks within 1000ms
        if (now - lastToggleTimestampMs < 1000L) {
            Timber.w("SosViewModel: Rapid toggle debounced; ignoring duplicate request")
            return
        }
        lastToggleTimestampMs = now

        if (_isBroadcasting.value) {
            cancelSos()
        } else {
            activateSos()
        }
    }

    private fun cancelSos() {
        val currentId = activeSosMessageId
        _isBroadcasting.value = false
        _deliveryStatus.value = SosDeliveryState.IDLE
        activeSosMessageId = null
        retryJob?.cancel()
        retryJob = null

        nearbyRepository.cancelSos()
        sosBeaconAdvertiser.stopAdvertising()
        if (currentId != null) {
            viewModelScope.launch {
                sosRepository.cancelSos(currentId)
            }
        }

        try {
            aiCoordinator.onSosDeactivated()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to notify aiCoordinator of SOS deactivation")
        }
    }

    private fun activateSos() {
        _isBroadcasting.value = true
        _deliveryStatus.value = SosDeliveryState.PENDING
        try {
            aiCoordinator.onSosActivated()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to notify aiCoordinator of SOS activation; continuing SOS broadcast")
        }
        broadcastSos()
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
                message = message.trim(),
                status = SosPacketData.SosStatus.PENDING
            )

            activeSosMessageId = packetData.uuid

            // Persist to durable queue with duplicate suppression
            val enqueued = sosRepository.enqueueSos(packetData)
            if (!enqueued) {
                Timber.w("SosViewModel: SOS with uuid ${packetData.uuid} already enqueued; skipping duplicate")
                return@launch
            }

            startRetryLoop(packetData)
        }
    }

    private fun startRetryLoop(packetData: SosPacketData) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            var attempt = 0
            var delayMs = INITIAL_BACKOFF_MS

            while (_isBroadcasting.value && attempt < MAX_RETRY_ATTEMPTS) {
                attempt++
                try {
                    _deliveryStatus.value = SosDeliveryState.TRANSMITTING
                    sosRepository.updateSosStatus(packetData.uuid, SosPacketData.SosStatus.SENT)

                    // Send via Nearby Mesh and BLE beacon advertising
                    nearbyRepository.sendSos(packetData.toJson())
                    Timber.d("SosViewModel: broadcast attempt $attempt succeeded for ${packetData.uuid}")
                } catch (t: Throwable) {
                    Timber.e(t, "SosViewModel: broadcast attempt $attempt failed for ${packetData.uuid}")
                }

                // If ACK already arrived, exit immediately
                if (_deliveryStatus.value == SosDeliveryState.PEER_ACKNOWLEDGED) {
                    break
                }

                // Bounded exponential backoff with jitter
                val jitter = random.nextInt(500)
                val totalDelay = (delayMs + jitter).coerceAtMost(MAX_BACKOFF_MS)
                delay(totalDelay)
                delayMs = (delayMs * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)
            }

            // If retry exhausted and still transmitting without ACK
            if (_isBroadcasting.value && _deliveryStatus.value == SosDeliveryState.TRANSMITTING && attempt >= MAX_RETRY_ATTEMPTS) {
                Timber.w("SosViewModel: Max retry attempts ($MAX_RETRY_ATTEMPTS) exhausted for ${packetData.uuid}")
                _deliveryStatus.value = SosDeliveryState.FAILED
                sosRepository.updateSosStatus(packetData.uuid, SosPacketData.SosStatus.FAILED)
            }
        }
    }

    companion object {
        const val INITIAL_BACKOFF_MS = 2000L
        const val BACKOFF_MULTIPLIER = 2.0
        const val MAX_BACKOFF_MS = 30000L
        const val MAX_RETRY_ATTEMPTS = 5
    }
}
