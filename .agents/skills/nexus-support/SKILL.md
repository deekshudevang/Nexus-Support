---
name: nexus-support
description: Guidelines and context for working on the Nexus Support decentralized emergency mesh communication Android app.
---

# Nexus Support Skill

Use this skill when developing, refactoring, or reviewing code for the Nexus Support Android application.

## Overview
Nexus Support connects Android smartphones into an ad-hoc mesh network. Using a hybrid combination of Bluetooth Low Energy (BLE) and Wi-Fi Direct via Google Nearby Connections, devices dynamically discover peers, establish encrypted links, and route emergency messages and GPS coordinates.

## Architectural Guidelines
The project follows **Clean Architecture**, **Domain-Driven Design**, and **Zero-Trust Offline Operation**.

### Module Breakdown
- `:core:domain`: Pure Kotlin layer, independent of Android SDK/UI. Contains business logic (`MeshPacket`, repositories). Testable 100% on JVM.
- `:core:crypto`: Cryptographic security layer (EC P-256, AES-256-GCM, ECDH).
- `:core:mesh`: Transport abstraction, multi-hop routing (Dijkstra), Nearby Connections.
- `:core:data`: Persistence (Room with SQLCipher) and cloud synchronization.
- `:app`: Jetpack Compose UI, ViewModels, AndroidX WorkManager.

## Contribution Rules
1. **Atomic Commits:** Keep commits small and focused. Reference GitHub issues.
2. **Testing is Mandatory:** Fixes need failing tests first. Add unit tests for `core/mesh` or `core/crypto`.
3. **ADRs:** Submit an Architecture Decision Record in `docs/adr/` for major changes.
4. **Docs Sync:** Keep documentation (`FINAL_AUDIT.md`, `DEVICE_TEST_MATRIX.md`) strictly coupled to code. Update the `**Last verified:**` line in `FINAL_AUDIT.md` with your commit hash.
5. **Code Style:** Use standard Kotlin conventions. Do not over-comment. Focus on the *why* (`// HACK:`, `// NOTE:`).

## Technical Requirements
- JDK 17+
- Android Studio
- Physical Android devices (API 26+) required for hardware radio testing (emulators not suitable for Nearby Connections).
