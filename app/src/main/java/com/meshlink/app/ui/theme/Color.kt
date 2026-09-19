package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  Figma Design System — MeshLink
// ═══════════════════════════════════════════════════════════════════════════════

// ── Core Palette ─────────────────────────────────────────────────────────────
val Primary            = Color(0xFFFF4500)   // Neon Orange/Red — critical actions, alerts, buttons (Stitch CustomColor)
val PrimaryDark        = Color(0xFFCC3700)   // Pressed / darker neon orange
val PrimaryLight       = Color(0x33FF4500)   // Light tint for containers (20% opacity)
val PrimaryContainer   = Color(0x4DFF4500)   // Container (30% opacity)

val Secondary          = Color(0xFF00BFFF)   // Deep Sky Blue — highlights, secondary actions
val SecondaryDark      = Color(0xFF0080FF)   // Pressed blue
val SecondaryLight     = Color(0x3300BFFF)   // Light blue tint
val SecondaryContainer = Color(0x4D00BFFF)   // Blue container

val Tertiary           = Color(0xFF00FA9A)   // Medium Spring Green — success, connected, safe
val TertiaryDark       = Color(0xFF00C77B)   // Pressed green
val TertiaryLight      = Color(0x3300FA9A)   // Light green tint
val TertiaryContainer  = Color(0x4D00FA9A)   // Green container

val Neutral            = Color(0xFFFFFFFF)   // Near-white — primary text on dark

// ── Backgrounds & Surfaces (DARK MODE) ───────────────────────────────────────
val AppBackground      = Color(0xFF121212)   // Main app background — DARK
val CardSurface        = Color(0xFF1E1E1E)   // Slightly elevated surface for cards
val SurfaceVariant     = Color(0xFF2C2C2C)   // Input fields, secondary surfaces
val SurfaceBright      = Color(0xFF383838)   // Elevated bright surface

// ── Text Colors ──────────────────────────────────────────────────────────────
val TextPrimary        = Color(0xFFFFFFFF)   // Primary text (White)
val TextSecondary      = Color(0xB3FFFFFF)   // Secondary / body text (70% White)
val TextMuted          = Color(0x66FFFFFF)   // Placeholder, disabled text (40% White)
val TextOnPrimary      = Color(0xFF121212)   // Dark text on primary buttons (contrast)
val TextOnPrimaryWhite = Color(0xFFFFFFFF)   // White text on primary buttons if contrast allows

// ── Borders & Dividers ───────────────────────────────────────────────────────
val Outline            = Color(0xFF333333)   // Dividers, card borders
val OutlineVariant     = Color(0xFF222222)   // Subtle dividers

// ── Status Colors ────────────────────────────────────────────────────────────
val StatusConnected    = Tertiary             // Green — connected
val StatusConnecting   = Secondary            // Blue — connecting
val StatusOffline      = Color(0xFF757575)    // Gray — offline
val StatusError        = Primary              // Orange/Red — error / disconnected

// ── Distance Badges ──────────────────────────────────────────────────────────
val BadgeNear          = Tertiary             // Green
val BadgeFar           = Secondary            // Blue
val BadgeVeryFar       = Color(0xFF757575)    // Gray

// ── Chat Bubbles ─────────────────────────────────────────────────────────────
val BubbleSent         = Primary              // Primary sent bubble
val BubbleReceived     = CardSurface          // Dark gray received bubble

// ── Legacy Aliases (backward compat — maps old names to Figma system) ────────
val EmergencyRed          = Primary
val EmergencyRedDark      = PrimaryDark
val EmergencyRedSurface   = Primary
val EmergencyRedContainer = PrimaryLight
val EmergencyRedLight     = PrimaryLight
val EmergencyGreen        = Tertiary
val EmergencyGreenDark    = TertiaryDark
val EmergencyGreenLight   = TertiaryLight
val EmergencyAmber        = Secondary
val EmergencyAmberLight   = SecondaryLight
val EmergencyAmberDark    = SecondaryDark
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
val MeshHandshaking       = Color(0xFFBB86FC)
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
val RadarRing1            = Color(0x33FF4500)
val RadarRing2            = Color(0x55FF4500)
val RadarRing3            = Color(0x88FF4500)
