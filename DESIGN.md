# RescueMesh Design System (DESIGN.md)

This document defines the premium, production-quality emergency communication design system for RescueMesh. It acts as the source of truth for the Android application's UI/UX.

## 1. Design Philosophy
- **Offline-First & Resilient:** The UI must communicate connection states, offline capabilities, and data sync status clearly.
- **High-Stress Usability:** Emergency features (SOS) must have minimal cognitive load, large tap targets, and no unnecessary scrolling.
- **Density & Edge-to-Edge Design:** Use an edge-to-edge layout where possible, separated by 1dp OutlineVariant dividers. Do NOT wrap every item in an isolated floating card. Avoid nested padding that reduces content width.
- **Material Design 3 (M3):** Leverage dynamic theming, semantic colors, and standard M3 components. Do not use default M3 components without thoughtful customization.
- **Accessibility:** Ensure high contrast, clear typography, scalable fonts, and content descriptions for all actionable elements.

## 2. Foundations

### 2.1 Color System (Semantic)
Avoid hardcoded hex values (`Color(0xFF...)`). Use `MaterialTheme.colorScheme` to support seamless dark/light modes.

* **Primary:** Core branding, main actions (Send message, Start Broadcast).
* **OnPrimary:** Text/Icons on Primary surfaces.
* **PrimaryContainer / OnPrimaryContainer:** Softer backgrounds for highlighted elements.
* **Secondary / Tertiary:** Accents for secondary actions and telemetry metrics.
* **Error:** Critical alerts, active SOS state, connection failures (Red).
* **Background / Surface:** Main app backgrounds.
* **SurfaceVariant:** Flat background layers for list rows or secondary containers. Do not use elevated cards unnecessarily.
* **Outline / OutlineVariant:** Borders for inputs, dividers (1dp height), and empty states.

### 2.2 Typography
Use the M3 Typography scale. Emphasize legibility.
* **Display/Headline:** Prominent headers (e.g., Active SOS timer).
* **Title:** App bar titles, dialog headers, section headers.
* **Body:** Chat messages, list item descriptions, instructions.
* **Label:** Metadata, connection status, timestamps, button text.

### 2.3 Spacing & Layout
* **Grid/Spacing:** 4dp baseline grid (4, 8, 12, 16, 24, 32, 48, 64).
* **Screen Padding:** Minimum 16dp horizontal padding on all edge-to-edge elements.
* **Touch Targets:** Minimum 48x48dp for all interactive elements.
* **Dividers:** Use 1dp `OutlineVariant` backgrounds to separate list items or rows instead of floating cards with padding.

## 3. Core Components

### 3.1 Buttons & Actions
* **Primary Button:** Filled button, used for the main action (e.g., Send Broadcast). Must include standard M3 ripple/morphing affordances. Sharp or mildly rounded corners (8dp to 12dp max) rather than full pills for denser layouts.
* **SOS Button:** Extra-large, circular or prominent shape. Changes to `Error` color when active. Action text inside must be highly readable (e.g., >14sp).
* **Secondary/Text Buttons:** Used for cancellation, secondary options, or dialog actions.
* **QuickActionChip:** Dense action toggles (e.g., connection methods, filters) should be standardized as `QuickActionChip` using an 8dp corner radius, avoiding overly bouncy pill shapes unless specified.

### 3.2 Containers & Surfaces
* **Edge-to-Edge Rows:** Lists of items (chats, active nodes) should span the full width of the screen. Use an explicit divider between rows.
* **MessageBubble:** Differentiated by sender (PrimaryContainer vs SurfaceVariant). Distinct shapes (rounded corners based on sender). Must not look like a floating standalone card. Keep padding snug.
* **TelemetryBadge:** Small pill or dot indicators showing status (battery, active peers, secure mesh active). Group them logically near titles or in status headers. Use solid distinct colors from the tertiary/secondary palette.

### 3.3 Inputs
* **Chat Composer:** Must adapt to screen size. Needs sufficient padding around the text input. Keep tactical chips accessible but not cluttered.
* **Standard Text Fields:** Must use M3 `OutlinedTextField` or `TextField` with clear focus indicators, labels, and error states.

### 3.4 Status Indicators
* **Top Bar Telemetry:** Clear icons for Connection, Mesh Nodes, and Battery.
* **Message Status:** Sent (single tick), Delivered (double tick), Failed (alert icon).
* **Empty States:** Visual illustration + prompt text when a list is empty.

## 4. Screens & Flows

### 4.1 Home / Dashboard
* Displays recent chats, active mesh status, and quick access to SOS/Broadcast.
* **Layout Rule:** Do NOT use floating `NexusCard`s for the broadcast or active channel items. Use `ActiveChannelRow` and `BroadcastChannelRow` that span the full width, separated by dividers.
* *State:* Show clear "Offline / Searching for Mesh" banner if disconnected.

### 4.2 SOS Screen
* **Focus:** Unmistakable Call to Action.
* **Layout:** All critical controls (Button, Incident Type) MUST fit on one screen without scrolling.

### 4.3 Chat Screen
* **Message Bubbles:** Standardized as mentioned above.
* **Header:** Peer connection status and telemetry clearly visible.

### 4.4 Broadcast Screen
* **Focus:** Single-task oriented.
* **Input:** Clear, outlined text field.

## 5. Accessibility
* Add `contentDescription` to all non-decorative icons.
* Ensure contrast ratio meets WCAG AA standards (4.5:1 for normal text).
* Support dynamic font scaling without breaking layouts.
