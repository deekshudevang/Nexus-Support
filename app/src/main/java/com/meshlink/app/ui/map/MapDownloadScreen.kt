package com.meshlink.app.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlink.app.ui.components.NexusCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MapRegion(
    val id: String,
    val name: String,
    val sizeMb: Int,
    var isDownloaded: Boolean = false,
    var isDownloading: Boolean = false,
    var progress: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDownloadScreen(
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Mock regions
    val regions = remember {
        mutableStateListOf(
            MapRegion("ind_mh", "Maharashtra, India", 215),
            MapRegion("ind_ka", "Karnataka, India", 185),
            MapRegion("ind_dl", "Delhi, India", 45),
            MapRegion("us_ca", "California, USA", 310)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Maps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Download vector maps for true offline capability. These maps do not expire and require no internet connection after download.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(regions) { region ->
                MapRegionItem(
                    region = region,
                    onDownloadClick = {
                        val index = regions.indexOf(region)
                        if (index != -1) {
                            val updated = region.copy(isDownloading = true)
                            regions[index] = updated
                            
                            scope.launch {
                                // Simulate download
                                for (i in 1..100) {
                                    delay(30)
                                    regions[index] = regions[index].copy(progress = i / 100f)
                                }
                                regions[index] = regions[index].copy(
                                    isDownloading = false,
                                    isDownloaded = true,
                                    progress = 1f
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MapRegionItem(
    region: MapRegion,
    onDownloadClick: () -> Unit
) {
    NexusCard(elevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${region.sizeMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (region.isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { region.progress },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }
            }
            
            IconButton(
                onClick = onDownloadClick,
                enabled = !region.isDownloaded && !region.isDownloading
            ) {
                if (region.isDownloaded) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (!region.isDownloading) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download"
                    )
                }
            }
        }
    }
}
