package com.meshlink.app.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshlink.app.map.DownloadState
import com.meshlink.app.map.MapRegion
import com.meshlink.app.ui.components.NexusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDownloadScreen(
    onNavigateBack: () -> Unit,
    viewModel: MapDownloadViewModel = hiltViewModel()
) {
    val downloadStates by viewModel.downloadStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Maps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Download Mapsforge vector maps for complete offline routing and rendering.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.availableRegions) { region ->
                    val state = downloadStates[region.id] ?: if (viewModel.isDownloaded(region.id)) DownloadState.Success else DownloadState.Idle
                    MapRegionItem(
                        region = region,
                        state = state,
                        onDownloadClick = { viewModel.startDownload(region) }
                    )
                }
            }
        }
    }
}

@Composable
fun MapRegionItem(
    region: MapRegion,
    state: DownloadState,
    onDownloadClick: () -> Unit
) {
    NexusCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = region.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatFileSize(region.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                when (state) {
                    is DownloadState.Idle, is DownloadState.Error -> {
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DownloadState.Downloading -> {
                        CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                    is DownloadState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            if (state is DownloadState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (state is DownloadState.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024).toDouble()
    return String.format("%.1f MB", mb)
}
