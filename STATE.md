# STATE.md — Nexus Support System State

**Phase:** PG-P4 Complete ➔ Transitioning to PG-P5 & PG-P6 (Real-Time Events & Command Center UI)  
**Branch:** `pg-p4-responders-dispatch`  
**Base Commit:** `65563c6` (Head of PG-P3)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Prompt v2 Inherited Rules  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py test_pg_p2_rbac.py test_pg_p3_incidents.py test_pg_p4_dispatch.py` | **58 / 58 PASSED** | Python 3.14 (FastAPI + SQLAlchemy) | `docs/evidence/PG-P4-responders-dispatch.md` |
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

---

## 3. Human Approval Gates (Pending Signoff)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline is pull-on-sync; push/mesh pending approval.

---

## 4. Next Three Actions

1. Commit PG-P4 deliverables on `pg-p4-responders-dispatch`.
2. Create branch `pg-p5-realtime-events` and implement cursor-based WebSocket replay (`since_seq`) and event stream filter.
3. Advance to Phase PG-P6 to implement the Tactical Command Center UI (split monolithic App.jsx into Queue, Map, Dispatch, Timeline).
