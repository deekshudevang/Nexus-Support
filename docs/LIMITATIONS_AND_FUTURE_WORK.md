# Nexus Support — Limitations and Future Work

> **Project Classification**: Academic Engineering Prototype (3rd-Year University Project).  
> **Status**: Functional proof-of-concept and operational demonstration prototype. Not certified for mission-critical life-safety or military-grade tactical deployment.

---

## 1. Current Engineering Limitations

### 1.1 Wire Format Overhead (JSON vs. Binary Encoding)
- **Current State**: Mesh packets (`MeshPacket.kt`) and cloud sync payloads are serialized using UTF-8 JSON strings.
- **Impact**: While JSON provides excellent debugging transparency and rapid cross-platform schema evolution, JSON payloads typically require 350 to 800 bytes per emergency packet. On high-bandwidth Wi-Fi Direct transports, this overhead is negligible. However, on narrowband physical LoRa radio links (which operate at maximum transmission units of 256 bytes and channel datarates between 250 bps and 5.4 kbps), JSON payloads exceed single-packet MTUs, forcing packet fragmentation.
- **Status Note**: Migration to Protocol Buffers (`protobuf-kotlin` / `IMP-HV-01`) was evaluated and planned, but intentionally cancelled for the M3.24 milestone to preserve cryptographic envelope stability and avoid breaking established Tink AEAD interfaces before final project evaluation.

### 1.2 Offline Map Tile Rendering Architecture
- **Current State**: The tactical web command center supports offline raster tiles through `VITE_LOCAL_TILE_URL` pointing to a local LAN HTTP server (`docs/OFFLINE_TILES_GUIDE.md`).
- **Impact**: Operating in a disaster zone requires pre-downloading and hosting pre-rendered XYZ PNG tiles for the specific area of interest. The web dashboard does not embed an offline vector tile rendering engine (e.g. MapLibre GL JS with raw `.pbf` / `.mbtiles` files) directly in client-side IndexedDB.

### 1.3 Physical LoRa Radio Driver Integration
- **Current State**: The Android mesh architecture provides an extensible transport interface (`MeshTransport.kt`), but the active production implementation relies on Google Nearby Connections (`NearbyConnectionsTransport.kt`).
- **Impact**: Connecting physical LoRa transceivers (such as Heltec V3 or LilyGO T-Beam ESP32 devices running Meshtastic firmware) via USB-OTG serial or BLE serial GATT requires flashing companion hardware and binding hardware-specific serial bridges.

### 1.4 Cursor-on-Target (CoT) and ATAK Interoperability
- **Current State**: Ingestion and dispatch payloads use proprietary Nexus Support JSON schemas (`/api/v1/sync/sos` and `/api/v1/dispatch/assignments`).
- **Impact**: The platform does not currently parse or generate Cursor-on-Target (CoT) XML or protobuf events required for native interoperability with standard defense/first-responder software ecosystems like ATAK (Android Tactical Assault Kit) or WinTAK.

### 1.5 Decentralized Identity & Public Key Pinning
- **Current State**: Edge cryptographic security relies on Google Tink with local key derivation, and gateway synchronization uses pre-shared secrets (`X-Gateway-Key` injected via `local.properties`).
- **Impact**: Ad-hoc mobile nodes discovering each other over RF do not possess cryptographically signed identity certificates rooted in a mutual trust authority or decentralized Web-of-Trust (WoT). While payloads are encrypted, an active local RF adversary could theoretically stage an initial man-in-the-middle during key exchange if out-of-band verification is omitted.

### 1.6 Concurrency and Geospatial Scalability Ceilings
- **Current State**: The cloud backend utilizes a single PostgreSQL 15 instance with PostGIS and in-memory FastAPI WebSocket connection management.
- **Impact**: Tested and validated for disaster coordination cells serving hundreds of concurrent field assets and dozens of simultaneous incidents. It is not architected for multi-region geo-distributed high-availability clusters or multi-million node mesh partitions.

---

## 2. Future Work Roadmap

| Phase | Milestone | Technical Scope & Objectives |
| :---: | :--- | :--- |
| **Phase 1** | **Binary Serialization (Protobuf)** | Replace JSON mesh serialization with Protobuf schemas to compress emergency packets below 180 bytes, eliminating fragmentation on LoRa SX1262 channels. |
| **Phase 2** | **Direct LoRa Serial/BLE Driver** | Implement a native `LoraSerialTransport.kt` implementing `MeshTransport` to directly drive USB-OTG serial and Meshtastic BLE GATT hardware transceivers. |
| **Phase 3** | **CoT / ATAK Gateway Bridge** | Add a bidirectional translation microservice converting Nexus Support SOS and responder telemetry into CoT XML messages over UDP multicast (port 4242). |
| **Phase 4** | **Decentralized Web-of-Trust (WoT)** | Implement cryptographic QR-code peer fingerprint verification and threshold-based trust gossip to secure ad-hoc mesh key exchanges without central servers. |
| **Phase 5** | **Client-Side Vector Map Caching** | Migrate frontend map engine to MapLibre GL JS with local IndexedDB storage of vector MBTiles for zero-server offline mapping on disaster command laptops. |
