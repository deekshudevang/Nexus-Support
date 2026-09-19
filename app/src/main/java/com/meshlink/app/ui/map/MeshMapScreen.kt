package com.meshlink.app.ui.map

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
                            setUseDataConnection(false) // Force offline mode
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
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        "Peers: ${uiState.nodes.size}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .background(Color(0xFF1E1E1E).copy(alpha = 0.85f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Pending Messages: ${uiState.pendingMessagesCount}",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .background(Color(0xFF1E1E1E).copy(alpha = 0.85f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
                        .padding(16.dp)
                ) {
                    Text(
                        text = "RF Hop Limit Filter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Showing peers up to ${uiState.currentHopLimit} hops away",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = uiState.currentHopLimit.toFloat(),
                        onValueChange = { viewModel.setHopLimit(it.roundToInt()) },
                        valueRange = 1f..7f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
