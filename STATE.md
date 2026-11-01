# STATE.md — Nexus Support System State

**Phase:** PG-P2 Complete ➔ Transitioning to PG-Slice / PG-P3 (Incident Operations Domain)  
**Branch:** `pg-p2-security-rbac`  
**Base Commit:** `e014b51` (Head of PG-P1)  
**Charter Rules Applied:** `.agent/rules/nexus-charter.md` + Master Prompt v2 Inherited Rules  

---

## 1. Verified Test Baselines

| Test Suite | Result | Environment | Evidence |
|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py test_pg_p2_rbac.py` | **47 / 47 PASSED** | Python 3.14 (FastAPI + SQLAlchemy) | `docs/evidence/PG-P2-security-rbac.md` |
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

---

## 3. Human Approval Gates (Pending Signoff)

1. **Offline Responder Enrollment Trust Model:** Documented in `docs/policy/offline-enrollment.md`.
2. **Responder Location Retention & Consent Policy:** Documented in `docs/policy/break-glass.md` & `medical-privacy.md`.
3. **Downlink Assignment Delivery Channel:** Baseline is pull-on-sync; push/mesh pending approval.

---

## 4. Next Three Actions

1. Commit PG-P2 deliverables on `pg-p2-security-rbac`.
2. Implement PG-Slice (Thin Vertical Slice) & PG-P3 Incident Domain service (`app/services/incident_service.py`), 3-state-machine validation, and duplicate incident linking.
3. Verify vertical slice (Device SOS -> DB -> Outbox -> Stream Event -> WebSocket broadcast).
