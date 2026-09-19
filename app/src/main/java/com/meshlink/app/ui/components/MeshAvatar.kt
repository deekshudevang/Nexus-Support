package com.meshlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Avatar showing the first 1–2 initials of [name] on a deterministic
 * color background tuned for the new SaaS theme.
 */
@Composable
fun MeshAvatar(
    name:     String,
    modifier: Modifier = Modifier,
    size:     Dp       = 46.dp
) {
    val initials = remember(name) { extractInitials(name) }
    val bgColor  = remember(name) { avatarColor(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .semantics { contentDescription = "Avatar for $name" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = initials,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize   = (size.value * 0.35f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        )
    }
}

private fun extractInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2              -> "${words[0][0]}${words[1][0]}".uppercase()
        words.size == 1 && words[0].length >= 2 -> words[0].take(2).uppercase()
        words.size == 1              -> words[0].take(1).uppercase()
        else                         -> "?"
    }
}

// Deep, vibrant palette for SaaS aesthetic
private val avatarPalette = listOf(
    Color(0xFF2979FF), // Electric Blue
    Color(0xFF8E24AA), // Vibrant Purple
    Color(0xFF00B0FF), // Light Blue
    Color(0xFF00E676), // Neon Green
    Color(0xFFFF3D00), // Orange/Red
    Color(0xFF651FFF), // Deep Purple
    Color(0xFFF50057), // Pink
    Color(0xFF00B8D4)  // Cyan
)

private fun avatarColor(name: String): Color {
    val idx = Math.abs(name.hashCode()) % avatarPalette.size
    return avatarPalette[idx]
}
