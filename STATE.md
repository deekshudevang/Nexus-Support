# STATE.md — Nexus Support System State

**Phase:** PG-P3 Complete ➔ Transitioning to PG-P4 (Responders, Teams & Dispatch Operations)  
**Branch:** `pg-p3-incident-domain`  
**Base Commit:** `36f20b3` (Head of PG-P2)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Prompt v2 Inherited Rules  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py test_pg_p2_rbac.py test_pg_p3_incidents.py` | **54 / 54 PASSED** | Python 3.14 (FastAPI + SQLAlchemy) | `docs/evidence/PG-P3-incident-domain.md` |
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

---

## 3. Human Approval Gates (Pending Signoff)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline is pull-on-sync; push/mesh pending approval.

---

## 4. Next Three Actions

1. Commit PG-Slice & PG-P3 deliverables on `pg-p3-incident-domain`.
2. Create branch `pg-p4-responders-dispatch` and implement Responder presence, Team roster management, and Assignment state machine (`DISPATCHED` -> `ACCEPTED` -> `EN_ROUTE` -> `ON_SCENE` -> `COMPLETED`).
3. Enforce partial unique index concurrency protection (`uq_assignments_active_responder`) preventing double-dispatch of active responders.
