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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MyLocation
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.mapsforge.map.reader.MapFile
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshMapScreen(
    viewModel: MeshMapViewModel = hiltViewModel(),
    onNavigateToDownloads: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterModal by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<com.meshlink.app.ui.map.MeshNode?>(null) }
    val context = LocalContext.current

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName // Important for Mapnik to not reject tile requests
        
        // Performance & Battery Tuning: Map rendering limits
        config.tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // Limit cache to 50MB
        config.tileFileSystemCacheTrimBytes = 40L * 1024 * 1024 // Trim to 40MB when limit reached
        config.osmdroidTileCache = File(context.cacheDir, "osmdroid_tiles")
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
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "Offline Maps")
                    }
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
                            mapViewRef = this
                            // Check if local mapsforge file exists (simulated location)
                            val mapFile = File(ctx.getExternalFilesDir(null), "offline_map.map")
                            if (mapFile.exists()) {
                                try {
                                    val map = MapFile(mapFile)
                                    val forge = MapsForgeTileSource.createFromFiles(arrayOf(mapFile))
                                    val provider = MapsForgeTileProvider(
                                        org.osmdroid.tileprovider.util.SimpleRegisterReceiver(ctx),
                                        forge,
                                        null
                                    )
                                    setTileProvider(provider)
                                    setUseDataConnection(false)
                                } catch (e: Exception) {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setUseDataConnection(true)
                                }
                            } else {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setUseDataConnection(true) // Allow loading online map tiles as fallback
                            }
                            
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        
                        val validNodes = uiState.nodes.filter { it.latitude != null && it.longitude != null }
                        val nodePoints = mutableMapOf<String, GeoPoint>()
                        
                        // Create a RadiusMarkerClusterer
                        val clusterer = RadiusMarkerClusterer(mapView.context)
                        // Create a default cluster icon (blue circle)
                        val clusterIcon = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(clusterIcon)
                        val paint = android.graphics.Paint().apply {
                            color = AndroidColor.argb(200, 33, 150, 243)
                            isAntiAlias = true
                        }
                        canvas.drawCircle(40f, 40f, 40f, paint)
                        clusterer.setIcon(clusterIcon)
                        
                        for (node in validNodes) {
                            val point = GeoPoint(node.latitude!!, node.longitude!!)
                            nodePoints[node.id] = point
                            
                            val marker = Marker(mapView)
                            marker.position = point
                            marker.title = node.name
                            marker.snippet = "Battery: ${node.batteryLevel}% | Hops: ${node.hopCount}"
                            marker.setOnMarkerClickListener { m, _ ->
                                selectedNode = node
                                true
                            }
                            clusterer.add(marker)
                        }
                        
                        mapView.overlays.add(clusterer)
                        
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
                        
                        
                        // Add MyLocation overlay
                        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(mapView.context), mapView)
                        myLocationOverlay.enableMyLocation()
                        
                        // Automatically center on user when location is first found
                        myLocationOverlay.runOnFirstFix {
                            mapView.post {
                                mapView.controller.animateTo(myLocationOverlay.myLocation)
                                mapView.controller.setZoom(17.0)
                            }
                        }
                        
                        mapView.overlays.add(myLocationOverlay)
                        
                        // Store the overlay in tag so we can use it from FAB if needed
                        mapView.tag = myLocationOverlay
                        
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // My Location FAB
                FloatingActionButton(
                    onClick = { 
                        val overlay = mapViewRef?.tag as? MyLocationNewOverlay
                        val location = overlay?.myLocation
                        if (location != null) {
                            mapViewRef?.controller?.animateTo(location)
                            mapViewRef?.controller?.setZoom(17.0)
                        } else {
                            // If location isn't ready yet, we can enable follow location
                            overlay?.enableFollowLocation()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                }
                
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
        
        // Node Details Bottom Sheet
        val activeNode = selectedNode
        if (activeNode != null) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { selectedNode = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Peer", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = activeNode.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (activeNode.isDirect) "Direct Connection" else "${activeNode.hopCount} Hops Away",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (activeNode.isDirect) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    NexusCard(elevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Battery Level:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${activeNode.batteryLevel}%", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Last Seen:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Just now", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Coordinates:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format("%.4f", activeNode.latitude)}, ${String.format("%.4f", activeNode.longitude)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
