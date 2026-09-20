package com.meshlink.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.Message
import com.meshlink.app.ui.theme.CardSurface
import com.meshlink.app.ui.theme.Primary


@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connState by viewModel.connectionState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val sasCode by viewModel.sasCode.collectAsState()
    val isPeerVerified by viewModel.isPeerVerified.collectAsState()
    val listState = rememberLazyListState()

    var showVerifyDialog by remember { mutableStateOf(false) }

    // Mock states from ViewModel
    val snr by viewModel.snr.collectAsState()
    val signalStrength by viewModel.signalStrength.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val protocol by viewModel.protocol.collectAsState()
    val modulation by viewModel.modulation.collectAsState()

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    val txColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (connState == ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else Color.Gray,
        label = "txColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = viewModel.deviceName.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPeerVerified) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Verified", tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        androidx.compose.animation.AnimatedContent(targetState = connState, label = "connState") { state ->
                            Text(
                                text = if (state == ConnectionState.CONNECTED) "Connected via Mesh" else "Offline",
                                color = if (state == ConnectionState.CONNECTED) Primary else Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Battery: ${battery}%", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    Text(text = "Encrypted", color = Primary, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    if (sasCode != null && !isPeerVerified) {
                        TextButton(
                            onClick = { showVerifyDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(text = "Verify Contact", color = Primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Telemetry badges removed for simplicity
        }

        if (showVerifyDialog && sasCode != null) {
            AlertDialog(
                onDismissRequest = { showVerifyDialog = false },
                title = { Text("Verify Contact", color = Color.White) },
                text = {
                    Column {
                        Text("Compare this security code with the one on ${viewModel.deviceName}'s screen:", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = sasCode ?: "",
                            color = Primary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("If the codes match exactly, it is safe to verify. This protects against Man-in-the-Middle attacks.", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onVerifyContactConfirmed()
                        showVerifyDialog = false
                    }) {
                        Text("Verify", color = Primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVerifyDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = CardSurface,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg ->
                val isMe = msg.senderId == viewModel.localDeviceId
                MessageBubble(message = msg, isMe = isMe)
            }
        }

        // Tactical Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface.copy(alpha = 0.9f))
                .padding(16.dp)
        ) {
            // Quick action chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                QuickActionChip(icon = Icons.Default.LocationOn, text = "Location")
                QuickActionChip(icon = Icons.Default.Memory, text = "Vitals")
                QuickActionChip(icon = Icons.Default.Map, text = "Map Tile")
            }
            
            // Input field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1F), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF2A2A35), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { viewModel.onInputChanged(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text("Message...", color = Color.Gray, fontSize = 14.sp, fontFamily = FontFamily.SansSerif)
                        }
                        innerTextField()
                    }
                )
                
                Box(
                    modifier = Modifier
                        .background(txColor, RoundedCornerShape(16.dp))
                        .clickable(enabled = connState == ConnectionState.CONNECTED) { viewModel.onSendClick() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Send", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                }
            }
            
            // Payload Estimator
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text(text = "${inputText.length}/240", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isMe: Boolean) {
    val bubbleShape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(
                    if (isMe) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(Primary, Color(0xFF00B2CC))
                            ),
                            shape = bubbleShape
                        )
                    } else {
                        Modifier.background(
                            color = Color(0xFF1E1E24),
                            shape = bubbleShape
                        ).border(1.dp, Color(0xFF2C2C32), bubbleShape)
                    }
                )
                .padding(14.dp)
        ) {
            Text(
                text = String(message.ciphertext, Charsets.UTF_8),
                color = if (isMe) Color.Black else Color.White,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        // Status / Telemetry for message
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMe) {
                Text("Delivered", color = Primary, fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
            } else {
                Text("via Node-Echo", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
            }
        }
    }
}

@Composable
fun TelemetryBadge(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF2A1105), RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = Primary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun QuickActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF24242A), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF33333E), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
    }
}
