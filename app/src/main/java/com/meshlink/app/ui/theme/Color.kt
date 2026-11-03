package com.meshlink.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  Public Safety & Emergency Operations Design System — MeshLink
//  Engineered for high contrast, direct sunlight legibility, and zero visual slop
// ═══════════════════════════════════════════════════════════════════════════════

val Primary            = Color(0xFF2563EB)   // Rescue Command Blue (ANSI Public Safety standard)
val PrimaryDark        = Color(0xFF1D4ED8)   // Deep Command Blue
val PrimaryLight       = Color(0x262563EB)   // 15% Blue container tint
val PrimaryContainer   = Color(0x332563EB)   // 20% Blue container

val Secondary          = Color(0xFF0284C7)   // Cadet Telemetry Blue/Slate
val SecondaryDark      = Color(0xFF0369A1)   // Darker Telemetry Blue
val SecondaryLight     = Color(0x260284C7)   // Light Telemetry tint
val SecondaryContainer = Color(0x330284C7)   // Telemetry container

val Tertiary           = Color(0xFF16A34A)   // Operational Safety Green
val TertiaryDark       = Color(0xFF15803D)   // Forest Operational Green
val TertiaryLight      = Color(0x2616A34A)   // Light Green tint
val TertiaryContainer  = Color(0x3316A34A)   // Green container

val Neutral            = Color(0xFFF8FAFC)   // Crisp High-Contrast Off-White

val AppBackground      = Color(0xFF0B0F19)   // Deep Matte Slate Canvas (prevents glare & eye strain)
val CardSurface        = Color(0xFF131B2B)   // Rugged Slate Surface for tactical cards
val SurfaceVariant     = Color(0xFF1E293B)   // Secondary slate panels, input fields
val SurfaceBright      = Color(0xFF243046)   // Focused/elevated field elements

val TextPrimary        = Color(0xFFF8FAFC)   // Primary High-Contrast Text (100% legibility outdoors)
val TextSecondary      = Color(0xFF94A3B8)   // Secondary Telemetry & metadata text
val TextMuted          = Color(0xFF64748B)   // Disabled/de-emphasized label text
val TextOnPrimary      = Color(0xFFFFFFFF)   // White text on primary buttons
val TextOnPrimaryWhite = Color(0xFFFFFFFF)   // White text on primary buttons

val Outline            = Color(0xFF334155)   // Crisp structural perimeter borders
val OutlineVariant     = Color(0xFF1E293B)   // Subtle panel dividers

val StatusConnected    = Tertiary             // Green — operational link
val StatusConnecting   = Color(0xFFD97706)    // Amber — acquiring signal / synchronizing
val StatusOffline      = Color(0xFF64748B)    // Neutral Slate — offline / standby
val StatusError        = Color(0xFFDC2626)    // ANSI Emergency Red — beacon active / failure

val BadgeNear          = Tertiary             // Operational Green
val BadgeFar           = Secondary            // Telemetry Blue
val BadgeVeryFar       = Color(0xFF64748B)    // Slate Gray

val BubbleSent         = Primary              // Command Blue bubble
val BubbleReceived     = SurfaceVariant       // Neutral slate bubble

val EmergencyRed          = StatusError       // 0xFFDC2626
val EmergencyRedDark      = Color(0xFFB91C1C)
val EmergencyRedSurface   = StatusError
val EmergencyRedContainer = Color(0x33DC2626)
val EmergencyRedLight     = Color(0x33DC2626)
val EmergencyGreen        = Tertiary          // 0xFF16A34A
val EmergencyGreenDark    = TertiaryDark
val EmergencyGreenLight   = TertiaryLight
val EmergencyAmber        = Color(0xFFD97706)
val EmergencyAmberLight   = Color(0x33D97706)
val EmergencyAmberDark    = Color(0xFFB45309)

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
val MeshHandshaking       = StatusConnecting
val MeshDisconnected      = StatusError
val MeshOnBackground      = TextPrimary
val MeshOnBackgroundDim   = TextSecondary
val MeshOnBackgroundMuted = TextMuted
val MeshOutline           = Outline
val NavBarBackground      = AppBackground
val NavItemActive         = Color(0xFF38BDF8) // High-visibility Sky Blue
val NavItemInactive       = TextMuted
val NavIndicator          = PrimaryLight
val BloodGroupSelected    = Primary
val BloodGroupUnselected  = SurfaceVariant
val MeshReadyGreen        = Tertiary
val ContactAvatarSalmon   = Color(0xFFE11D48)
val RadarRing1            = Color(0x2638BDF8)
val RadarRing2            = Color(0x3338BDF8)
val RadarRing3            = Color(0x4D38BDF8)
