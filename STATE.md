# STATE.md — Nexus Support System State

**Phase:** PG-P0 Complete ➔ Transitioning to PG-P1 (PostgreSQL Foundation)  
**Branch:** `pg-p0-audit-baseline`  
**Base Commit:** `4f88c01` (Head of baseline transformation)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Prompt v2 Inherited Rules  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py` | **35 / 35 PASSED** | Python 3.14 (FastAPI) | `docs/audit/POSTGRES_BACKEND_BASELINE.md` |
| `cloud/frontend` build & test | **6 / 6 PASSED**, build ok | Node.js 22 (Vite / React) | `cloud/frontend/dist` |
| `:core:data:testDebugUnitTest` | **58 tasks PASSED** | Java 21 / Room v1..v14 | `docs/evidence/M0-baseline-audit.md` |
| `:app:testDebugUnitTest` | **87 tasks PASSED** | Java 21 / ViewModels + Sync | `docs/evidence/M1-contracts-and-defects.md` |

---

## 2. Key Architecture Decisions (ADR Summary)

- **ADR-001 (Strangler Layout):** Modular code lives in `cloud/backend/app/`, with `main.py` acting as compatibility shim preserving legacy endpoints.
- **ADR-002 (Dual-Dialect DB):** Synchronous SQLAlchemy 2.x + psycopg 3 with SQLite fallback for offline edge testing.
- **ADR-003 (3 State Machines):** Independent lifecycles for Incidents, Assignments, and Responder Availability. Response state dynamically derived.
- **ADR-004 (Real-time Pipeline):** Transactional outbox ➔ gapless `stream_seq` ➔ WebSocket replay with Redis pub/sub + Postgres fallback.

---

## 3. Human Approval Gates (Pending Signoff)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline is pull-on-sync; push/mesh pending approval.

---

## 4. Next Three Actions

1. Commit PG-P0 deliverables and create branch `pg-p1-postgres-foundation`.
2. Implement PostgreSQL entities (`app/models/`), constraint rules, and Alembic migrations (`PG-P1-01`, `PG-P1-02`).
3. Build idempotent SQLite ➔ PostgreSQL import tool and `create-admin` CLI command (`PG-P1-03`, `PG-P1-04`).
