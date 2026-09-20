package com.meshlink.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlink.app.domain.model.NodeRole
import com.meshlink.app.domain.model.NodeStatus
import com.meshlink.app.mesh.routing.RoutingTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NodeDashboardViewModel @Inject constructor(
    routingTable: RoutingTable
) : ViewModel() {

    val nodeStatuses: StateFlow<Map<String, NodeStatus>> = routingTable.nodeStatusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
}
