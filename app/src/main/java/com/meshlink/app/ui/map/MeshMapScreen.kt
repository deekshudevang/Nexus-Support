package com.meshlink.app.ui.map

import com.meshlink.app.ui.components.MetricBadge
import com.meshlink.app.ui.components.NexusCard

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshMapScreen(
    viewModel: MeshMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterModal by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Topology Map", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = { showFilterModal = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E1E1E))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setUseDataConnection(true) // Allow loading online map tiles
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        
                        val validNodes = uiState.nodes.filter { it.latitude != null && it.longitude != null }
                        val nodePoints = mutableMapOf<String, GeoPoint>()
                        
                        for (node in validNodes) {
                            val point = GeoPoint(node.latitude!!, node.longitude!!)
                            nodePoints[node.id] = point
                            
                            val marker = Marker(mapView)
                            marker.position = point
                            marker.title = node.name
                            marker.snippet = "Battery: ${node.batteryLevel}% | Hops: ${node.hopCount}"
                            mapView.overlays.add(marker)
                        }
                        
                        // Draw edges
                        for (edge in uiState.edges) {
                            val startPoint = nodePoints[edge.sourceId]
                            val endPoint = nodePoints[edge.targetId]
                            if (startPoint != null && endPoint != null) {
                                val line = Polyline(mapView)
                                line.addPoint(startPoint)
                                line.addPoint(endPoint)
                                line.color = AndroidColor.argb(128, 255, 69, 0) // Semi-transparent Neon Orange
                                line.width = 5f
                                mapView.overlays.add(line)
                            }
                        }
                        
                        // Auto-center on first available node if zoom is default
                        if (validNodes.isNotEmpty() && mapView.zoomLevelDouble < 10.0) {
                            mapView.controller.setCenter(nodePoints[validNodes.first().id])
                        }
                        
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Stats overlay
                NexusCard(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .padding(bottom = 32.dp),
                    elevation = 4.dp
                ) {
                    MetricBadge(
                        text = "Peers: ${uiState.nodes.size}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    if (uiState.nodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.nodes.take(5).forEach { node ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (node.isDirect) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = node.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (uiState.nodes.size > 5) {
                                Text(
                                    text = "and ${uiState.nodes.size - 5} more...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (uiState.pendingMessagesCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MetricBadge(
                            text = "Pending: ${uiState.pendingMessagesCount}",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        if (showFilterModal) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showFilterModal = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Topology Filter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Displaying nodes up to ${uiState.currentHopLimit} RF hops away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    NexusCard(elevation = 0.dp) {
                        Slider(
                            value = uiState.currentHopLimit.toFloat(),
                            onValueChange = { viewModel.setHopLimit(it.roundToInt()) },
                            valueRange = 1f..7f,
                            steps = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
