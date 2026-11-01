# Evidence: Phase PG-P9 — Production Readiness, Health Probes & Restore Drill

## Overview
Phase PG-P9 hardens the cloud backend and verifies production operational readiness. It introduces Kubernetes/orchestrator-standard liveness (`/health`) and readiness (`/health/ready`) probes with database connectivity testing and outbox lag measurement. It formally validates the SQLite-to-Target migration tool through a non-destructive dry-run and full idempotent import drill with strict SHA-256 source file immutability guarantees.

---

## 1. Health & Readiness Probes

### Liveness Probe (`GET /health`)
- **HTTP 200 OK**:
  ```json
  {
    "status": "ok",
    "environment": "development",
    "timestamp": 1700000000,
    "service": "MeshLink Gateway Backend"
  }
  ```
- Fast, non-blocking check for container liveness. Exempt from rate limiting.

### Readiness Probe (`GET /health/ready`)
- **HTTP 200 OK (Healthy)**:
  ```json
  {
    "status": "READY",
    "database": "OK",
    "database_engine": "sqlite",
    "outbox_lag": 0,
    "environment": "development",
    "timestamp": 1700000000,
    "service": "MeshLink Gateway Backend"
  }
  ```
  - Probes live database connection (`SELECT 1`).
  - Measures outbox queue publication lag (`COUNT(*) WHERE published_at IS NULL`).
- **HTTP 503 Service Unavailable (Degraded / Connection Lost)**:
  ```json
  {
    "status": "NOT_READY",
    "database": "ERROR",
    "database_engine": "sqlite",
    "outbox_lag": null,
    "errors": ["Database connection pool exhausted"],
    "environment": "development",
    "timestamp": 1700000000,
    "service": "MeshLink Gateway Backend"
  }
  ```
  - Directly informs load balancers and orchestrators not to route traffic to degraded instances.

---

## 2. SQLite-to-PostgreSQL Restore & Migration Drill
- **Tool**: `cloud/backend/app/tools/import_sqlite_to_pg.py`
- **Verification Criteria**:
  1. **Source Immutability**: Computes SHA-256 hash before and after migration; verifies source SQLite file is opened read-only (`mode=ro`) and never altered.
  2. **Dry Run**: Validates schema mapping, table inspection, and planned row counts without modifying target database.
  3. **Data Fidelity & Zero GPS Fabrication**:
     - Historical records with valid GPS are preserved.
     - Historical records without GPS fix retain explicit `NULL` coordinates and `gpsFixAvailable: false`.
  4. **Idempotency**: Repeated runs perform upserts rather than duplicating records.

---

## 3. Test Verification Results

### Backend Pytest Suite
```powershell
py -m pytest cloud/backend -q
```
```
........................................................................ [100%]
72 passed, 2 warnings in 7.50s
```
- Includes 5 new readiness and migration drill tests in `test_pg_p9_readiness.py`.

### Frontend Vitest Suite
```powershell
npm --prefix cloud/frontend test -- --run
```
```
 ✓ src/TacticalCommandCenter.test.jsx (3 tests) 234ms
 ✓ src/App.test.jsx (6 tests) 267ms

 Test Files  2 passed (2)
      Tests  9 passed (9)
```

### Frontend Production Build
```powershell
npm --prefix cloud/frontend run build
```
- `dist/index.html` (0.66 kB)
- `dist/assets/index-CM4-3qLG.css` (17.87 kB)
- `dist/assets/index-Bwzbj6dV.js` (351.57 kB)
- Status: Built in 968ms with 0 secrets or sensitive tokens embedded.
