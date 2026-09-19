# Changelog

All notable changes to **Nexus Support** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-19

### 🚀 Added
- **Multi-Hop Mesh Routing**: Dijkstra shortest-path mesh routing engine supporting up to 7 hops over Google Nearby Connections.
- **Hardware-Backed Security**: StrongBox KeyStore support with automatic TEE fallback for EC P-256 identity keys.
- **Packet Signing & Anti-Replay**: Cryptographic ECDSA SHA-256 packet signatures bound to unique `eventId` tokens with timestamp window validation.
- **Split-Horizon Forwarding & Rate Limiting**: Anti-echo packet forwarding and heartbeat rate-limiting (1/5s) to defend against Sinkhole and Sybil attacks.
- **CRDT / Vector Clock Location Sync**: High-accuracy GPS location sharing synchronized across peers using causal Vector Clock ordering and conflict-free replicated data structures.
- **Offline Maps Engine**: Mapsforge vector tile loader and cache manager for full offline map rendering without cellular or internet data.
- **Encrypted Database at Rest**: SQLCipher AES-256 integration securing Room entities, messages, location events, and cryptographic keys.
- **Production Hardening**: Complete ProGuard / R8 minification and resource shrinking, hardened `network_security_config.xml`, and strict Android backup exclusions.
- **Large-Scale Simulation Suite**: 20-node JVM test scenarios covering linear chains, dense mesh flooding, network partitions, churn, and heartbeat floods.
- **Emergency Medical Profile & SOS**: User-editable medical emergency information and high-priority flood broadcast protocol.

---

## [0.1.0] - Initial Prototype
- Initial prototype of Nearby Connections P2P transport and basic Jetpack Compose interface.
