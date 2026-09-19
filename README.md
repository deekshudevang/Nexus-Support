<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Nexus Support Logo" width="100"/>

# Nexus Support — Offline Emergency Mesh Communication Platform

**Peer-to-peer encrypted mesh chat and location sharing that works without internet or cellular signal.**  
Built with Android Nearby Connections API, Jetpack Compose, and Clean Architecture.

[![Android](https://img.shields.io/badge/Platform-Android%2026+-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-brightgreen)](https://github.com/deekshudevang/Nexus-Support/releases)
[![GitHub last commit](https://img.shields.io/github/last-commit/deekshudevang/Nexus-Support)](https://github.com/deekshudevang/Nexus-Support/commits/master)

</div>

---

## 📖 Table of Contents

- [About](#-about)
- [Features](#-features)
- [Security Architecture](#-security-architecture)
- [Architecture](#-architecture)
- [Module Structure](#-module-structure)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [Testing](#-testing)
- [License](#-license)

---

## 🌐 About

**Nexus Support** is an offline-first Android application that enables real-time peer-to-peer messaging and geospatial location sharing between nearby devices using the **Google Nearby Connections API** — no internet, no SIM card required.

It is designed for situations where traditional communication infrastructure is unavailable: disaster zones, remote areas, large events, or campus emergencies. Messages and location events hop across multiple devices to extend reach beyond direct range, forming a true **multi-hop mesh network**.

This project proves that a genuine offline-first, multi-hop, store-and-forward emergency mesh is buildable on commodity Android hardware.

---

## ✨ Features

| Category | Feature |
|---|---|
| **Mesh Networking** | Multi-hop routing (up to 7 hops) via Dijkstra shortest-path |
| **Messaging** | AES-256-GCM encrypted direct chat; ECIES multi-hop routed chat |
| **Location** | HIGH_ACCURACY GPS; CRDT/Vector Clock location sync across mesh |
| **Store & Forward** | Messages queued up to 48h and delivered when peers reconnect |
| **Offline Maps** | Mapsforge vector tiles downloadable for fully offline map rendering |
| **Medical Profile** | User-editable emergency contacts, blood group, allergies, medications |
| **SOS** | High-priority emergency flood broadcast across entire mesh |
| **Cloud Sync** | WorkManager syncs pending events when internet returns |

---

## 🔐 Security Architecture

Nexus Support implements a layered security model suitable for emergency deployments:

### Cryptographic Identity
- **EC P-256 key pair** generated in Android KeyStore on first launch
- **StrongBox hardware module** used automatically on supported devices (Pixel 3+)
- Keys are **never exported** from the KeyStore

### Packet Signing & Replay Prevention
- Every location event is signed: `ECDSA(SHA-256, eventId:peerId:lat:lon:accuracy:timestamp:seq)`
- `eventId` is bound to the signature — prevents replay of valid signatures on different payloads
- Events with timestamps **>24 hours old or >60 seconds in the future** are rejected

### Routing Hardening
- **Heartbeat rate-limiting**: max 1 heartbeat per peer per 5 seconds — blocks Sinkhole/Sybil flooding
- **Split-horizon forwarding**: packets never echoed back to the endpoint they arrived from
- **SeenMessageCache**: cryptographic deduplication prevents broadcast loops

### Data at Rest
- **SQLCipher AES-256**: entire Room database encrypted at rest
- Passphrase derived from a hardware-backed 256-bit AES key stored in AndroidKeyStore
- `allowBackup=false`: prevents device backup from leaking encrypted DB

### Network
- **Cleartext HTTP blocked** in release builds via `network_security_config.xml`
- All cloud sync uses HTTPS only

---

## 🏗 Architecture

```
┌────────────────────────────────────────────────────────┐
│                        App Layer                       │
│   UI (Compose) → ViewModels → UseCases → Repositories │
└───────────────────────┬────────────────────────────────┘
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
    core:data      core:mesh     core:crypto
   (Room/SQLite   (MeshRouter,   (CryptoManager,
    encrypted)     RoutingTable)  ECIES, AES-GCM)
                        │
                        ▼
               core:domain (models, interfaces)
```

---

## 📦 Module Structure

| Module | Responsibility |
|---|---|
| `app` | UI, ViewModels, Navigation, WorkManager scheduling |
| `core:domain` | Entities, repository interfaces, use cases |
| `core:data` | Room (SQLCipher), DAOs, Retrofit cloud sync |
| `core:mesh` | MeshRouter, RoutingTable, SeenMessageCache, BatteryMonitor |
| `core:crypto` | CryptoManager (KeyStore), EncryptionService (AES-GCM), EciesService |

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.51 |
| Database | Room 2.8 + SQLCipher 4.6 (AES-256 encrypted) |
| Background | WorkManager 2.10 (cleanup + cloud sync) |
| Networking | Google Nearby Connections 19.3 |
| Maps | OsmDroid 6.1 + Mapsforge 0.20 (fully offline) |
| Location | Google Play Services Location (HIGH_ACCURACY) |
| Cryptography | Android KeyStore + EC P-256 + AES-256-GCM + ECIES |
| Build | Gradle 8.14 + R8 (minification + shrinking enabled) |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Meerkat or later
- Android device/emulator running **API 26+**
- Two or more physical devices for mesh testing (Nearby Connections requires real hardware)

### Build

```bash
git clone https://github.com/deekshudevang/Nexus-Support.git
cd Nexus-Support
./gradlew assembleDebug
```

### Release Build

```bash
./gradlew assembleRelease
```

R8 minification, resource shrinking, and SQLCipher are all active in release.

---

## 🧪 Testing

### Unit Tests

```bash
# All mesh routing tests (runs on JVM, no device needed)
./gradlew :core:mesh:test

# Large-scale 20-node mesh simulation (7 scenarios)
./gradlew :core:mesh:test --tests "com.meshlink.app.mesh.routing.LargeScaleMeshSimTest"

# CRDT/Vector Clock tests
./gradlew :app:test --tests "com.meshlink.app.location.LocationSyncManagerTest"
```

### Test Scenarios Covered

| Scenario | What it validates |
|---|---|
| 20-node linear chain | End-to-end broadcast within TTL |
| 20-node fully connected | SeenMessageCache deduplication under dense flooding |
| Partition + reconnect | Store-and-forward delivery on link restoration |
| 10 churn join/leave | Routing table convergence under peer instability |
| 1000 heartbeat flood | Rate-limiter blocks Sinkhole/Sybil attacks |
| TTL wall | Packet dies exactly at `maxHops` boundary |
| Split-horizon | No echo back to source endpoint |
| CRDT out-of-order delivery | Vector Clock handles non-contiguous sequences |

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.
