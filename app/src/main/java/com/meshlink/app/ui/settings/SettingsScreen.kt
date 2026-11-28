package com.meshlink.app.ui.settings

import com.meshlink.app.ui.components.NexusCard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onDebugDashboardClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val useMeshtastic by viewModel.useMeshtastic.collectAsState()
    val useNearby by viewModel.useNearby.collectAsState()
    val batteryOptimization by viewModel.batteryOptimization.collectAsState()
    val crashReportingOptIn by viewModel.crashReportingOptIn.collectAsState()
    
    val isMasterPaused by viewModel.isMasterScanningPaused.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hardware Integration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi Mesh (Google Nearby)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Use device Wi-Fi/Bluetooth to route packets to nearby phones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useNearby,
                        onCheckedChange = { viewModel.setUseNearby(it) }
                    )
                }
            }

            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LoRa Mesh (Meshtastic)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Use Bluetooth to forward packets through an external Meshtastic LoRa radio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useMeshtastic,
                        onCheckedChange = { viewModel.setUseMeshtastic(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Battery & Network",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Optimization",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pause network scanning when battery is critically low.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = batteryOptimization,
                        onCheckedChange = { viewModel.setBatteryOptimization(it) }
                    )
                }
            }
            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pause Mesh & Location Scanning",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Master toggle to pause all background network discovery and location tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMasterPaused,
                        onCheckedChange = { viewModel.toggleMasterScanning(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val isAiEnabled by viewModel.isAiEnabled.collectAsState()
            val aiState by viewModel.aiState.collectAsState()

            Text(
                text = "Offline AI Assistant",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Offline AI",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Status: ${aiState.name} (Strictly on-device, 0 network use)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAiEnabled,
                            onCheckedChange = { viewModel.toggleAiEnabled(it) }
                        )
                    }

                    val importStatus by viewModel.importStatus.collectAsState()
                    val modelPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            viewModel.importModel(uri)
                        }
                    }

                    val pendingHash by viewModel.pendingHashConfirmation.collectAsState()
                    if (pendingHash != null) {
                        AlertDialog(
                            onDismissRequest = { viewModel.cancelPendingModelHash() },
                            title = { Text("Confirm Model Integrity", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Computed SHA-256 Checksum:")
                                    Text(
                                        text = pendingHash!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Verify this checksum against the official model provider before loading into memory.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = { viewModel.confirmPendingModelHash() }) {
                                    Text("Confirm & Load")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.cancelPendingModelHash() }) {
                                    Text("Reject")
                                }
                            }
                        )
                    }

                    if (importStatus != null) {
                        Text(
                            text = importStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Accept all binary containers (.litertlm, .task, .bin, octet-stream)
                                modelPickerLauncher.launch(
                                    arrayOf(
                                        "application/octet-stream",
                                        "application/*",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import Model (SAF)", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearAiData() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear AI Data", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    val canRollback by viewModel.canRollback.collectAsState()
                    if (canRollback) {
                        OutlinedButton(
                            onClick = { viewModel.rollbackModel() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Rollback to Previous Model", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isAiEnabled) {
                        Button(
                            onClick = { viewModel.triggerAiKillSwitch() },

                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("EMERGENCY AI KILL SWITCH", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Privacy & Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crash & Diagnostic Reporting",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Opt-in anonymous error reports to help diagnose crashes. Disabled by default to protect offline privacy and battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = crashReportingOptIn,
                        onCheckedChange = { viewModel.setCrashReportingOptIn(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val persistedUrl by viewModel.backendBaseUrl.collectAsState()
            val persistedKey by viewModel.gatewayKey.collectAsState()
            val syncTelemetry by viewModel.syncTelemetry.collectAsState()
            val pendingSosCount by viewModel.pendingSosCount.collectAsState()
            val connectionTestState by viewModel.connectionTestState.collectAsState()

            var urlInput by remember(persistedUrl) { mutableStateOf(persistedUrl) }
            var keyInput by remember(persistedKey) { mutableStateOf(persistedKey) }
            var showGatewayKey by remember { mutableStateOf(false) }

            Text(
                text = "Cloud Gateway Connectivity",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            NexusCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Backend Base URL") },
                        placeholder = { Text("http://192.168.1.4:8000/ or https://api.meshlink.app/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gateway API Key (Encrypted at rest)") },
                        singleLine = true,
                        visualTransformation = if (showGatewayKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGatewayKey = !showGatewayKey }) {
                                Icon(
                                    imageVector = if (showGatewayKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showGatewayKey) "Hide gateway key" else "Show gateway key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveAndTestConnection(urlInput, keyInput) },
                            enabled = connectionTestState !is ConnectionTestState.Testing,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(
                                text = if (connectionTestState is ConnectionTestState.Testing) "Testing..." else "Test Connection",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.triggerManualSync() },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    when (val state = connectionTestState) {
                        is ConnectionTestState.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.tertiary)
                                Text(state.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        is ConnectionTestState.Error -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = "Connection Error", tint = MaterialTheme.colorScheme.error)
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> Unit
                    }

                    HorizontalDivider()

                    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    val lastSuccessLabel = syncTelemetry.lastSyncSuccessMs?.let { formatter.format(Date(it)) } ?: "Never"
                    val lastAttemptLabel = syncTelemetry.lastSyncAttemptMs?.let { formatter.format(Date(it)) } ?: "Never"

                    Text(
                        text = "Pending SOS uploads: $pendingSosCount",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Last successful sync: $lastSuccessLabel • Last attempt: $lastAttemptLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!syncTelemetry.lastError.isNullOrBlank()) {
                        Text(
                            text = "Last sync error: ${syncTelemetry.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onDiagnosticsClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("View Diagnostics Dashboard", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDebugDashboardClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("View QA Debug Dashboard", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
