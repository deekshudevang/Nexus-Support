package com.meshlink.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BottomNavItem(
    val route:          String,
    val label:          String,
    val selectedIcon:   ImageVector,
    val unselectedIcon: ImageVector,
    val isSos:          Boolean = false
)

val bottomNavItems = listOf(
    BottomNavItem(
        route          = Screen.Home.route,
        label          = "CHATS",
        selectedIcon   = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    ),
    BottomNavItem(
        route          = Screen.Discovery.route,
        label          = "DISCOVER",
        selectedIcon   = Icons.Filled.WifiTethering,
        unselectedIcon = Icons.Outlined.WifiTethering
    ),
    BottomNavItem(
        route          = Screen.Map.route,
        label          = "MAP",
        selectedIcon   = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map
    ),
    BottomNavItem(
        route          = Screen.Sos.route,
        label          = "SOS",
        selectedIcon   = Icons.Filled.Warning,
        unselectedIcon = Icons.Filled.Warning,
        isSos          = true
    )
)

@Composable
fun MeshBottomNavBar(
    currentRoute: String?,
    onNavigate:   (String) -> Unit,
    modifier:     Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f) // Glassmorphic tint
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { item ->
                    NavItem(
                        item     = item,
                        selected = currentRoute == item.route,
                        onClick  = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    item:     BottomNavItem,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue   = when {
            item.isSos -> MaterialTheme.colorScheme.error
            selected   -> MaterialTheme.colorScheme.primary
            else       -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "navIconColor"
    )

    Column(
        modifier            = modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier         = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        item.isSos && selected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        selected               -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else                   -> Color.Transparent
                    }
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint               = iconColor,
                modifier           = Modifier.size(24.dp)
            )
        }
    }
}
