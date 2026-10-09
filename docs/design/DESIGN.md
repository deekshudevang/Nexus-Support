# Nexus-Support Design System (DESIGN.md)

## 1. Mission & Ethos
**The visual personality must be: Precise and intentional. Calm under pressure. Highly legible. Technically sophisticated. Operational rather than decorative. Trustworthy and restrained.**

This is an emergency communication application. It is not a social network or a toy. It operates in high-stress, low-connectivity environments. 

**Avoid:**
- Gradients on every card or button.
- Excessive rounded rectangles (squishy UI).
- A dashboard made entirely of floating cards.
- Large empty areas (poor information density).
- Default Material 3 components used without thoughtful customization.

## 2. Typography
- **Primary Typeface**: System default sans-serif (Inter/Roboto), optimized for legibility.
- **Hierarchy**:
  - `Display`: Reserved for critical telemetry (e.g., "12 Offline Messages").
  - `Headline`: Section headers. Clean, medium weight, track out slightly.
  - `Title`: Component titles (e.g., node names). Strong, clear.
  - `Body`: Message text and standard operational data. Must remain legible under glare.
  - `Label`: Metadata, timestamps, status tags. Use uppercase with wider tracking for technical data.

## 3. Surface Hierarchy & Spacing
- **Base Background**: True black (`#000000`) to save battery on OLED screens and reduce glare.
- **Level 1 (App Surface)**: `SurfaceVariant` (`#16161A`). Used for structural containers, not isolated floating cards. Flush to edges where possible to maximize space.
- **Level 2 (Elevated)**: `SurfaceBright` (`#1F1F24`). Used for interactive rows, message bubbles, or prominent active states.
- **Borders over Elevation**: Rely on subtle 1dp borders (`Outline` `#2C2C32`) rather than drop shadows, which look messy in dark mode.
- **Density**: Use tight, deliberate spacing for technical data (telemetry, mesh topology) and generous spacing for critical interactive targets (SOS, Send).

## 4. Color Strategy
- **Restraint**: Use the neon brand colors (`Primary` Cyan, `Secondary` Purple, `Tertiary` Green) *sparingly* to draw the eye to state changes and primary actions.
- **Status Indicators**:
  - `Connected` / `Safe`: `Tertiary` (Green)
  - `Connecting` / `Syncing`: `Secondary` (Purple)
  - `Offline` / `Stale`: Muted Grays
  - `Critical` / `SOS`: `EmergencyRed`
- **Text Contrast**: Ensure WCAG AA compliance. Use `TextPrimary` for core data, `TextSecondary` for labels.

## 5. Component Specifications

### 5.1 TelemetryBadge
- **Purpose**: Display technical metadata (signal strength, battery, hops).
- **Style**: Flush background (`SurfaceVariant`), sharp or slightly rounded corners (e.g., 4dp), subtle border. Icon + Monospaced text.
- **Avoid**: Pill-shaped brightly colored tags unless indicating a critical alert.

### 5.2 QuickActionChip
- **Purpose**: Contextual actions (e.g., "Send Location", "Acknowledge").
- **Style**: Outlined or subtly filled. 8dp corner radius. No gradients. Must look like a distinct control but not overpower the primary action (e.g., "Send" button).

### 5.3 MessageBubble
- **Purpose**: Chat and broadcast messages.
- **Style**: 
  - *Sent*: Subtle `Primary` tint background, no gradient. Distinct but not blinding.
  - *Received*: `SurfaceVariant` with a left border indicating sender color.
  - *Corner Radius*: 12dp, asymmetrical for flow direction.
  - *Density*: Tight padding. Metadata (time, status) sits flush below or inline with the bubble, using `LabelSmall`.

### 5.4 List Items (Nodes, Chats)
- **Purpose**: Displaying mesh nodes or conversations.
- **Style**: Edge-to-edge layout, separated by 1dp `OutlineVariant` dividers. Do NOT wrap every item in an isolated floating card. Use a clear grid for avatar, title, subtitle, and trailing telemetry.

## 6. Interaction Design
- **Feedback**: Immediate visual feedback for all taps (ripple).
- **Transitions**: Fast, linear transitions. Avoid bouncy or springy animations.
- **Critical Actions**: SOS or broadcast actions must require distinct, intentional interaction (e.g., slide-to-confirm or large, unambiguous tap targets with hold-to-cancel).
