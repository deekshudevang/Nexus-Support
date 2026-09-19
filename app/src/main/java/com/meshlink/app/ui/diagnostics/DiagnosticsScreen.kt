package com.meshlink.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Network Health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            DiagnosticCard(
                title = "Active Connections",
                value = uiState.activeConnections.toString(),
                icon = Icons.Filled.WifiTethering,
                color = if (uiState.activeConnections > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            )
            
            DiagnosticCard(
                title = "Total Known Nodes",
                value = uiState.knownNodesCount.toString(),
                icon = Icons.Filled.NetworkCheck,
                color = MaterialTheme.colorScheme.secondary
            )

            DiagnosticCard(
                title = "Topology Edges",
                value = uiState.totalLinks.toString(),
                icon = Icons.Filled.Info,
                color = MaterialTheme.colorScheme.secondary
            )
            
            DiagnosticCard(
                title = "Store & Forward Queue",
                value = uiState.pendingMessagesCount.toString(),
                icon = Icons.Filled.Storage,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Hardware Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            DiagnosticCard(
                title = "Radio Scan Strategy",
                value = uiState.scanStrategyName,
                icon = Icons.Filled.WifiTethering,
                color = if (uiState.scanStrategyName == "PAUSED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun DiagnosticCard(title: String, value: String, icon: ImageVector, color: Color) {
    com.meshlink.app.ui.components.TacticalCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
