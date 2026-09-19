package com.meshlink.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiagnosticsUiState(
    val activeConnections: Int = 0,
    val knownNodesCount: Int = 0,
    val totalLinks: Int = 0,
    val pendingMessagesCount: Int = 0,
    val scanStrategyName: String = "UNKNOWN"
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val nearbyRepository: NearbyRepository,
    private val pendingMessageRepository: PendingMessageRepository
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        nearbyRepository.connectionStates,
        nearbyRepository.graphFlow,
        nearbyRepository.scanStrategy,
        pendingMessageRepository.getPendingCountFlow()
    ) { connStates, graph, scanStrategy, pendingCount ->

        val activeConns = connStates.count { it.value == ConnectionState.CONNECTED }
        val nodes = (graph.keys + graph.values.flatMap { it.keys }).distinct().size
        val links = graph.values.sumOf { it.size }

        DiagnosticsUiState(
            activeConnections = activeConns,
            knownNodesCount = nodes,
            totalLinks = links,
            pendingMessagesCount = pendingCount,
            scanStrategyName = scanStrategy.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiagnosticsUiState()
    )
}
