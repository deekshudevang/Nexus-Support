# STATE.md — Nexus Support System State

**Phase:** PG-P0 through PG-P9 COMPLETE (Full Implementation & Verification Achieved)  
**Branch:** `pg-p9-production-readiness`  
**Base Commit:** `ab763ad` (Head of PG-P8)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Implementation Prompt v2  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| Full Backend Test Suite (9 test files) | **72 / 72 PASSED** (100% green) | Python 3.14 (FastAPI + SQLAlchemy) | `docs/evidence/PG-P9-production-readiness.md` |
| Vitest Frontend Test Suite | **9 / 9 PASSED** (100% green) | Node.js 22 (Vite / React / RTL) | `docs/evidence/PG-P6-command-center-ui.md` |
| Frontend Production Build | **BUILT SUCCESSFUL in 968ms** | Vite 8 / Zero leaked tokens in dist | `cloud/frontend/dist` |
| Android `:core:data:testDebugUnitTest` | **58 tests PASSED** | Java 21 / Room v1..v14 / SQLCipher | `docs/evidence/PG-P7-android-integration.md` |
| Android `:app:testDebugUnitTest` | **87 tests PASSED** | Java 21 / ViewModels / GatewaySync | `docs/evidence/PG-P7-android-integration.md` |
| E2E Operational Flows A through F | **6 / 6 PASSED** | Python 3.14 / TestClient | `docs/evidence/PG-P8-e2e-flows.md` |
| SQLite ➔ Target DB Restore Drill | **100% Verified, SHA-256 Unaltered** | Read-Only Idempotent Upsert Drill | `docs/evidence/PG-P9-production-readiness.md` |

---

## 2. Key Architecture Decisions (ADR Summary)

- **ADR-001 (Strangler Layout):** Modular code lives in `cloud/backend/app/`, with `main.py` acting as compatibility shim preserving legacy endpoints.
- **ADR-002 (Dual-Dialect DB):** Synchronous SQLAlchemy 2.x + psycopg 3 with SQLite fallback for offline edge testing.
- **ADR-003 (3 State Machines):** Independent lifecycles for Incidents, Assignments, and Responder Availability. Response state dynamically derived.
- **ADR-004 (Real-time Pipeline):** Transactional outbox ➔ gapless `stream_seq` ➔ WebSocket replay with Redis pub/sub + Postgres fallback.
- **ADR-005 (ECDSA Device Keystore):** NIST P-256 (SECP256R1) + SHA-256 challenge-response auth matching Android `ProductionKeyManager.kt`.
- **ADR-006 (Proximity Duplicate Clustering):** Haversine distance detection automatically links incidents within 500m / 1hr in the same emergency category.
- **ADR-007 (Concurrency Dispatch Guard):** Partial unique index `uq_assignments_active_responder` prevents double-dispatch of active field units.
- **ADR-008 (Cursor-Based Gapless Replay):** `/ws/events?since_seq=<n>` backfills missed events on reconnect before joining live broadcast.
- **ADR-009 (Zero GPS Fabrication):** Missing GPS fix strictly stored as `NULL` coordinates with `gpsFixAvailable: false` and surfaced honestly in UI.
- **ADR-010 (Production Readiness Probes):** `/health` (liveness) and `/health/ready` (connectivity + outbox queue lag probe).

---

## 3. Human Approval Gates (Documented Policies)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline pull-on-sync implemented in `GatewaySyncWorker.kt`; push/mesh pending physical deployment.
