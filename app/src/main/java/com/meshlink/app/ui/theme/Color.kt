package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  Startup SaaS Design System — MeshLink
// ═══════════════════════════════════════════════════════════════════════════════

val Primary            = Color(0xFF00E5FF)   // Neon Cyan / Electric Blue
val PrimaryDark        = Color(0xFF00B2CC)   // Deeper cyan
val PrimaryLight       = Color(0x3300E5FF)   // Light tint for containers (20% opacity)
val PrimaryContainer   = Color(0x4D00E5FF)   // Container (30% opacity)

val Secondary          = Color(0xFFB026FF)   // Neon Purple
val SecondaryDark      = Color(0xFF8B12CC)   // Deep purple
val SecondaryLight     = Color(0x33B026FF)   // Light purple tint
val SecondaryContainer = Color(0x4DB026FF)   // Purple container

val Tertiary           = Color(0xFF00FFA3)   // Neon Green
val TertiaryDark       = Color(0xFF00CC82)   // Deeper green
val TertiaryLight      = Color(0x3300FFA3)   // Light green tint
val TertiaryContainer  = Color(0x4D00FFA3)   // Green container

val Neutral            = Color(0xFFFFFFFF)   // Near-white — primary text on dark

val AppBackground      = Color(0xFF000000)   // True OLED Black
val CardSurface        = Color(0xFF0F0F13)   // Elevated sleek surface for cards
val SurfaceVariant     = Color(0xFF16161A)   // Input fields, secondary surfaces
val SurfaceBright      = Color(0xFF1F1F24)   // Elevated bright surface

val TextPrimary        = Color(0xFFF0F0F0)   // Primary text (Soft White)
val TextSecondary      = Color(0xFFA0A0A5)   // Secondary / body text
val TextMuted          = Color(0xFF6B6B70)   // Placeholder, disabled text
val TextOnPrimary      = Color(0xFFFFFFFF)   // White text on primary buttons
val TextOnPrimaryWhite = Color(0xFFFFFFFF)   // White text on primary buttons

val Outline            = Color(0xFF2C2C32)   // Dividers, card borders
val OutlineVariant     = Color(0xFF1F1F24)   // Subtle dividers

val StatusConnected    = Tertiary             // Green — connected
val StatusConnecting   = Secondary            // Purple — connecting
val StatusOffline      = Color(0xFF5A5A60)    // Gray — offline
val StatusError        = Color(0xFFFF3B30)    // iOS Red — error / disconnected

val BadgeNear          = Tertiary             // Green
val BadgeFar           = Primary              // Blue
val BadgeVeryFar       = Color(0xFF5A5A60)    // Gray

val BubbleSent         = Primary              // Primary sent bubble
val BubbleReceived     = SurfaceVariant       // Dark gray received bubble

val EmergencyRed          = StatusError
val EmergencyRedDark      = Color(0xFFD32F2F)
val EmergencyRedSurface   = StatusError
val EmergencyRedContainer = Color(0x33FF3B30)
val EmergencyRedLight     = Color(0x33FF3B30)
val EmergencyGreen        = Tertiary
val EmergencyGreenDark    = TertiaryDark
val EmergencyGreenLight   = TertiaryLight
val EmergencyAmber        = Color(0xFFFF9500)
val EmergencyAmberLight   = Color(0x33FF9500)
val EmergencyAmberDark    = Color(0xFFF39C12)
val DarkBackground        = AppBackground
val DarkSurface           = CardSurface
val DarkSurfaceElevated   = SurfaceVariant
val DarkBorder            = Outline
val LightBackground       = AppBackground
val LightSurface          = CardSurface
val LightSurfaceVariant   = SurfaceVariant
val LightBorder           = Outline
val TextOnDark            = TextPrimary
val TextOnDarkDim         = TextSecondary
val TextOnDarkMuted       = TextMuted
val BadgeOffline          = StatusOffline
val MeshPrimary           = Primary
val MeshBackground        = AppBackground
val MeshSurface           = CardSurface
val MeshSurfaceVariant    = SurfaceVariant
val MeshSurfaceBright     = SurfaceBright
val MeshPrimaryDim        = PrimaryDark
val MeshPrimaryContainer  = PrimaryContainer
val MeshOnPrimary         = TextOnPrimaryWhite
val MeshSecondary         = Secondary
val MeshSecondaryContainer= SecondaryContainer
val MeshConnected         = StatusConnected
val MeshConnecting        = StatusConnecting
val MeshHandshaking       = Secondary
val MeshDisconnected      = StatusError
val MeshOnBackground      = TextPrimary
val MeshOnBackgroundDim   = TextSecondary
val MeshOnBackgroundMuted = TextMuted
val MeshOutline           = Outline
val NavBarBackground      = AppBackground
val NavItemActive         = Primary
val NavItemInactive       = TextMuted
val NavIndicator          = PrimaryLight
val BloodGroupSelected    = Primary
val BloodGroupUnselected  = SurfaceVariant
val MeshReadyGreen        = Tertiary
val ContactAvatarSalmon   = Color(0xFFEF5350)
val RadarRing1            = Color(0x332979FF)
val RadarRing2            = Color(0x552979FF)
val RadarRing3            = Color(0x882979FF)
