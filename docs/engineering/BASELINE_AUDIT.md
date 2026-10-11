# BASELINE_AUDIT.md

## Repository: deekshudevang/Nexus-Support
**Branch:** `feature/offline-ai`  
**Commit:** `8b9b948`  
**Target:** 9.5/10 Engineering Quality across all 6 core categories.

---

## 1. Environment & Build Baseline
- **Operating System:** Windows 11 (PowerShell)
- **Connected Physical Device:** Motorola edge 50 pro (`ZD222M3BNH`), Android 16 (API 36, arm64-v8a), 11.5 GB RAM, 154 GB storage free.
- **Java / JDK:** Microsoft OpenJDK 21.0.12.1
- **Gradle:** 8.14.5, AGP 8.10.1, Kotlin 2.0.21
- **Modules in Workspace:**
  - `:app`: Jetpack Compose UI, ViewModels, Offline AI assistant, Navigation, App initialization.
  - `:core:domain`: Entities, Models, Repository interfaces, Result types.
  - `:core:crypto`: ECDH P-256 key exchange, HKDF, AES-256-GCM, Keystore/StrongBox encapsulation.
  - `:core:data`: Room DB (SQLite/SQLCipher), DAOs, Entities, Device/Message repositories.
  - `:core:mesh`: Google Nearby Connections, BLE advertising/scanning, Meshtastic LoRa, MeshRouter, RoutingTable, SeenMessageCache, Packet serialization.
- **Initial Test Suite Run:**
  - `.\gradlew.bat testDebugUnitTest --console=plain`
  - Exit code: 0
  - Total Passing Unit Tests: 346 tests across all 5 modules.
  - Failures: 0.

---

## 2. Component Audits & Gap Analysis

### Category 1: AI Implementation & Safety (Current: ~8.8/10)
- **Strengths:** 
  - LiteRT / MediaPipe `tasks-genai:0.10.14` integrated with Gemma 1B / Gemma 2B execution.
  - Emergency RAG with high retrieval accuracy and strict abstention (`RagConstants.ABSTENTION_MESSAGE`).
  - Offline cyberattack and offensive SQL hacking filters implemented and live-tested on physical device.
  - AI enable toggle persisted in `SharedPreferences`.
  - Candidate model directory search (`ai_models`, `models`, `filesDir`).
- **Gaps / Remaining Work:**
  - Model lifecycle under concurrent loads and low memory pressure.
  - Output validation post-generation to prevent hallucinations of confirmed physical actions or SOS delivery.
  - Bounded inference timeout and coroutine cancellation guarantees.
  - Documenting model limitations in `docs/ai/AI_SAFETY_AND_LIMITATIONS.md`.

### Category 2: Mesh and SOS Reliability (Current: ~7.5/10)
- **Strengths:**
  - 19-byte v1 SOS beacon with per-activation nonce and CRC-16 backwards compatibility.
  - Dijkstra shortest path routing and split-horizon forwarding in `MeshRouter`.
  - SeenMessageCache deduplication.
- **Gaps / Critical Defects:**
  - **Defect E (P1):** Stale `CONNECTED` and `AUTHENTICATED` peer entries. When a peer powers down or moves out of range without graceful disconnect, entries remain active. Missing liveness detection / heartbeat timeout.
  - **Defect H (P1):** No delivery confirmation or acknowledgement for SOS messages. Senders have no feedback whether nearby nodes or emergency personnel received their distress beacon.
  - **Defect J (P3):** `DISCONNECTED` map entries never pruned in `NearbyRepositoryImpl._connectionStates`.
  - Need bounded retry with backoff and store-and-forward queueing for SOS.

### Category 3: Security Engineering (Current: ~8.0/10)
- **Strengths:**
  - AES-256-GCM encryption with 12-byte IV for peer-to-peer unicast messages.
  - ThreatModelValidationTest covering packet tampering, replay detection, and flood rate-limiting.
- **Gaps / Remaining Work:**
  - Full application threat model documentation in `docs/security/THREAT_MODEL.md`.
  - Input packet bounds verification: prevent large memory allocations on malformed/corrupted frames.
  - Protection against nonce reuse and spoofed ACK control packets.

### Category 4: Automated Testing & CI (Current: ~8.2/10)
- **Strengths:**
  - 346 passing unit tests across modules.
  - Fast execution (~22s clean run).
- **Gaps / Remaining Work:**
  - GitHub Actions workflow improvements (`.github/workflows/android.yml`) to ensure reliable CI execution, caching, and release checks.
  - Lack of regression tests for stale peer expiration and SOS delivery acknowledgement.

### Category 5: Release Readiness (Current: ~8.0/10)
- **Strengths:**
  - Minification and R8 shrinking configured for release builds.
  - Debug APK installs cleanly on Android 16 device.
- **Gaps / Remaining Work:**
  - Formal release guide `docs/engineering/RELEASE_GUIDE.md`.
  - Distinction between unsigned local builds and production signing.

### Category 6: Documentation & Maintainability (Current: ~8.0/10)
- **Strengths:**
  - Good initial architecture docs for Offline AI and defect registers.
- **Gaps / Remaining Work:**
  - Updating `DEFECT_REGISTER.md` to reflect fixes.
  - Updating `TESTING_GUIDE.md`, `MESH_PROTOCOL.md`, and `THREAT_MODEL.md`.
