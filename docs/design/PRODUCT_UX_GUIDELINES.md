# Nexus Support — Product UI/UX Standards

**Scope:** Android app and cloud incident dashboard  
**Purpose:** Keep emergency workflows fast, legible, accessible, and honest about network/data state.

## Product principles

1. **Emergency actions outrank decoration.** SOS and safety-critical controls must be easy to find, clearly labelled, and difficult to trigger accidentally.
2. **Status is explicit.** Distinguish scanning, connecting, connected, queued, sent, acknowledged, failed, and offline. Never present a local queue or simulation as confirmed delivery.
3. **No fabricated data.** Missing GPS, battery, peer, model, or incident fields must be labelled unavailable; never substitute random values or a plausible-looking location.
4. **Offline-first is a behavior, not a badge.** Keep local messaging and emergency guidance useful without the cloud. Label cloud-only monitoring and map tiles honestly.
5. **Progressive disclosure.** Show the immediate decision first; keep diagnostics, protocol details, and advanced configuration available without overwhelming first-time users.
6. **Accessible by default.** Use semantic labels, adequate touch targets, clear focus, screen-reader announcements for state changes, and reduced-motion support.

## Visual system

- Use Material 3 and the app's theme tokens as the Android source of truth. Do not scatter hard-coded colors through feature screens when a semantic token exists.
- Keep the cloud dashboard aligned with the app's dark emergency-operations palette: deep neutral surfaces, restrained cyan/blue for interaction, red for critical risk, amber for warning, and green only for positive/confirmed state.
- Color must never be the only way to communicate severity or connectivity. Pair color with text/iconography.
- Prefer consistent spacing, rounded surfaces, and clear typographic hierarchy over excessive glow, animation, gradients, or decorative effects.
- Preserve high contrast for small status labels, map popups, and dense incident metadata.

## Critical flows

### SOS

- Make the primary SOS action visually dominant and keyboard/screen-reader discoverable.
- Communicate whether the alert is idle, transmitting, queued, or acknowledged.
- Never label a broadcast "delivered" until an acknowledgement or equivalent protocol evidence exists.
- Keep location/medical-data inclusion visible and under user control.
- Avoid animation that blocks activation or hides the active-alert state; respect system reduced-motion settings.

### Chat and identity

- Keep the recipient and connection state visible while composing.
- Make send state and failed/queued messages understandable without requiring users to inspect logs.
- Present safety-number verification as a deliberate, understandable comparison step.
- Avoid describing a peer as verified unless the cryptographic verification state supports it.

### Discovery and network status

- Distinguish a device discovered nearby from a connected peer and from a peer that can relay.
- Explain permission, Bluetooth, location, battery, and scan-paused states with an actionable next step.
- Do not imply that scanning is active when a master pause or system restriction has stopped it.

### Map and incident dashboard

- Center the map only on valid incident coordinates. When no coordinates exist, show a clear empty state rather than a fake/default incident location.
- Label live WebSocket, reconnecting, polling fallback, and disconnected states.
- Search, severity filters, refresh, and locate controls must be usable on touch screens and with keyboard navigation.
- Errors should be actionable and announced to assistive technology; empty results should be distinct from loading and network failure.
- Treat the dashboard as cloud-connected unless a tested local/offline map and data path is explicitly implemented.

### Offline AI

- Show model availability/loading/unavailable states and make clear when no local model is ready.
- Do not imply generated guidance is a substitute for emergency services or verified field procedures.
- Keep emergency-domain safety checks and the SOS override independent of model latency.
- Never transmit private prompts or location data to cloud services without explicit product behavior and consent.

## Interaction and accessibility requirements

- Aim for touch targets of at least 48 dp on Android and 44 CSS px on web where practical.
- Every icon-only control needs a meaningful accessible name.
- Use visible keyboard focus; do not remove outlines without a replacement.
- Announce authentication errors, connection changes, and meaningful incident updates through appropriate semantics without flooding screen readers.
- Support dynamic text, small-screen layouts, landscape, system font scaling, and reduced motion.
- Avoid hover-only actions. Do not use placeholder text as the only field label.
- Use confirmations for destructive or high-impact actions; do not add confirmation friction to an explicitly designed emergency SOS flow without a safety review.

## Engineering acceptance checklist

- [ ] Loading, empty, error, offline, and success states are distinct.
- [ ] No synthetic/fallback values are shown as real field data.
- [ ] Critical actions are reachable and labelled with TalkBack/VoiceOver or keyboard/screen reader.
- [ ] Contrast, focus, touch target, and dynamic type checks are complete.
- [ ] Reduced-motion preferences are respected.
- [ ] Network reconnect/polling does not create duplicate alerts or stale state.
- [ ] Tests cover state transitions and important failure paths.
- [ ] Physical-device validation is recorded separately from automated tests.
- [ ] Screenshots/recordings and test evidence are tied to the release commit when used for sign-off.

## Review rule

A UI can be described as **implemented** when code exists; it can be described as **verified** only after the relevant tests or manual device checks pass on the exact candidate. These standards do not certify accessibility or field readiness by themselves.
