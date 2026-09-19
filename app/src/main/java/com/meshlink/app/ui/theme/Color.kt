package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  Startup SaaS Design System — MeshLink
// ═══════════════════════════════════════════════════════════════════════════════

// ── Core Palette ─────────────────────────────────────────────────────────────
val Primary            = Color(0xFF2979FF)   // Electric Blue — startup aesthetic
val PrimaryDark        = Color(0xFF1565C0)   // Deeper blue
val PrimaryLight       = Color(0x332979FF)   // Light tint for containers (20% opacity)
val PrimaryContainer   = Color(0x4D2979FF)   // Container (30% opacity)

val Secondary          = Color(0xFFBB86FC)   // Neon Purple — accent / highlights
val SecondaryDark      = Color(0xFF9B51E0)   // Deep purple
val SecondaryLight     = Color(0x33BB86FC)   // Light purple tint
val SecondaryContainer = Color(0x4DBB86FC)   // Purple container

val Tertiary           = Color(0xFF00E676)   // Spring Green — success, connected, safe
val TertiaryDark       = Color(0xFF00C853)   // Pressed green
val TertiaryLight      = Color(0x3300E676)   // Light green tint
val TertiaryContainer  = Color(0x4D00E676)   // Green container

val Neutral            = Color(0xFFFFFFFF)   // Near-white — primary text on dark

// ── Backgrounds & Surfaces (DARK MODE) ───────────────────────────────────────
val AppBackground      = Color(0xFF0B0B0C)   // Deep OLED Black
val CardSurface        = Color(0xFF151518)   // Elevated sleek surface for cards
val SurfaceVariant     = Color(0xFF1E1E22)   // Input fields, secondary surfaces
val SurfaceBright      = Color(0xFF28282D)   // Elevated bright surface

// ── Text Colors ──────────────────────────────────────────────────────────────
val TextPrimary        = Color(0xFFF0F0F0)   // Primary text (Soft White)
val TextSecondary      = Color(0xFFA0A0A5)   // Secondary / body text
val TextMuted          = Color(0xFF6B6B70)   // Placeholder, disabled text
val TextOnPrimary      = Color(0xFFFFFFFF)   // White text on primary buttons
val TextOnPrimaryWhite = Color(0xFFFFFFFF)   // White text on primary buttons

// ── Borders & Dividers ───────────────────────────────────────────────────────
val Outline            = Color(0xFF2C2C32)   // Dividers, card borders
val OutlineVariant     = Color(0xFF1F1F24)   // Subtle dividers

// ── Status Colors ────────────────────────────────────────────────────────────
val StatusConnected    = Tertiary             // Green — connected
val StatusConnecting   = Secondary            // Purple — connecting
val StatusOffline      = Color(0xFF5A5A60)    // Gray — offline
val StatusError        = Color(0xFFFF3B30)    // iOS Red — error / disconnected

// ── Distance Badges ──────────────────────────────────────────────────────────
val BadgeNear          = Tertiary             // Green
val BadgeFar           = Primary              // Blue
val BadgeVeryFar       = Color(0xFF5A5A60)    // Gray

// ── Chat Bubbles ─────────────────────────────────────────────────────────────
val BubbleSent         = Primary              // Primary sent bubble
val BubbleReceived     = SurfaceVariant       // Dark gray received bubble

// ── Legacy Aliases (backward compat — maps old names to Figma system) ────────
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
