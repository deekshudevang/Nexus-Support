# STATE.md — Nexus Support System State

**Phase:** PG-P1 Complete ➔ Transitioning to PG-P2 (Security & RBAC)  
**Branch:** `pg-p1-postgres-foundation`  
**Base Commit:** `a122a6a` (Head of PG-P0)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Prompt v2 Inherited Rules  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py` | **40 / 40 PASSED** | Python 3.14 (FastAPI + SQLAlchemy) | `docs/evidence/PG-P1-postgres-foundation.md` |
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

---

## 3. Human Approval Gates (Pending Signoff)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline is pull-on-sync; push/mesh pending approval.

---

## 4. Next Three Actions

1. Commit PG-P1 deliverables on `pg-p1-postgres-foundation`.
2. Create branch `pg-p2-security-rbac` and implement capability-based RBAC (`incident:create`, `incident:triage`, etc.) and device authentication.
3. Write negative authorization, IDOR, and device signature tests in `test_pg_p2_rbac.py`.
