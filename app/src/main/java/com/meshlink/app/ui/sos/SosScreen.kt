package com.meshlink.app.ui.sos

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SosScreen(
    viewModel: SosViewModel,
    onGuideClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {}
) {
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val deliveryStatus by viewModel.deliveryStatus.collectAsState()
    val gpsFixAvailable by viewModel.gpsFixAvailable.collectAsState()
    val activeUplink by viewModel.activeUplink.collectAsState()
    val incidentType by viewModel.incidentType.collectAsState()
    val includeGps by viewModel.includeGps.collectAsState()
    val includeMedical by viewModel.includeMedical.collectAsState()

    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = "SOS Emergency Screen" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        AnimatedVisibility(
            visible = isBroadcasting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = errorColor.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, errorColor.copy(alpha = 0.3f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning, 
                            contentDescription = "Active Alert", 
                            tint = errorColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "EMERGENCY SIGNAL ACTIVE",
                            color = errorColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (deliveryStatus) {
                            SosViewModel.SosDeliveryState.PEER_ACKNOWLEDGED -> "DELIVERY CONFIRMED: PEER ACK RECEIVED"
                            SosViewModel.SosDeliveryState.DELIVERED -> "RELAYED TO MESH PEER"
                            SosViewModel.SosDeliveryState.TRANSMITTING -> "BROADCASTING BEACON (AWAITING PEER ACK)"
                            SosViewModel.SosDeliveryState.PENDING -> "ENQUEUED: PENDING DISPATCH"
                            SosViewModel.SosDeliveryState.FAILED -> "TRANSMISSION FAILED (ALL RETRIES EXHAUSTED)"
                            SosViewModel.SosDeliveryState.EXPIRED -> "SIGNAL EXPIRED"
                            SosViewModel.SosDeliveryState.IDLE -> ""
                        },
                        color = when (deliveryStatus) {
                            SosViewModel.SosDeliveryState.PEER_ACKNOWLEDGED,
                            SosViewModel.SosDeliveryState.DELIVERED -> Color(0xFF4CAF50)
                            SosViewModel.SosDeliveryState.FAILED,
                            SosViewModel.SosDeliveryState.EXPIRED -> errorColor
                            else -> errorColor.copy(alpha = 0.85f)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Start)
                .background(primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = "GPS Status", tint = primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (gpsFixAvailable) "GPS Fix: Acquired" else "GPS Signal: Searching...",
                color = primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))

        SosInteractiveButton(
            isBroadcasting = isBroadcasting,
            onToggle = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.toggleSos() 
            }
        )
        
        Spacer(modifier = Modifier.height(56.dp))

        Text(
            text = "CONNECTION METHOD",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionPill(
                text = "Nearby Direct (BLE/Wi-Fi)",
                selected = activeUplink == SosViewModel.UplinkType.NEARBY_BLE,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setUplink(SosViewModel.UplinkType.NEARBY_BLE) 
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            SelectionPill(
                text = "LoRa RF (Meshtastic)",
                selected = activeUplink == SosViewModel.UplinkType.LORA_RF,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setUplink(SosViewModel.UplinkType.LORA_RF) 
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "NATURE OF EMERGENCY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionCard(
                text = "Medical",
                selected = incidentType == SosViewModel.IncidentType.MED_EVAC,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setIncidentType(SosViewModel.IncidentType.MED_EVAC) 
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            SelectionCard(
                text = "Lost",
                selected = incidentType == SosViewModel.IncidentType.LOST,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setIncidentType(SosViewModel.IncidentType.LOST) 
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionCard(
                text = "Equipment",
                selected = incidentType == SosViewModel.IncidentType.GEAR,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setIncidentType(SosViewModel.IncidentType.GEAR) 
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            SelectionCard(
                text = "In Danger",
                selected = incidentType == SosViewModel.IncidentType.SECURITY,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setIncidentType(SosViewModel.IncidentType.SECURITY) 
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleGps() 
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Share location", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Switch(checked = includeGps, onCheckedChange = { viewModel.toggleGps() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleMedical() 
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Share medical info", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Switch(checked = includeMedical, onCheckedChange = { viewModel.toggleMedical() })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onGuideClick()
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Guides", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDashboardClick()
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Dashboard", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SosInteractiveButton(
    isBroadcasting: Boolean,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "buttonScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val errorColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    val mainColor = if (isBroadcasting) errorColor else primaryColor

    Box(
        modifier = Modifier
            .size(240.dp)
            .semantics { contentDescription = if (isBroadcasting) "Cancel SOS" else "Hold to send SOS" },
        contentAlignment = Alignment.Center
    ) {
        // Pulsing rings when broadcasting
        if (isBroadcasting) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(errorColor.copy(alpha = pulseAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isBroadcasting) 24.dp else 12.dp,
                    shape = CircleShape,
                    spotColor = mainColor
                )
                .clip(CircleShape)
                .background(mainColor)
                .border(4.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val pressStartTime = System.currentTimeMillis()
                            try {
                                awaitRelease()
                                val duration = System.currentTimeMillis() - pressStartTime
                                if (duration > 500) { // Hold for 500ms to activate/cancel
                                    onToggle()
                                } else {
                                    // Hint to hold longer
                                }
                            } finally {
                                isPressed = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isBroadcasting) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 40.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HOLD TO CANCEL",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        lineHeight = 56.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HOLD TO SEND",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        
        // Progress ring during press
        if (isPressed) {
            val progress by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(500, easing = LinearEasing),
                label = "holdProgress"
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(220.dp),
                color = Color.White,
                strokeWidth = 6.dp,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun SelectionPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "bg")
    val textColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "text")
    val borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(24.dp))
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp)
            .semantics { 
                contentDescription = if (selected) "$text selected" else text 
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SelectionCard(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface, label = "bg")
    val textColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface, label = "text")
    val borderColor = if (selected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 20.dp)
            .semantics { 
                contentDescription = if (selected) "$text selected" else text 
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
