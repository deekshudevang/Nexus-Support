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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val listState = rememberLazyListState()

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
                .background(CardSurface)
                .border(1.dp, Primary.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
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
                    androidx.compose.animation.AnimatedContent(targetState = connState, label = "connState") { state ->
                        Text(
                            text = if (state == ConnectionState.CONNECTED) "Connected via Mesh" else "Offline",
                            color = if (state == ConnectionState.CONNECTED) Primary else Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Battery: ${battery}%", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    Text(text = "Encrypted", color = Primary, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Telemetry badges removed for simplicity
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
                .background(CardSurface)
                .border(1.dp, Primary.copy(alpha = 0.15f))
                .padding(12.dp)
        ) {
            // Quick action chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                QuickActionChip(icon = Icons.Default.LocationOn, text = "Send Location")
                QuickActionChip(icon = Icons.Default.Memory, text = "Send Vitals")
                QuickActionChip(icon = Icons.Default.Map, text = "Send Map Tile")
            }
            
            // Input field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        .background(txColor, RoundedCornerShape(4.dp))
                        .clickable(enabled = connState == ConnectionState.CONNECTED) { viewModel.onSendClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Send", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isMe) Primary.copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isMe) Primary.copy(alpha = 0.6f) else Color.DarkGray,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = String(message.ciphertext, Charsets.UTF_8),
                color = Color.White,
                fontSize = 14.sp
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
            .background(Color.Transparent)
            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
    }
}
