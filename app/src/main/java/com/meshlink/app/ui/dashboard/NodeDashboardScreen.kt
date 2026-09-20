package com.meshlink.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshlink.app.domain.model.NodeRole
import com.meshlink.app.domain.model.NodeStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDashboardScreen(
    viewModel: NodeDashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val nodeStatuses by viewModel.nodeStatuses.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Node Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (nodeStatuses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No node statuses received yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(nodeStatuses.values.toList().sortedByDescending { it.timestamp }) { status ->
                    NodeStatusCard(status)
                }
            }
        }
    }
}

@Composable
fun NodeStatusCard(status: NodeStatus) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = timeFormat.format(Date(status.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = "User")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status.displayName.ifEmpty { "Unknown (${status.deviceId.take(4)})" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = status.role.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Battery: ${status.batteryLevel}%", style = MaterialTheme.typography.bodyMedium)
                Text("Group size: ${status.personCount}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Needs Help: ${if (status.needsHelp) "YES" else "No"}", style = MaterialTheme.typography.bodyMedium)
                Text("Last update: $formattedTime", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Resources: " + buildString {
                if (status.hasWater) append("Water ")
                if (status.hasFood) append("Food ")
                if (status.hasMedKit) append("MedKit ")
                if (isEmpty()) append("None reported")
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
