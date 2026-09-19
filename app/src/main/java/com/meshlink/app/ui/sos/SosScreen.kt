package com.meshlink.app.ui.sos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlink.app.ui.components.NexusCard

@Composable
fun SosScreen(viewModel: SosViewModel) {
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val satelliteLock by viewModel.satelliteLockStatus.collectAsState()
    val activeUplink by viewModel.activeUplink.collectAsState()
    val incidentType by viewModel.incidentType.collectAsState()
    val includeGps by viewModel.includeGps.collectAsState()
    val includeMedical by viewModel.includeMedical.collectAsState()
    val nextOrb by viewModel.nextOrbitalWindowSeconds.collectAsState()

    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = isBroadcasting) {
            NexusCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = errorColor)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Emergency Signal Active",
                        color = errorColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = "GPS", tint = primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (satelliteLock.contains("LOCK")) "GPS Signal: Strong" else "GPS Signal: Searching...",
                color = primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        // Big SOS Button
        Box(
            modifier = Modifier
                .size(200.dp)
                .shadow(
                    elevation = if (isBroadcasting) 24.dp else 12.dp,
                    shape = CircleShape,
                    ambientColor = if (isBroadcasting) errorColor else primary,
                    spotColor = if (isBroadcasting) errorColor else primary
                )
                .clip(CircleShape)
                .background(if (isBroadcasting) errorColor else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 4.dp,
                    color = if (isBroadcasting) Color.White else primary,
                    shape = CircleShape
                )
                .clickable { viewModel.toggleSos() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isBroadcasting) "CANCEL" else "SOS",
                color = if (isBroadcasting) Color.White else primary,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Connection Method",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionPill(
                text = "Local Mesh",
                selected = activeUplink == SosViewModel.UplinkType.LORA_RF,
                onClick = { viewModel.setUplink(SosViewModel.UplinkType.LORA_RF) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            SelectionPill(
                text = "Satellite",
                selected = activeUplink == SosViewModel.UplinkType.SATELLITE,
                onClick = { viewModel.setUplink(SosViewModel.UplinkType.SATELLITE) },
                modifier = Modifier.weight(1f)
            )
        }
        
        if (activeUplink == SosViewModel.UplinkType.SATELLITE) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Satellite available in ${nextOrb}s",
                color = primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "What is your emergency?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionCard(
                text = "Medical",
                selected = incidentType == SosViewModel.IncidentType.MED_EVAC,
                onClick = { viewModel.setIncidentType(SosViewModel.IncidentType.MED_EVAC) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            SelectionCard(
                text = "Lost",
                selected = incidentType == SosViewModel.IncidentType.LOST,
                onClick = { viewModel.setIncidentType(SosViewModel.IncidentType.LOST) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SelectionCard(
                text = "Equipment",
                selected = incidentType == SosViewModel.IncidentType.GEAR,
                onClick = { viewModel.setIncidentType(SosViewModel.IncidentType.GEAR) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            SelectionCard(
                text = "In Danger",
                selected = incidentType == SosViewModel.IncidentType.SECURITY,
                onClick = { viewModel.setIncidentType(SosViewModel.IncidentType.SECURITY) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        NexusCard(elevation = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share my location", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Switch(checked = includeGps, onCheckedChange = { viewModel.toggleGps() })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share medical info", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Switch(checked = includeMedical, onCheckedChange = { viewModel.toggleMedical() })
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Simple Status Log
        NexusCard(elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isBroadcasting) "Sending emergency signal..." else "Ready to send.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isBroadcasting) errorColor else MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun SelectionPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(24.dp))
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SelectionCard(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.error else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
