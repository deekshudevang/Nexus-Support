# UX Audit: Meshlink Offline Messaging

This document outlines the UX audit findings for the key communication screens in the Meshlink app: Chat, SOS, and Broadcast. The audit evaluates usability, accessibility, visual hierarchy, and consistency with modern Android design principles (Material Design 3).

## 1. Chat Screen (`ChatScreen.kt`)

### Strengths
- **Clear Connection Status**: The top bar effectively displays connection states, peer verification, and telemetry like battery life.
- **Message Bubbles**: Good differentiation between sent and received messages using gradients and distinct shapes.

### Areas for Improvement
- **Hardcoded Colors**: The screen relies heavily on hardcoded hex colors (e.g., `Color(0xFF1A1A1F)`, `Color(0xFF2A2A35)`) rather than `MaterialTheme` semantic colors. This breaks dark/light mode adaptability.
- **Empty State**: There is no visual empty state (e.g., illustration or prompt) when the chat has zero messages. Users just see a blank screen before they start chatting.
- **Tactical Composer Clutter**: The composer section contains quick action chips, text input, send button, and a payload estimator. It could feel cramped on smaller devices. The input field might need more padding.
- **Send Button Affordance**: The send button is a `Box` with a click listener, but lacks standard ripple effects or shape morphing that comes with standard Material 3 `IconButton` or `Button`.

## 2. SOS Screen (`SosScreen.kt`)

### Strengths
- **Clear Call to Action**: The giant SOS button is unmistakable and easy to tap in high-stress situations.
- **State Changes**: The screen effectively uses color changes (Primary to ErrorRed) and shape changes to indicate when an emergency signal is active.

### Areas for Improvement
- **Information Overload/Scrolling**: The screen requires vertical scrolling to access all options (Connection Method, Incident Type, Location/Medical toggles). In an emergency, cognitive load should be minimized, and all critical controls should ideally fit on one screen without scrolling.
- **Small Tap Targets**: The cancel text inside the active SOS button is small (`10.sp` / `20.sp`). While preventing accidental cancellation is important, the typography could be optimized for readability under stress.
- **Hardcoded Text Colors**: Some text elements use `Color.White` directly, which may cause contrast issues depending on the underlying theme if it were changed.

## 3. Broadcast Screen (`BroadcastScreen.kt`)

### Strengths
- **Focused Task**: The screen is entirely dedicated to a single task—sending a broadcast message—with clear explanatory text.
- **Success Animation**: The use of `AnimatedVisibility` and scaling for the success state provides excellent positive feedback.

### Areas for Improvement
- **Hardcoded Theme References**: The screen imports and uses hardcoded light theme colors (e.g., `LightBackground`, `LightSurface`, `LightBorder`). This prevents the screen from automatically adapting to a system-wide Dark Mode.
- **Input Field Discoverability**: The `BasicTextField` is placed inside a container, but without a clear focus indicator or outline, users might not immediately realize it's a text input area until they tap it.
- **Accessibility**: Missing content descriptions for some visual elements, which could impair screen reader experience.

## General Recommendations
1. **Adopt MaterialTheme Colors**: Replace all hardcoded hex values and specific `Light*` or `Dark*` color references with `MaterialTheme.colorScheme` attributes to ensure seamless light/dark mode support.
2. **Improve Empty States**: Add illustrations and helpful text for empty lists, particularly in the Chat screen.
3. **Optimize for Stress (SOS)**: Consider collapsing advanced SOS settings (like connection method) behind an "Advanced" toggle to keep the primary screen unscrollable and focused solely on the button and incident type.
