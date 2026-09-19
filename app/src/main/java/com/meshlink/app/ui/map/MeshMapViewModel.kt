package com.meshlink.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.model.DomainRouteEntry
import com.meshlink.app.domain.repository.UserProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MeshNode(
    val id: String,
    val name: String,
    val batteryLevel: Int,
    val isDirect: Boolean,
    val hopCount: Int,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class MeshEdge(
    val sourceId: String,
    val targetId: String
)

data class MeshMapUiState(
    val myName: String = "",
    val nodes: List<MeshNode> = emptyList(),
    val edges: List<MeshEdge> = emptyList(),
    val pendingMessagesCount: Int = 0,
    val currentHopLimit: Int = 7
)

@HiltViewModel
class MeshMapViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository,
    private val deviceRepository: DeviceRepository,
    private val pendingMessageRepository: PendingMessageRepository,
    private val userProfileManager: UserProfileManager
) : ViewModel() {

    private val myDeviceId = "ME"
    private val myName = userProfileManager.getDisplayName()

    private val _hopLimit = kotlinx.coroutines.flow.MutableStateFlow(7)

    fun setHopLimit(limit: Int) {
        _hopLimit.value = limit
    }

    val uiState: StateFlow<MeshMapUiState> = combine(
        nearbyRepository.connectionStates,
        nearbyRepository.routesFlow,
        _hopLimit,
        pendingMessageRepository.getPendingCountFlow()
    ) { connStates, routes, hopLimit, pendingCount ->
        
        val nodes = mutableListOf<MeshNode>()
        val edges = mutableListOf<MeshEdge>()
        
        // Find all connected peers (direct neighbors)
        val connectedEndpoints = connStates.filterValues { it == ConnectionState.CONNECTED }.keys
        val directDeviceIds = mutableSetOf<String>()
        
        // Direct nodes have hopCount = 1
        if (hopLimit >= 1) {
            for (ep in connectedEndpoints) {
                val peerDeviceId = nearbyRepository.peerDeviceIdForEndpoint(ep) ?: continue
                directDeviceIds.add(peerDeviceId)
                val device = deviceRepository.getDeviceById(peerDeviceId)
                val name = device?.displayName ?: "Unknown"
                
                // For battery, we can look up the route entry if we have one, else default to 100
                val route = routes[peerDeviceId]?.firstOrNull { it.nextHopEndpointId == ep }
                val battery = route?.batteryLevel ?: 100
                
                nodes.add(MeshNode(peerDeviceId, name, battery, isDirect = true, hopCount = 1, latitude = device?.lastLatitude, longitude = device?.lastLongitude))
                
                // Edge from Me -> Direct Neighbor
                edges.add(MeshEdge(myDeviceId, peerDeviceId))
            }
        }

        // Add multi-hop nodes from routes flow
        for ((destId, routeEntries) in routes) {
            if (destId == myDeviceId) continue
            if (directDeviceIds.contains(destId)) continue
            
            val bestRoute = routeEntries.maxByOrNull { it.batteryLevel - (it.hopCount * 10) }
                ?: continue
                
            if (bestRoute.hopCount > hopLimit) continue
                
            val device = deviceRepository.getDeviceById(destId)
            val name = device?.displayName ?: "Unknown"
            nodes.add(MeshNode(destId, name, bestRoute.batteryLevel, isDirect = false, hopCount = bestRoute.hopCount, latitude = device?.lastLatitude, longitude = device?.lastLongitude))
            
            // To figure out the actual relay, we need the stable deviceId of the nextHopEndpointId
            val relayDeviceId = nearbyRepository.peerDeviceIdForEndpoint(bestRoute.nextHopEndpointId)
            if (relayDeviceId != null) {
                edges.add(MeshEdge(relayDeviceId, destId))
            }
        }

        MeshMapUiState(
            myName = myName,
            nodes = nodes.distinctBy { it.id },
            edges = edges,
            pendingMessagesCount = pendingCount,
            currentHopLimit = hopLimit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MeshMapUiState(myName = myName)
    )
}
