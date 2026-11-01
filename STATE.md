# STATE.md — Nexus Support (RescueMesh) System State

**Last Updated:** 2026-11-01  
**Current Milestone Branch:** `m1-contracts-and-critical-defects`  
**Base Commit:** `f23eb77` (M1 contracts + critical defects E1, E3, E4 resolved)  
**Parent Reference Commit:** `ffc16571af3e372490aed91548cc83a0d69bb509` / `b9de422`  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md`  

---

## 1. Baseline Test Execution Reality

| Module / Test Suite | Runner / Command | Status | Notes |
|---|---|---|---|
| `:core:data:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** (58 tasks, 17 executed, 41 up-to-date) | Room migrations 1->2, 2->3, 3->13, 13->14 verified; Tink AEAD & SQLCipher operational. |
| `:core:crypto:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** | StrongBox/TEE key generation and SAS verification intact. |
| `:core:mesh:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** (87 tasks) | Nearby, BLE SOS, Meshtastic transport, and deterministic SHA-256 node ID tests verified. |
| `:app:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** (87 tasks) | ViewModel test suites and GatewaySyncWorkerTest verified. |
| `cloud/backend` | Python 3.14 / pytest 9.1.1 | **PASSED** (29/29 tests passed) | FastAPI route authentication, WebSocket tickets, nullable GPS, and receipt reconciliation. |
| `cloud/frontend` | Node.js 22 / vitest 5.0.5 | **PASSED** (6/6 tests passed) | React tactical command center, situation room, kanban, radar, copilot. |

---

## 2. Hardware & Device Reality

- **Physical Device:** Connected via ADB (`ZD222M3BNH`, Motorola Edge 50 Pro, Android 16 / SDK 36, arm64-v8a).
- **Existing Pre-built APK:** `app/build/outputs/apk/debug/app-debug.apk` (194.9 MB).
- **Current ADB Status:** Disconnected or awaiting authorization re-handshake; install command queued for execution as soon as device is connected.

---

## 3. Architecture Baseline Reality

1. **Android Client (`app`, `core/data`, `core/domain`, `core/mesh`, `core/crypto`)**:
   - Single cryptographic identity root (Ed25519 / X25519 + StrongBox/TEE).
   - Room schema assets strictly configured in `core/data/build.gradle.kts` (`$projectDir/schemas`). Current active schema version is 13.
   - Offline AI bundle: Gemma 3 1B IT int4 bundle with MediaPipe Tasks GenAI and local Emergency RAG.
   - P2P Mesh Transports: Google Nearby Connections, BLE SOS Beacon advertising/scanning, and Meshtastic GATT transport.

2. **Cloud Backend (`cloud/backend`)**:
   - Single-file FastAPI application (`main.py`) using SQLite database (`sos_mesh.db`).
   - Role separation: Gateway write token vs Operator read token.
   - In-memory WebSocket broadcast hub with single-use authentication tickets.
   - Defect identified: Flat architecture, in-memory tokens, needs PostgreSQL/PostGIS migration + Alembic migrations.

3. **Cloud Frontend (`cloud/frontend`)**:
   - Tactical Command Operations Center (Mil-Spec Dark UI, Lucide icons, Tailwind/CSS).
   - Views: Situation Room (Leaflet tactical map), Incident Triage Kanban (Draggable state progression), Mesh Radar (Live topology & link quality), Broadcast Console (EOC emergency broadcast dispatcher), AI Triage Copilot (RAG-assisted triage).
   - Honest GPS indicator: Coordinates `(0.0, 0.0)` accurately rendered as "No GPS Fix".

---

## 4. Defect Register & Targeted Remediation

- **[E1] GatewaySyncWorker Hardcoding & Reconciliation Defect**: **RESOLVED**
  - *Location:* `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt`
  - *Resolution:* Injected configurable `KEY_GATEWAY_URL` and `KEY_GATEWAY_KEY`, added `X-Gateway-Key` and `Authorization` headers, implemented per-event receipt reconciliation with Room `updateStatus(uuid, "ACKNOWLEDGED")`. Verified with `GatewaySyncWorkerTest`.

- **[E2] Backend In-Memory Token Management**: **PENDING (Milestone M2)**
  - *Location:* `cloud/backend/main.py`
  - *Symptom:* Operator and Gateway tokens stored in environment list/memory, without persistent user credentials, audit logging, or role revocation.
  - *Remedy:* Milestone M2: Migrate to PostgreSQL/PostGIS with persistent users, hashed passwords/tokens, and RBAC table.

- **[E3] Nullable GPS Constraint Violation in Pydantic Schema**: **RESOLVED**
  - *Location:* `cloud/backend/main.py`
  - *Resolution:* Updated `SosPacket` schema with nullable latitude/longitude and `gpsFixAvailable: bool`. Updated database schema and added automatic column migration. Verified with `test_sync_sos_nullable_gps_and_receipts` (29 passed).

- **[E4] MeshtasticTransport Premature Success & String HashCode**: **RESOLVED**
  - *Location:* `core/mesh/src/main/java/com/meshlink/app/mesh/transport/MeshtasticTransport.kt`
  - *Resolution:* Implemented `deterministicNodeId` and `deterministicMessageId` using SHA-256 byte extraction. Added `writeMutex` and `pendingWriteDeferred` awaiting `onCharacteristicWrite` callback with 5s timeout. Verified with `MeshtasticProtoSerializationTest`.

- **[E5] Human Approval Gates**:
  - *Offline Enrollment Policy:* Documented in `docs/policy/offline-enrollment.md`. Requires human approval prior to coding.
  - *Medical Break-Glass Policy:* Documented in `docs/policy/break-glass.md`. Requires human approval prior to coding.

- **[E6] Backend Ingestion Idempotency**: **IN-PROGRESS (Milestone M2)**
  - *Location:* `cloud/backend/main.py`
  - *Symptom:* Lacks transactional deduplication on event IDs.
  - *Remedy:* Milestone M2: Idempotent ingest endpoint with `ON CONFLICT (event_id) DO NOTHING`.
