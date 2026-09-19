package com.meshlink.app.ui.home

import androidx.compose.material.icons.filled.Info
import com.meshlink.app.ui.components.EmptyStateView
import com.meshlink.app.ui.components.NexusCard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlink.app.ui.components.MeshAvatar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

@Composable
fun HomeScreen(
    onConversationClick: (deviceId: String, deviceName: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onBroadcastClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val peerCount     by viewModel.peerCount.collectAsStateWithLifecycle()
    val pendingCount  by viewModel.pendingMessageCount.collectAsStateWithLifecycle()
    val scanStrategy  by viewModel.scanStrategy.collectAsStateWithLifecycle()

    val isScanningPaused = scanStrategy == com.meshlink.app.domain.model.ScanStrategy.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatListHeader(
            isScanningPaused = isScanningPaused,
            onToggleScan = { viewModel.toggleScanning() },
            onSettingsClick = onSettingsClick
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SectionLabel("BROADCAST CHANNEL")
                if (conversations.isNotEmpty()) {
                    val broadcast = conversations.first()
                    BroadcastChannelCard(
                        conversation = broadcast,
                        onClick      = { onConversationClick(broadcast.deviceId, broadcast.deviceName) }
                    )
                } else {
                    EmptyBroadcastCard()
                }
            }

            item { 
                NetworkStatusSection(
                    peerCount = peerCount, 
                    pendingCount = pendingCount,
                    scanStrategy = scanStrategy,
                    onResumeClick = { viewModel.forceResumeScanning() }
                ) 
            }
            item { BroadcastToAllButton(onClick = onBroadcastClick) }
            item { SectionLabel("ACTIVE CHANNELS") }

            if (conversations.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Info,
                        title = "No active channels",
                        message = "Connected peers will appear here when they send messages.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                items(conversations.drop(1).ifEmpty { conversations }, key = { it.deviceId }) { conv ->
                    ActiveChannelRow(
                        conversation = conv,
                        isOnline     = true,
                        onClick      = { onConversationClick(conv.deviceId, conv.deviceName) }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 78.dp)
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ChatListHeader(
    isScanningPaused: Boolean,
    onToggleScan: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.WifiTethering,
                contentDescription = null,
                tint     = if (isScanningPaused) Color.Gray else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text       = if (isScanningPaused) "MESH PAUSED" else "MESH ACTIVE",
                style      = MaterialTheme.typography.titleMedium,
                color      = if (isScanningPaused) Color.Gray else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(
                checked = !isScanningPaused,
                onCheckedChange = { onToggleScan() },
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit profile",
                    tint     = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        style         = MaterialTheme.typography.labelLarge,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier      = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun BroadcastChannelCard(conversation: Conversation, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (conversation.isSos) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (conversation.isSos) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                MeshAvatar(name = conversation.deviceName, size = 50.dp)
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .align(Alignment.Center)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text       = conversation.deviceName,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = if (conversation.isSos) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (conversation.isSos) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text("SOS", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text     = conversation.lastMessage,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text  = conversation.formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyBroadcastCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        EmptyStateView(
            icon = Icons.Default.Info,
            title = "No Broadcasts",
            message = "Global network alerts will appear here."
        )
    }
}

@Composable
private fun NetworkStatusSection(
    peerCount: Int, 
    pendingCount: Int,
    scanStrategy: com.meshlink.app.domain.model.ScanStrategy,
    onResumeClick: () -> Unit
) {
    NexusCard(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        elevation = 0.dp
    ) {
        Text(
            text          = "NETWORK STATUS",
            style         = MaterialTheme.typography.labelLarge,
            color         = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = "$peerCount PEERS",
            style      = MaterialTheme.typography.displayMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            Text(
                text       = "SECURE MESH ACTIVE",
                style      = MaterialTheme.typography.labelLarge,
                color      = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        
        if (pendingCount > 0) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Text(
                    text       = "$pendingCount PENDING (STORE & FORWARD)",
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
        
        AnimatedVisibility(
            visible = scanStrategy == com.meshlink.app.domain.model.ScanStrategy.PAUSED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scanning Paused",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Battery is critically low.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = onResumeClick) {
                            Text("RESUME", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastToAllButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Default.WifiTethering,
                contentDescription = null,
                tint     = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Broadcast to All",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Alert all nodes in range",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Text(
                text       = "›",
                color      = Color.White,
                fontSize   = 24.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
private fun ActiveChannelRow(
    conversation: Conversation,
    isOnline:     Boolean,
    onClick:      () -> Unit
) {
    val bgColor = if (conversation.isSos) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box {
            MeshAvatar(name = conversation.deviceName, size = 48.dp)
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .align(Alignment.BottomEnd)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outline
                        )
                        .align(Alignment.Center)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text       = conversation.deviceName,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = if (conversation.isSos) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (conversation.isSos) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text("SOS", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!isOnline) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text     = "OFFLINE",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Text(
                        text  = conversation.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text     = conversation.lastMessage,
                style    = MaterialTheme.typography.bodySmall,
                color    = if (isOnline) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
