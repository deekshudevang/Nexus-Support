package com.meshlink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MeshLinkColorScheme = darkColorScheme(
    primary             = Primary,
    onPrimary           = TextOnPrimary,
    primaryContainer    = PrimaryContainer,
    onPrimaryContainer  = TextPrimary,

    secondary           = Secondary,
    onSecondary         = TextOnPrimaryWhite,
    secondaryContainer  = SecondaryContainer,
    onSecondaryContainer= TextPrimary,

    tertiary            = Tertiary,
    onTertiary          = TextOnPrimaryWhite,
    tertiaryContainer   = TertiaryContainer,
    onTertiaryContainer = TextPrimary,

    background          = AppBackground,
    onBackground        = TextPrimary,
    surface             = CardSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = SurfaceVariant,
    onSurfaceVariant    = TextSecondary,

    outline             = Outline,
    outlineVariant      = OutlineVariant,

    error               = Primary,
    onError             = TextOnPrimaryWhite,
    errorContainer      = PrimaryLight,
    onErrorContainer    = TextPrimary,

    scrim               = Neutral.copy(alpha = 0.32f)
)

@Composable
fun MeshLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeshLinkColorScheme,
        typography  = MeshTypography,
        content     = content
    )
}

@Composable
fun MeshLinkLightTheme(content: @Composable () -> Unit) {
    // Both are now dark theme to maintain consistency with the new aesthetic
    MaterialTheme(
        colorScheme = MeshLinkColorScheme,
        typography  = MeshTypography,
        content     = content
    )
}
