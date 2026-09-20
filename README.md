<div align="center">

# ⚡ Nexus Support
### Decentralized Emergency Mesh Communication & Geospatial Intelligence

[![CI Pipeline](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml/badge.svg)](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml)
[![CodeQL](https://github.com/deekshudevang/Nexus-Support/actions/workflows/codeql.yml/badge.svg)](https://github.com/deekshudevang/Nexus-Support/actions/workflows/codeql.yml)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

<p align="center">
  <b>A multi-hop mesh network for disaster relief, off-grid operations, and field deployments.</b>
  <br />
  Operates completely independent of cellular towers, satellite uplinks, or centralized servers.
</p>

</div>

## 📖 Overview

**Nexus Support** connects Android smartphones into an ad-hoc mesh network. Using a hybrid combination of **Bluetooth Low Energy (BLE)** and **Wi-Fi Direct** via Google Nearby Connections, devices dynamically discover peers, establish encrypted links, and route emergency messages and GPS coordinates.

## ⚡ Key Features

* **Multi-Hop Routing:** Dijkstra shortest-path pathfinding across ad-hoc graphs (up to 7 hops).
* **Zero-Internet Maps:** Offline Mapsforge vector tile engine rendering topo & street maps.
* **Store & Forward:** Persistent message buffer (48h TTL) for disconnected peers.
* **End-to-End Encryption:** Direct AES-256-GCM sessions + multi-hop ECIES (Ephemeral ECDH + HKDF SHA-256).
* **At-Rest Encryption:** Room SQLite database encrypted via SQLCipher AES-256-CBC.
* **Tamper Proofing:** ECDSA SHA-256 signatures with temporal validity windows.

## 📚 Documentation

Deep technical specs and architectural decisions (ADRs) are maintained in the [`docs/`](docs/) directory:

- 🏛️ [**Architecture & Submodules**](docs/ARCHITECTURE.md)
- 📡 [**Mesh Routing & Protocol Specification**](docs/MESH_PROTOCOL.md)
- 🔐 [**Cryptographic Security Specification**](docs/SECURITY_SPEC.md)
- 🗺️ [**Offline Maps & GPS Engine**](docs/OFFLINE_MAPS.md)
- 🏗️ [**Architecture Decision Records (ADRs)**](docs/adr/)

## 🚀 Getting Started

### Prerequisites
- JDK 17+
- Android Studio
- Physical Devices: 2 or more Android devices (API 26+) for hardware radio testing

### Clone & Build

```bash
git clone https://github.com/deekshudevang/Nexus-Support.git
cd Nexus-Support

# Build debug APK
./gradlew assembleDebug
```

## 🧪 Testing

```bash
# Run JVM unit tests
./gradlew test

# Run mesh simulation suite
./gradlew :core:mesh:test --tests "com.meshlink.app.mesh.routing.LargeScaleMeshSimTest"
```

## 🗺️ Roadmap & Known Issues

Check our GitHub Issues for ongoing tasks. Major upcoming items:
- **Field Testing**: Validating store-and-forward TTLs under real-world drop-offs.
- **Test Coverage**: Expanding tests for ViewModels and edge-case mesh packet routing.

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions and guidelines.

## 📄 License
This project is licensed under the **MIT License** — see [LICENSE](LICENSE).
