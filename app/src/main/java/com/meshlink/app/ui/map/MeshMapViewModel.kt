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
        nearbyRepository.graphFlow,
        _hopLimit,
        pendingMessageRepository.getPendingCountFlow()
    ) { connStates, graph, hopLimit, pendingCount ->
        
        val nodes = mutableListOf<MeshNode>()
        val edges = mutableListOf<MeshEdge>()
        
        // Find all connected peers (direct neighbors)
        val connectedEndpoints = connStates.filterValues { it == ConnectionState.CONNECTED }.keys
        val directDeviceIds = mutableSetOf<String>()
        
        for (ep in connectedEndpoints) {
            val peerDeviceId = nearbyRepository.peerDeviceIdForEndpoint(ep) ?: continue
            directDeviceIds.add(peerDeviceId)
            val device = deviceRepository.getDeviceById(peerDeviceId)
            val name = device?.displayName ?: "Unknown"
            
            val link = graph[myDeviceId]?.get(peerDeviceId)
            val battery = link?.battery ?: 100
            
            nodes.add(MeshNode(peerDeviceId, name, battery, isDirect = true, hopCount = 1, latitude = device?.lastLatitude, longitude = device?.lastLongitude))
            edges.add(MeshEdge(myDeviceId, peerDeviceId))
        }

        // Add multi-hop nodes and all edges from the graph
        val allNodesInGraph = graph.keys + graph.values.flatMap { it.keys }
        for (destId in allNodesInGraph.distinct()) {
            if (destId == myDeviceId) continue
            if (directDeviceIds.contains(destId)) continue
            
            val device = deviceRepository.getDeviceById(destId)
            val name = device?.displayName ?: "Unknown"
            
            // We don't have hopCount directly in graphFlow, so we'll just show it as > 1 
            // and use an average battery if available.
            val inboundLinks = graph.values.mapNotNull { it[destId] }
            if (inboundLinks.isNotEmpty()) {
                val battery = inboundLinks.maxOf { it.battery }
                nodes.add(MeshNode(destId, name, battery, isDirect = false, hopCount = 2, latitude = device?.lastLatitude, longitude = device?.lastLongitude))
            }
        }
        
        // Add all edges found in the graph
        for ((sourceId, links) in graph) {
            for ((targetId, _) in links) {
                // To avoid duplicate bidirectional edges, we can enforce order, 
                // but MapView can draw multiple lines. We'll just add them.
                edges.add(MeshEdge(sourceId, targetId))
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
