# STATE.md — Nexus Support (RescueMesh) System State

**Last Updated:** 2026-11-01  
**Current Milestone Branch:** `m0-baseline-audit`  
**Base Commit:** `a1c92c5` (Staged modern dashboard + backend token alignment on `release/production-readiness`)  
**Parent Reference Commit:** `ffc16571af3e372490aed91548cc83a0d69bb509` / `b9de422`  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md`  

---

## 1. Baseline Test Execution Reality

| Module / Test Suite | Runner / Command | Status | Notes |
|---|---|---|---|
| `:core:data:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** (58 tasks, 17 executed, 41 up-to-date) | Room migrations 1->2, 2->3, 3->13, 13->14 verified; Tink AEAD & SQLCipher operational. |
| `:core:crypto:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** | StrongBox/TEE key generation and SAS verification intact. |
| `:core:mesh:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** | Nearby, BLE SOS, Meshtastic transport unit tests verified. |
| `:app:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | **PASSED** (87 tasks, 8 executed, 79 up-to-date) | ViewModel test suites (SosViewModel, ChatViewModel, Navigation, etc.). |
| `cloud/backend` | Python 3.14 / pytest 9.1.1 | **PASSED** (28/28 tests passed) | FastAPI route authentication, WebSocket tickets, and validation constraints. |
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

- **[E1] GatewaySyncWorker Hardcoding & Reconciliation Defect**:
  - *Location:* `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt`
  - *Symptom:* Hardcoded `http://10.0.2.2:8000/api/v1/sync/sos` URL; missing gateway auth header; lacks per-event sync receipt reconciliation.
  - *Remedy:* Milestone E1 / M1: inject dynamic `GatewayConfigRepository`, append `X-Gateway-Key`, update local Room event sync status per response receipt.

- **[E2] Backend In-Memory Token Management**:
  - *Location:* `cloud/backend/main.py`
  - *Symptom:* Operator and Gateway tokens stored in environment list/memory, without persistent user credentials, audit logging, or role revocation.
  - *Remedy:* Milestone M2: Migrate to PostgreSQL/PostGIS with persistent users, hashed passwords/tokens, and RBAC table.

- **[E3] Nullable GPS Constraint Violation in Pydantic Schema**:
  - *Location:* `cloud/backend/main.py` (`SOSCreate` / `IncidentCreate`)
  - *Symptom:* Requires lat/long floats, rejecting SOS when victim has no GPS lock.
  - *Remedy:* Milestone M1: Make `latitude` and `longitude` `Optional[float] = None` with explicit `gps_fix_available: bool`.

- **[E4] MeshtasticTransport Premature Success & String HashCode**:
  - *Location:* `core/mesh/src/main/java/com/meshlink/app/mesh/transport/MeshtasticTransport.kt`
  - *Symptom:* `send()` returns `true` immediately after scheduling write without waiting for GATT onCharacteristicWrite callback; node IDs derived via unstable `String.hashCode()`.
  - *Remedy:* Milestone E4 / M3: Use `CompletableDeferred<Boolean>` awaiting GATT callback; use SHA-256 byte slice for stable node IDs.

- **[E5] Human Approval Gates**:
  - *Offline Enrollment Policy:* Documented in `docs/policy/offline-enrollment.md`. Requires human approval prior to coding.
  - *Medical Break-Glass Policy:* Documented in `docs/policy/break-glass.md`. Requires human approval prior to coding.

- **[E6] Backend Ingestion Idempotency**:
  - *Location:* `cloud/backend/main.py`
  - *Symptom:* Lacks transactional deduplication on event IDs.
  - *Remedy:* Milestone M2: Idempotent ingest endpoint with `ON CONFLICT (event_id) DO NOTHING`.
