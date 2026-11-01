# PostgreSQL Backend Baseline Audit (PG-P0)

**Audit Timestamp:** 2026-11-01T20:05:00+05:30  
**Audit Scope:** Database foundation, ORM models, migration readiness, and legacy contract compatibility.  
**Git Commit Reference:** `4f88c01` (Branch: `pg-p0-audit-baseline`)  

---

## 1. Baseline Test Execution Results

| Test Suite | Environment | Exit Code | Pass / Total | Notes |
|---|---|---|---|---|
| `pytest test_backend.py test_legacy_contract.py` | Python 3.14 / FastAPI TestClient | 0 | **35 / 35** | 100% pass across all legacy and current routes. |
| `cloud/frontend` build | Vite 5 / Node.js 22 | 0 | 2088 modules | Production client build succeeded in 1.02s without secrets. |
| `:core:data:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | 0 | 58 tasks | All sequential Room migrations (v1..v14) and Tink AEAD tests passed. |
| `:app:testDebugUnitTest` | Gradle 8.11.1 / Java 21 | 0 | 87 tasks | ViewModels and `GatewaySyncWorkerTest` passed. |

---

## 2. Characterization of Identified Backend Risks

1. **In-Memory Rate Limiting & Revocation:**
   - Current state: Stored in module globals `_ip_request_history` and `REVOKED_CREDENTIALS`.
   - Risk: Process restarts reset counters; horizontal scaling causes split-brain rate limits.
   - Mitigation Plan (PG-P1 & PG-P2): Migrate rate-limit counters to Redis (with Postgres/in-process fallback failing closed to strict limit).
2. **Replacement-Style Write Behavior:**
   - Current state: `INSERT OR REPLACE INTO sos_packets` overwrites existing state.
   - Risk: Overwriting loses transition history, reporter context, and status provenance.
   - Mitigation Plan (PG-P1 & PG-P3): Preserve packet rows, create separate `incidents` and append-only `incident_events` ledger.
3. **Database Liveness vs Readiness:**
   - Current state: `/health` returns static JSON without pinging the database engine.
   - Mitigation Plan (PG-P1): Introduce `/ready` probing `SELECT 1` and checking Alembic migration head.

---

## 3. Migration Architecture & Schema Foundation

- **Alembic Configuration:** Initialized in `cloud/backend/alembic.ini` and `alembic/`.
- **Baseline Migration:** `7f9227a7fd69_create_initial_rescuemesh_tables.py` exists, covering:
  - `sos_packets`: with nullable `latitude`, `longitude`, `gpsFixAvailable`, and descending indexes on `timestamp` and `severity`.
  - `node_status`: with `deviceId` PK and `timestamp` index.
  - `users`: with unique `username`, `password_hash`, `role`, and active flag.
- **Target Schema (PG-P1):**
  - Will add: `responders`, `teams`, `team_members`, `incidents`, `incident_links`, `assignments`, `incident_events`, `audit_logs`, `outbox_events`, `stream_events`, `idempotency_keys`, `devices`.
