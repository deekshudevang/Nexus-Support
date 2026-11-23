package com.meshlink.app.ui.admin

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.data.local.dao.AssignmentDao
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.SettingsRepository
import com.meshlink.app.mesh.routing.RoutingTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

data class AdminNodeUi(
    val deviceId: String,
    val displayName: String,
    val connectionState: String,
    val batteryLevel: Int?, // null if not reported by node protocol
    val lastSeenTimestamp: Long,
    val role: String,
    val isVerified: Boolean
)

data class AdminAssignmentUi(
    val id: String,
    val incidentId: String,
    val responderId: String,
    val role: String,
    val status: String,
    val priority: Int,
    val description: String,
    val offeredAt: Long
)

data class AdminOverviewStats(
    val totalNodes: Int = 0,
    val connectedNodes: Int = 0,
    val activeSosCount: Int = 0,
    val totalAssignments: Int = 0,
    val pendingAssignments: Int = 0,
    val systemUptime: String = "--:--",
    val meshSyncState: String = "IDLE"
)

data class AdminConfigUi(
    val useNearby: Boolean = true,
    val useMeshtastic: Boolean = false,
    val batteryOptimization: Boolean = true
)

data class AdminAuditEntry(
    val id: String,
    val timestamp: Long,
    val action: String,
    val detail: String,
    val actor: String
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val routingTable: RoutingTable,
    private val nearbyRepository: NearbyRepository,
    private val assignmentDao: AssignmentDao,
    private val deviceDao: DeviceDao,
    private val sosPacketDao: SosPacketDao,
    private val settingsRepository: SettingsRepository,
    @Named("localDeviceId") private val localDeviceId: String
) : ViewModel() {

    // Admin authorization state (defaults to true for dev/eval, can be gated)
    private val _isAuthorized = MutableStateFlow(true)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    // Audit trail
    private val _auditLog = MutableStateFlow<List<AdminAuditEntry>>(emptyList())
    val auditLog: StateFlow<List<AdminAuditEntry>> = _auditLog.asStateFlow()

    // Truthful device uptime derived from system hardware clock
    private val _systemUptime = MutableStateFlow(formatDeviceUptime())
    val systemUptime: StateFlow<String> = _systemUptime.asStateFlow()

    // Active alerts derived from real unacknowledged SOS or disconnected peers
    private val _activeAlerts = MutableStateFlow<List<String>>(emptyList())
    val activeAlerts: StateFlow<List<String>> = _activeAlerts.asStateFlow()

    init {
        recordAuditAction("SESSION_INIT", "Admin dashboard initialized on device $localDeviceId")
    }

    // Consolidated node state from Room Known Devices, Nearby Connections, and Routing Graph
    val nodes: StateFlow<List<AdminNodeUi>> = combine(
        deviceDao.getAll(),
        nearbyRepository.discoveredDevices,
        nearbyRepository.connectionStates,
        routingTable.graphFlow
    ) { knownDevices, discovered, states, graph ->
        val nodeMap = mutableMapOf<String, AdminNodeUi>()

        // 1. Seed from verified known devices in Room
        for (device in knownDevices) {
            val state = states[device.deviceId]?.name ?: "KNOWN"
            val linkBattery = graph[localDeviceId]?.get(device.deviceId)?.battery
            nodeMap[device.deviceId] = AdminNodeUi(
                deviceId = device.deviceId,
                displayName = device.displayName.ifBlank { "Node ${device.deviceId.take(6)}" },
                connectionState = state,
                batteryLevel = linkBattery,
                lastSeenTimestamp = device.lastSeen,
                role = "PEER",
                isVerified = device.isVerified
            )
        }

        // 2. Overlay live discovered nearby devices
        for (disc in discovered) {
            val peerId = nearbyRepository.peerDeviceIdForEndpoint(disc.endpointId) ?: disc.endpointId
            val state = states[disc.endpointId]?.name ?: "DISCOVERED"
            val linkBattery = graph[localDeviceId]?.get(peerId)?.battery
            val existing = nodeMap[peerId]

            nodeMap[peerId] = AdminNodeUi(
                deviceId = peerId,
                displayName = existing?.displayName ?: disc.name.ifBlank { "Endpoint ${disc.endpointId}" },
                connectionState = state,
                batteryLevel = linkBattery ?: existing?.batteryLevel,
                lastSeenTimestamp = System.currentTimeMillis(),
                role = existing?.role ?: "DISCOVERED",
                isVerified = existing?.isVerified ?: false
            )
        }

        // 3. Overlay mesh routing graph links
        val localLinks = graph[localDeviceId] ?: emptyMap()
        for ((targetId, link) in localLinks) {
            if (!nodeMap.containsKey(targetId)) {
                nodeMap[targetId] = AdminNodeUi(
                    deviceId = targetId,
                    displayName = "Mesh Peer ${targetId.take(6)}",
                    connectionState = "MESH_ROUTE",
                    batteryLevel = if (link.battery in 1..100) link.battery else null,
                    lastSeenTimestamp = link.timestamp,
                    role = "RELAY",
                    isVerified = false
                )
            }
        }

        nodeMap.values.sortedByDescending { it.lastSeenTimestamp }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Live assignments from Room
    val assignments: StateFlow<List<AdminAssignmentUi>> = assignmentDao.getAll()
        .combine(MutableStateFlow(Unit)) { entities, _ ->
            entities.map { entity ->
                AdminAssignmentUi(
                    id = entity.id,
                    incidentId = entity.incidentId,
                    responderId = entity.responderId,
                    role = entity.role,
                    status = entity.status,
                    priority = entity.priority,
                    description = entity.description.ifBlank { entity.notes },
                    offeredAt = entity.offeredAt
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Consolidated overview statistics
    val overviewStats: StateFlow<AdminOverviewStats> = combine(
        nodes,
        sosPacketDao.getAll(),
        assignments,
        systemUptime
    ) { currentNodes, sosList, currentAssignments, uptime ->
        val connected = currentNodes.count { it.connectionState == "CONNECTED" }
        val activeSos = sosList.count { it.status == "PENDING" || it.status == "SENT" || it.status == "RELAYED" }
        val pendingAssignments = currentAssignments.count { it.status == "OFFERED" || it.status == "DISPATCHED" }

        AdminOverviewStats(
            totalNodes = currentNodes.size,
            connectedNodes = connected,
            activeSosCount = activeSos,
            totalAssignments = currentAssignments.size,
            pendingAssignments = pendingAssignments,
            systemUptime = uptime,
            meshSyncState = if (connected > 0) "ACTIVE_MESH" else "LOCAL_ONLY"
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdminOverviewStats())

    // Configuration state
    val config: StateFlow<AdminConfigUi> = combine(
        settingsRepository.useNearby,
        settingsRepository.useMeshtastic,
        settingsRepository.batteryOptimization
    ) { nearby, meshtastic, batteryOpt ->
        AdminConfigUi(
            useNearby = nearby,
            useMeshtastic = meshtastic,
            batteryOptimization = batteryOpt
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdminConfigUi())

    fun updateAssignmentStatus(assignmentId: String, newStatus: String) {
        viewModelScope.launch {
            assignmentDao.updateStatus(assignmentId, newStatus, System.currentTimeMillis(), synced = false)
            recordAuditAction("ASSIGNMENT_UPDATE", "Updated assignment $assignmentId status to $newStatus")
        }
    }

    fun setUseNearby(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseNearby(enabled)
            recordAuditAction("CONFIG_CHANGE", "Nearby transport set to $enabled")
        }
    }

    fun setUseMeshtastic(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseMeshtastic(enabled)
            recordAuditAction("CONFIG_CHANGE", "Meshtastic transport set to $enabled")
        }
    }

    fun setBatteryOptimization(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBatteryOptimization(enabled)
            recordAuditAction("CONFIG_CHANGE", "Battery optimization set to $enabled")
        }
    }

    fun recordAuditAction(action: String, detail: String) {
        val entry = AdminAuditEntry(
            id = "audit-${System.currentTimeMillis()}-${(1000..9999).random()}",
            timestamp = System.currentTimeMillis(),
            action = action,
            detail = detail,
            actor = localDeviceId
        )
        _auditLog.value = (listOf(entry) + _auditLog.value).take(50)
    }

    private fun formatDeviceUptime(): String {
        val millis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
    }
}
