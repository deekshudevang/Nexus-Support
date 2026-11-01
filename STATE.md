# STATE.md — Nexus Support System State

**Phase:** PG-P5 Complete ➔ Transitioning to PG-P6 (Command Center Tactical Web UI)  
**Branch:** `pg-p5-realtime-events`  
**Base Commit:** `41363db` (Head of PG-P4)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Prompt v2 Inherited Rules  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py test_pg_p2_rbac.py test_pg_p3_incidents.py test_pg_p4_dispatch.py test_pg_p5_events.py` | **61 / 61 PASSED** | Python 3.14 (FastAPI + SQLAlchemy) | `docs/evidence/PG-P5-realtime-events.md` |
| `cloud/frontend` build & test | **6 / 6 PASSED**, build ok | Node.js 22 (Vite / React) | `cloud/frontend/dist` |
| `:core:data:testDebugUnitTest` | **58 tasks PASSED** | Java 21 / Room v1..v14 | `docs/evidence/M0-baseline-audit.md` |
| `:app:testDebugUnitTest` | **87 tasks PASSED** | Java 21 / ViewModels + Sync | `docs/evidence/M1-contracts-and-defects.md` |
| `alembic check` & bi-directional migration | **0 drift, 100% reversible** | Alembic 1.20 | `docs/evidence/PG-P1-postgres-foundation.md` |

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

---

## 3. Human Approval Gates (Pending Signoff)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline is pull-on-sync; push/mesh pending approval.

---

## 4. Next Three Actions

1. Commit PG-P5 deliverables on `pg-p5-realtime-events`.
2. Create branch `pg-p6-command-center-ui`.
3. Implement Tactical Command Center UI in `cloud/frontend`: decompose monolithic `App.jsx` into modular components (IncidentQueue, TacticalMap, DispatchPanel, EventTimeline) with honest missing GPS badges and Vitest test coverage.
