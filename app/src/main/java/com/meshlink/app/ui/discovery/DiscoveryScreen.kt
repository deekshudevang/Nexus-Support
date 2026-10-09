package com.meshlink.app.ui.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice

// Mocked distance helper (Nearby API doesn't expose distance)
private fun mockDistance(endpointId: String): String {
    val distances = listOf("42M", "110M", "250M", "85M", "320M", "67M")
    return distances[Math.abs(endpointId.hashCode()) % distances.size]
}

@Composable
private fun getDistanceColor(endpointId: String): Color {
    val d = mockDistance(endpointId).removeSuffix("M").toIntOrNull() ?: 100
    return when {
        d <= 80  -> MaterialTheme.colorScheme.primary
        d <= 180 -> MaterialTheme.colorScheme.secondary
        else     -> MaterialTheme.colorScheme.error
    }
}

private fun distanceBadgeLabel(endpointId: String): String {
    val d = mockDistance(endpointId).removeSuffix("M").toIntOrNull() ?: 100
    return when {
        d <= 80  -> "NEAR"
        d <= 180 -> "FAR"
        else     -> "VERY FAR"
    }
}

private fun deviceIcon(index: Int): ImageVector = when (index % 3) {
    0    -> Icons.Default.Person
    1    -> Icons.Default.CellTower
    else -> Icons.Default.SignalCellularAlt
}


@Composable
fun DiscoveryScreen(
    onDeviceClick: (endpointId: String, deviceName: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val devices          by viewModel.devices.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToChat.collect { (endpointId, deviceName) ->
            onDeviceClick(endpointId, deviceName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DeviceListHeader(onSettingsClick = onSettingsClick)

        NetworkStatusCard(deviceCount = devices.size)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text          = "PEERS IN RANGE",
                style         = MaterialTheme.typography.labelLarge,
                color         = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            ScanningBadge()
        }

        if (devices.isEmpty()) {
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color       = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier    = Modifier.size(48.dp)
                    )
                    Text(
                        text  = "Scanning for mesh nodes…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(devices.mapIndexed { i, d -> i to d }, key = { it.second.endpointId }) { (idx, device) ->
                    val state        = connectionStates[device.endpointId] ?: ConnectionState.IDLE
                    val isConnecting = state == ConnectionState.CONNECTING ||
                                       state == ConnectionState.HANDSHAKING
                    val isConnected  = state == ConnectionState.CONNECTED || state == ConnectionState.AUTHENTICATED
                    DeviceCard(
                        device       = device,
                        icon         = deviceIcon(idx),
                        distance     = mockDistance(device.endpointId),
                        isConnecting = isConnecting,
                        isConnected  = isConnected,
                        onClick      = { if (!isConnecting) viewModel.onDeviceClick(device) },
                        onDisconnect = { viewModel.onDisconnectClick(device.endpointId) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}


@Composable
private fun DeviceListHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text       = "Mesh Network",
                style      = MaterialTheme.typography.titleLarge,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


@Composable
private fun NetworkStatusCard(deviceCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text          = "NETWORK STATUS",
                    style         = MaterialTheme.typography.labelMedium,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text       = if (deviceCount == 0) "Scanning for peers"
                                 else "$deviceCount peers nearby",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val statusColor by animateColorAsState(
                        targetValue = if (deviceCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "statusColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text       = if (deviceCount > 0) "MESH SECURED" else "WAITING FOR PEERS",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = statusColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}


@Composable
private fun ScanningBadge() {
    val transition = rememberInfiniteTransition(label = "scan")
    val alpha by transition.animateFloat(
        initialValue  = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha * 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
            Text(
                text     = "SCANNING",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun DeviceCard(
    device:       DiscoveredDevice,
    icon:         ImageVector,
    distance:     String,
    isConnecting: Boolean,
    isConnected:  Boolean,
    onClick:      () -> Unit,
    onDisconnect: () -> Unit
) {
    val badgeColor = getDistanceColor(device.endpointId)
    val badgeLabel = distanceBadgeLabel(device.endpointId)
    
    val containerColor by animateColorAsState(
        targetValue = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        label = "cardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isConnected) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Device icon
                Box(
                    modifier         = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier           = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = device.name,
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "$distance away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Distance badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        text     = badgeLabel,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action button
            val buttonColor by animateColorAsState(
                targetValue = when {
                    isConnected  -> MaterialTheme.colorScheme.primary
                    isConnecting -> MaterialTheme.colorScheme.surfaceVariant
                    else         -> MaterialTheme.colorScheme.secondary
                },
                label = "buttonColor"
            )
            val buttonTextColor by animateColorAsState(
                targetValue = when {
                    isConnected  -> MaterialTheme.colorScheme.onPrimary
                    isConnecting -> MaterialTheme.colorScheme.onSurfaceVariant
                    else         -> MaterialTheme.colorScheme.onSecondary
                },
                label = "buttonTextColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(buttonColor)
                    .clickable(enabled = !isConnecting) {
                        if (isConnected) onDisconnect() else onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color       = buttonTextColor,
                            strokeWidth = 2.dp,
                            modifier    = Modifier.size(18.dp)
                        )
                        Text(
                            text       = "Connecting…",
                            style      = MaterialTheme.typography.labelLarge,
                            color      = buttonTextColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    } else if (isConnected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = buttonTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text       = "Connected",
                            style      = MaterialTheme.typography.labelLarge,
                            color      = buttonTextColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Text(
                            text       = "Connect",
                            style      = MaterialTheme.typography.labelLarge,
                            color      = buttonTextColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
