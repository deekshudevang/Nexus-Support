# Architecture and Baseline Engineering Audit: Nexus-Support

**Date:** 2026-10-11  
**Auditor:** Senior Android Architect / Security & Privacy Lead  
**Scope:** Whole repository (`:app`, `:core:domain`, `:core:crypto`, `:core:mesh`, `:core:data`)

---

## 1. Executive Summary

Nexus-Support is an offline-first disaster response and mesh communications Android application.
The system is built on a clean multi-module architecture:
- `:core:domain` (pure Kotlin models & repository interfaces)
- `:core:crypto` (AndroidKeyStore, ECIES, AES-GCM, ECDH key exchange)
- `:core:mesh` (Nearby Connections transport, routing table, Dijkstra multi-hop, SOS beacon encoder/decoder)
- `:core:data` (Room DB with SQLCipher encryption, Room migrations v1–v14, DAOs)
- `:app` (Jetpack Compose, Material 3, navigation graph, ViewModels, Hilt DI, WorkManager background workers)

### Verification Summary
- **Compilation (L1 Evidence):** `./gradlew assembleDebug` and `./gradlew assembleRelease` pass.
- **Unit Tests (L2 Evidence):** 
  - `:app:testDebugUnitTest`: PASSING (all tests green)
  - `:core:crypto:testDebugUnitTest`: PASSING (all tests green)
  - `:core:data:testDebugUnitTest`: PASSING (all tests green)
  - `:core:mesh:testDebugUnitTest`: 49/50 tests PASSING. Exactly 1 test failing: `SosMessageIdTest.inv3_KNOWN_OPEN_DEFECT_F_twoEmissionsAfter1812_mustHaveDifferentIds`, which is documented in [DEFECT_REGISTER.md](file:///c:/Users/deeks/OneDrive/Desktop/Meshlink--Offline-messaging-main/docs/engineering/DEFECT_REGISTER.md) row F as an intentional failing sentinel for timestamp delta overflow beyond 18:12 UTC.

---

## 2. Architectural Layer Analysis

### 2.1 Domain Layer (`:core:domain`)
- **Strengths:** Pure Kotlin module without Android dependencies. Clear data classes (`MeshPacket`, `MeshMessage`, `KnownDevice`, `SosPacketData`).
- **Dependencies:** None on UI or platform packages.
- **Observations:** Models contain clear invariants (e.g. `VectorClock` and packet types).

### 2.2 Cryptographic Layer (`:core:crypto`)
- **Components:** `EciesService`, `EncryptionService`, `HandshakeManager`, `SessionKeyStore`.
- **Strengths:** Session keys are kept in-memory (`SessionKeyStore`) and never persisted to plaintext storage.
- **Security Posture:** EC P-256 with ECDH key negotiation. Ephemeral symmetric keys use AES-256-GCM.

### 2.3 Mesh & Transport Layer (`:core:mesh`)
- **Components:**
  - `MeshRouter`: Manages epidemic routing, store-and-forward, TTL decrements, and packet dispatching.
  - `RoutingTable`: Dynamic topology cache using Dijkstra shortest path.
  - `SeenMessageCache`: Sliding window deduplication cache using message hash digests.
  - `SosBeaconCodec`: 18-byte compact binary encoder/decoder fitting inside BLE manufacturer data fields.
- **Identified Defects / Risks:**
  - **Defect F (Known):** `timestampDelta` is 16-bit seconds since midnight UTC (max 65535 s ≈ 18:12:15 UTC). Emissions after 18:12 clamp to 65535, causing identical beacon hashes and deduplication of distinct emergency activations. V1 19-byte format (nonce appended) is documented in the defect register.
  - **Defect E (Risk):** `NearbyRepositoryImpl` handles disconnections reactively; silent drops without callback may leave peers marked as `CONNECTED`.
  - **Defect H (Design):** Broadcast packets (SOS) do not generate explicit transport ACKs; UI correctly reflects broadcast state without false delivery claims.

### 2.4 Data & Persistence Layer (`:core:data`)
- **Components:** `AppDatabase` (Room + SQLCipher), DAOs (`DeviceDao`, `MessageDao`, `PendingMessageDao`, `SosPacketDao`).
- **Migrations:** Robust migration ladder from schema version 1 to 14.
- **Offline Queuing:** `PendingMessageRepository` holds unsent mesh messages until next hop or peer becomes reachable.

### 2.5 Presentation Layer (`:app`)
- **Components:** Compose UI, Material 3 theme, ViewModels (`HomeViewModel`, `SosViewModel`, `ChatViewModel`).
- **State Management:** Reactive state flows from Repositories -> ViewModels -> UI.
- **Hygiene:** Verified that UI does not synthesize fake peers or coordinates.

---

## 3. Systematic Phase Alignment & Next Steps

1. **Phase 0 (Baseline & Architecture Audit):** Verified and documented.
2. **Phase 1 (Gradle, Dependencies & Baseline Health):** Unit tests verified across modules. Memory configuration stabilized.
3. **Phase 2–4 (Core Data, Discovery & Routing):** Next immediate focus is addressing Defect F (SOS wire-format v1 nonce migration) and refining peer state invalidation in `NearbyRepositoryImpl`.
