# Nexus Support — Verification & Testing Protocol

## 1. Overview & Verification Philosophy

Nexus Support follows a **Verification-First Protocol**: no feature, bug fix, or security mitigation is considered complete without empirical evidence captured from clean execution of automated test suites.

The testing strategy spans three distinct tiers:
1. **Cloud Backend**: Python `pytest` suite exercising REST endpoints, WebSocket hubs, PostGIS spatial queries, state machine transitions, multi-tenant isolation, and automated demo integration.
2. **Cloud Frontend**: React / Vitest suite validating tactical map rendering, Leaflet interactions, zero-fabrication empty states, and offline tile fallbacks.
3. **Android Client**: Gradle JUnit / Robolectric / MockWebServer suite validating Room migrations, SQLCipher encryption, Tink AEAD crypto, MeshRouter priority queues, WorkManager sync, and notification permissions.

---

## 2. Test Execution Commands & Baseline Results

### 2.1 Cloud Backend Test Suite

* **Prerequisites**: Python 3.11+, virtual environment activated, dependencies installed (`pip install -r cloud/backend/requirements.txt`).
* **Execution Command**:
  ```bash
  # Run entire backend test suite from repository root
  py -m pytest cloud/backend -q
  ```
* **Single Integration Flow Run**:
  ```bash
  py -m pytest cloud/backend/test_pg_p33_demo_flow_integration.py -v
  ```
* **Observed Baseline**: **128 passed in 43.35s** (100% passing).
* **Coverage Scope**:
  - `test_backend.py`: Health endpoint diagnostics, gateway auth, dev key rejection in production.
  - `test_pg_p33_demo_flow_integration.py`: End-to-end demo lifecycle (SOS $\to$ Incident $\to$ Dispatch $\to$ Downlink $\to$ Status Transitions).
  - `test_tenant_isolation.py`: Negative authorization tests preventing cross-organization incident access.
  - `test_spatial_dispatch.py`: PostGIS proximity matching and boundary calculations.
  - `test_assignment_transitions.py`: State transition graph validation (409 Conflict on invalid order).
  - `test_import_sqlite_to_pg.py`: Legacy SQLite data migration CLI verification.

### 2.2 Cloud Frontend Test Suite & Production Build

* **Prerequisites**: Node.js 18+, frontend dependencies installed (`npm --prefix cloud/frontend install`).
* **Execution Command**:
  ```bash
  # Windows PowerShell
  npm --prefix cloud/frontend test -- --run ; if ($LASTEXITCODE -eq 0) { npm --prefix cloud/frontend run build }

  # Linux / macOS / Bash
  npm --prefix cloud/frontend test -- --run && npm --prefix cloud/frontend run build
  ```
* **Observed Baseline**: **17 passed across 3 test files in 4.57s**; production build completed cleanly in 694ms.
* **Coverage Scope**:
  - `TacticalMap.test.jsx`: Base layer toggling (RF Mesh, Topo, Sat), responder plotting from live coordinates, `ONLINE TILES` / `OFFLINE TILES` / `NO MAP TILES` status badge updates, offline warning alerts.
  - `App.test.jsx`: Application shell, WebSocket event listener connection, and navigation.
  - `TacticalCommandCenter.test.jsx`: Zero-Fabrication empty state verification, responder asset statistics.

### 2.3 Android Mobile Application Test Suite

* **Prerequisites**: JDK 21 configured (`JAVA_HOME`), Android SDK installed.
* **Execution Command**:
  ```bash
  # Windows
  .\gradlew.bat clean testDebugUnitTest

  # Linux / macOS
  ./gradlew clean testDebugUnitTest
  ```
* **Observed Baseline**: **BUILD SUCCESSFUL in 1m 6s, 149 actionable tasks executed** (100% passing from clean state).
* **Coverage Scope**:
  - `:app`:
    - `AssignmentNotificationHelperTest.kt`: Robolectric tests validating heads-up notifications across Android 13+ (API 33) permission-granted, permission-denied, and pre-API 33 devices.
    - `GatewaySyncWorkerTest.kt` & `CloudSyncWorkerTest.kt`: WorkManager outbox sync and network retry handling.
    - `SosViewModelTest.kt`: Emergency trigger, cancellation, and state management.
    - `EmergencyGuideViewModelTest.kt`: Offline triage flow.
  - `:core:mesh`:
    - `MeshRouterTest.kt`: Priority queue preemption (P10 SOS priority), packet deduplication, TTL hops.
    - `MeshSecurityHardeningTest.kt`: Replay attack protection and malformed packet rejection.
  - `:core:data`:
    - `AppDatabaseMigrationTest.kt`: Room schema migrations 1 $\to$ 2, 2 $\to$ 3, 3 $\to$ 13, 13 $\to$ 14.
    - `OutboxRepositoryTest.kt`: Transactional outbox persistence and status updates.
    - `LocationSyncTest.kt`: MockWebServer integration for responder location updates.
  - `:core:crypto`:
    - `TinkCryptoManagerTest.kt`: ECIES encryption, AES-GCM envelope security, and key rotation.

---

## 3. What is NOT Covered (Strict Honesty & Limitations)

To maintain absolute release integrity, the following areas are documented as **NOT COVERED** by automated tests in this environment:

1. **Physical RF Radio Transceiver Hardware**:
   Tests run against software abstractions (`NearbyConnectionsTransport.kt`, Mock transports) and Robolectric. Over-the-air RF performance on physical Heltec LoRa ESP32 or Semtech SX1262 transceivers requires dedicated hardware in the test loop.
2. **Real-World Mesh Multipath & Austere RF Interference**:
   Multi-hop ad-hoc mesh routing is verified algorithmically in unit tests, but real-world RF degradation (concrete penetration, multipath fading, rain attenuation, extreme node mobility) cannot be modeled in JVM unit tests.
3. **Live Docker Daemon Execution on Current Windows Host**:
   Docker Compose and Dockerfile specifications have been verified statically and file layouts checked, but the live Docker daemon was not running during this session.
4. **Physical Android Device Battery Profiling**:
   Continuous BLE background scanning and Wi-Fi Direct socket maintenance under long-duration field deployments require hardware battery bench profiling.
