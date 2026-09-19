package com.meshlink.app.ui.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SosScreen(viewModel: SosViewModel) {
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val satelliteLock by viewModel.satelliteLockStatus.collectAsState()
    val activeUplink by viewModel.activeUplink.collectAsState()
    val incidentType by viewModel.incidentType.collectAsState()
    val includeGps by viewModel.includeGps.collectAsState()
    val includeMedical by viewModel.includeMedical.collectAsState()
    val nextOrb by viewModel.nextOrbitalWindowSeconds.collectAsState()

    val darkBg = MaterialTheme.colorScheme.background
    val panelBg = MaterialTheme.colorScheme.surfaceVariant
    val cyan = MaterialTheme.colorScheme.secondary
    val crimson = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isBroadcasting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(crimson.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .border(1.dp, crimson, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = crimson, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DISTRESS FLOOD ACTIVE // TX: 100%", color = crimson, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(text = satelliteLock, color = cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .background(if (isBroadcasting) crimson else panelBg, RoundedCornerShape(90.dp))
                .border(2.dp, if (isBroadcasting) Color.White else crimson, RoundedCornerShape(90.dp))
                .clickable { viewModel.toggleSos() },
            contentAlignment = Alignment.Center
        ) {
            Text(if (isBroadcasting) "ABORT" else "SOS", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("UPLINK FAILOVER", color = Color.White, fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            UplinkButton("LORA RF", activeUplink == SosViewModel.UplinkType.LORA_RF, { viewModel.setUplink(SosViewModel.UplinkType.LORA_RF) }, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            UplinkButton("IRIDIUM SBD", activeUplink == SosViewModel.UplinkType.SATELLITE, { viewModel.setUplink(SosViewModel.UplinkType.SATELLITE) }, Modifier.weight(1f))
        }
        
        if (activeUplink == SosViewModel.UplinkType.SATELLITE) {
            Text("NEXT ORBITAL WINDOW: 00:0${nextOrb}s", color = cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("INCIDENT TYPE", color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            IncidentCard("MED EVAC", incidentType == SosViewModel.IncidentType.MED_EVAC, { viewModel.setIncidentType(SosViewModel.IncidentType.MED_EVAC) }, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IncidentCard("LOST", incidentType == SosViewModel.IncidentType.LOST, { viewModel.setIncidentType(SosViewModel.IncidentType.LOST) }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            IncidentCard("GEAR FAIL", incidentType == SosViewModel.IncidentType.GEAR, { viewModel.setIncidentType(SosViewModel.IncidentType.GEAR) }, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IncidentCard("SECURITY", incidentType == SosViewModel.IncidentType.SECURITY, { viewModel.setIncidentType(SosViewModel.IncidentType.SECURITY) }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("GPS COORDINATES", color = Color.White, fontSize = 14.sp)
            Switch(checked = includeGps, onCheckedChange = { viewModel.toggleGps() })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MEDICAL DOSSIER", color = Color.White, fontSize = 14.sp)
            Switch(checked = includeMedical, onCheckedChange = { viewModel.toggleMedical() })
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Column(modifier = Modifier.fillMaxWidth().background(panelBg, RoundedCornerShape(4.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp)).padding(8.dp)) {
            Text("> LORA FLOOD DAEMON v2.4", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("> WAITING FOR UPLINK...", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            if (isBroadcasting) {
                Text("> TX BROADCAST INITIATED", color = crimson, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("> HOP 1: ACKNOWLEDGED", color = cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun UplinkButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) Color(0xFF00F0FF).copy(alpha = 0.2f) else Color(0xFF111827)
    val borderColor = if (selected) Color(0xFF00F0FF) else Color.DarkGray
    val textColor = if (selected) Color(0xFF00F0FF) else Color.White
    Box(
        modifier = modifier.background(bgColor, RoundedCornerShape(4.dp)).border(1.dp, borderColor, RoundedCornerShape(4.dp)).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
}

@Composable
fun IncidentCard(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF111827)
    val borderColor = if (selected) Color(0xFFEF4444) else Color.DarkGray
    val textColor = if (selected) Color(0xFFEF4444) else Color.White
    Box(
        modifier = modifier.background(bgColor, RoundedCornerShape(4.dp)).border(1.dp, borderColor, RoundedCornerShape(4.dp)).clickable { onClick() }.padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
