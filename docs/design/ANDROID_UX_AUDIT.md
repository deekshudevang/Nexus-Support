# ANDROID UI/UX AUDIT & COMMAND PLATFORM REDESIGN SPECIFICATION

**Document Version:** 2.0.0  
**Target:** Nexus-Support Emergency Mesh Communication Platform  
**Design Standard:** Emergency-First, Unslop Design System, Material 3 Jetpack Compose  
**Date:** 2026-11-23  

---

## 1. Executive Summary & Design Philosophy

Emergency response tools are operated under high physical and cognitive stress: low light, bright direct sunlight, smoke, rain, adrenaline, and with gloved or wet hands. Visual ambiguity, decorative clutter, and sluggish interactions can cost lives.

### Core Pillars
1. **Zero AI Slop**: Absolutely no decorative neon purple/magenta gradients, no faux-futuristic glowing borders, no emoji badges (`NEW ✨`), no gratuitous drop-shadows, and no fake telemetry.
2. **Tactical Precision & Contrast**: Deep tactical slate neutrals (Dark: `#0F172A`, Surface: `#1E293B`, Borders: `#334155`), high-contrast text (100% white primary, `#94A3B8` secondary), and strictly functional semantic status colors:
   - Critical / SOS: Crimson (`#EF4444`)
   - Active Mesh / Verified: Emerald (`#10B981`)
   - Warning / Connecting: Amber (`#F59E0B`)
   - Operational Action: Tactical Cyan/Blue (`#3B82F6`)
3. **Rigid 4px Spatial Grid**: All dimensions, paddings, and margins adhere to multiples of 4: `4dp`, `8dp`, `12dp`, `16dp`, `20dp`, `24dp`, `32dp`, `48dp`.
4. **48dp Ergonomic Touch Targets**: Every button, chip, and icon target guarantees at least 48x48dp touchable bounds to prevent accidental mis-taps during crisis conditions.

---

## 2. Navigation Architecture Transformation

### The 7-Destination Anti-Pattern
Prior iterations introduced 6 to 7 destinations in the bottom navigation bar (`Home`, `Discovery`, `Map`, `AI`, `Admin`, `SOS`). This caused:
- Severe overcrowding on 360–412dp Android devices (< 50dp per tab width).
- Text label truncation and icon clipping.
- Mis-tapping between everyday tabs and the life-critical SOS beacon.
- Exposing privileged administrative controls to everyday survivors.

### Redesigned 5-Destination Primary Navigation
The primary bottom navigation bar is streamlined to 5 core operational destinations:

```
[ CHATS ]    [ DISCOVER ]    [ ( ! ) SOS ]    [ MAP ]    [ AI ASSIST ]
  (Home)      (Mesh Peers)    (Life-Safety)   (Tactical)   (Protocols)
```

1. **CHATS (`Screen.Home`)**: Active direct conversations, emergency broadcast feed, and high-level mesh health chip.
2. **DISCOVER (`Screen.Discovery`)**: Nearby radio discovery (BLE, Wi-Fi Direct), active peer transceivers, pairing triggers.
3. **SOS (`Screen.Sos`)**: Centered, elevated, high-contrast crimson beacon. Unambiguous single-tap emergency broadcast with countdown and cancel option.
4. **MAP (`Screen.Map`)**: Geospatial situational awareness, offline raster/vector mbtiles, peer pins, and danger zones.
5. **AI ASSIST (`Screen.AiAssistant`)**: Offline Gemma 3 on-device intelligence for medical first-aid protocols, triage, and search-and-rescue guidance.

### Secondary & Privileged Destinations
- **Admin Dashboard (`Screen.Admin`)**: Relocated to the Top App Bar action overflow and Settings. Gated with role authorization and audit logging.
- **Assignments (`Screen.Assignments`)**: Sourced from the Home status card or Admin tab.
- **Medical Profile (`Screen.MedicalProfile`)**: Accessible via Settings or user profile chip.
- **Map Downloads (`Screen.MapDownload`)**: Accessible from Map top action.

---

## 3. Screen-by-Screen Redesign & Truthful Interaction Matrix

### 3.1 Home Screen (`HomeScreen.kt`)
- **Header**: Compact tactical banner showing Radio scan status (`ACTIVE_SCAN`, `BATTERY_SAVER`, `PAUSED`), peer count badge, and quick Settings action.
- **Broadcast Channel Card**: Dedicated top-pinned channel for one-to-all emergency broadcasts.
- **Conversation List**: Clean, scannable conversation rows with initials avatar (`MeshAvatar`), verified cryptographic shield, last message preview, and timestamp.
- **Truthful States**: When no peers are discovered, displays a clear, actionable empty state with a "Scan for Peers" action.

### 3.2 SOS Emergency Beacon (`SosScreen.kt`)
- **Primary Trigger**: Large high-contrast crimson button with haptic feedback.
- **Truthful Telemetry**:
  - GPS Coordinate display shows real satellite fix. If unacquired, displays `"Searching for GPS lock..."` — NEVER fake coordinates.
  - Durable Queue counter: Displays exact number of pending packets stored in encrypted Room database.
- **Cancellation**: Clear 5-second countdown banner allowing accidental triggers to be cancelled before RF broadcast.

### 3.3 Discovery Screen (`DiscoveryScreen.kt`)
- **Peer List**: Displays actual discovered endpoints with real connection states (`CONNECTED`, `CONNECTING`, `AVAILABLE`).
- **No Distance Fabrication**: Eliminates fake "3.2m away" distance estimates; replaces them with genuine radio transport type (`Nearby BLE`, `Wi-Fi Direct`, `Meshtastic LoRa`).

### 3.4 Tactical Map (`MeshMapScreen.kt`)
- **Tile Layer**: High-contrast offline vector/raster map with dark tactical skin.
- **Markers**: Responder pins, SOS emergency beacons, and team clusters with unambiguous status rings.
- **Controls**: Recenter on GPS, toggle mesh topology overlay, offline region download shortcut.

### 3.5 Offline AI Assistant (`AiAssistantScreen.kt`)
- **Model Invariant**: Strictly backed by on-device Gemma 3 via MediaPipe GenAI.
- **Discrete Lifecycle States**:
  1. `UNAVAILABLE`: Model binary not loaded or device lacks memory. Displays clear manual download/copy instruction.
  2. `LOADING`: Real progress indicator while weights load into NPU/GPU.
  3. `READY`: Model initialized and ready for offline inference.
  4. `INFERRING`: Real streaming response generation.
  5. `ERROR`: Explicit error message without fake fallback hallucination.
- **Prompt Guard**: Strict XML sandboxing prevents emergency instruction bypass or hallucinations.

### 3.6 Admin Dashboard (`AdminDashboardScreen.kt`)
- **Real Metrics**: System uptime sourced from `SystemClock.elapsedRealtime()`; node battery displayed only when reported by protocol; honest "Unknown" states.
- **Tabs**: `OVERVIEW`, `NODES`, `ASSIGNMENTS`, `CONFIG`.
- **Role Verification**: Admin operations logged to persistent audit table.

---

## 4. Accessibility & Material 3 Verification Standards

- **Contrast Ratios**: All text elements verified >= 4.5:1 against their container surfaces.
- **Touch Target Bounds**: Minimum 48x48dp for all clickables.
- **TalkBack Semantics**: Explicit `contentDescription` on every icon, badge, and interactive element.
- **Typography**: Single clean neutral font family (Inter/Roboto) with Monospace font reserved for IDs, timestamps, and coordinates.
