# M3.16 — PRODUCTION POSTGRESQL, POSTGIS & SAFETY INVARIANT 9 CERTIFICATION
**Milestone:** M3.16 Production Readiness Execution  
**Execution Timestamp:** 2026-11-03T18:35:00+05:30  
**Branch:** `m3-15-to-m3-24-production`  
**Test Suite:** `cloud/backend/test_pg_p16_postgres_postgis.py` + full backend regression (80 passed)  
**Status:** COMPLETE & VERIFIED

---

## 1. Safety Invariant 9: Strict Prohibition of SQLite in Production

**Safety Invariant 9 Rule:**  
> *Production must never silently fall back to SQLite. If PostgreSQL is unavailable in production mode, fail fast and loudly with a health-check failure.*

### Implementation Details
In `cloud/backend/app/core/config.py` and `cloud/backend/main.py`:
- `_resolve_database_url()` and `validate_production_configuration()` inspect the configured database connection URL when `ENVIRONMENT == "production"`.
- Any attempt to bootstrap with an empty `DATABASE_URL`, an SQLite scheme (`sqlite://`), or in-memory database (`:memory:`) immediately halts initialization with a fatal `RuntimeError`:
  ```python
  CRITICAL SAFETY INVARIANT 9 VIOLATION: Production environment must never run on SQLite. A valid PostgreSQL connection URL is mandatory.
  ```
- Non-PostgreSQL database URL schemes (e.g., MySQL, Oracle) are rejected with an explicit scheme mismatch error.
- Verified in automated tests:
  - `test_production_refuses_sqlite_fallback`
  - `test_production_refuses_missing_database_url`
  - `test_config_resolve_database_url_invariants`
  - `test_production_configuration_passes_valid_postgres`

---

## 2. Production Connection Pooling Architecture

In `cloud/backend/app/core/database.py`, engine creation distinguishes between edge/development mode and production PostgreSQL deployments:

```python
if DATABASE_URL.startswith("sqlite"):
    engine = create_engine(
        DATABASE_URL,
        connect_args={"check_same_thread": False, "timeout": 5.0}
    )
else:
    engine = create_engine(
        DATABASE_URL,
        pool_size=int(os.getenv("DB_POOL_SIZE", "20")),
        max_overflow=int(os.getenv("DB_MAX_OVERFLOW", "10")),
        pool_timeout=float(os.getenv("DB_POOL_TIMEOUT", "30.0")),
        pool_pre_ping=True,  # Proactively discards stale/severed sockets
        pool_recycle=int(os.getenv("DB_POOL_RECYCLE", "1800")), # 30 min recycle
    )
```

| Parameter | Production Default | Description |
|---|---|---|
| `pool_size` | `20` | Base number of concurrent database connections kept persistently open. |
| `max_overflow` | `10` | Maximum surge connections permitted during disaster burst events. |
| `pool_timeout` | `30.0s` | Maximum wait duration before raising `TimeoutError` when pool is saturated. |
| `pool_pre_ping` | `True` | Issues `SELECT 1` heartbeat prior to checking out connection from pool. |
| `pool_recycle` | `1800s` | Periodically recycles connections to prevent state accumulation on TCP proxies. |

---

## 3. PostGIS Geometry Migrations & Spatial Indexing

Alembic revision `3b9a1f28e05c` (`add_postgis_and_spatial_indexes.py`) extends all spatial models:

### Schema Additions
1. **`incidents`**: Adds `geom geometry(Point, 4326)` with GIST spatial index `ix_incidents_spatial_geom` (or composite `(latitude, longitude)` index for bounding-box search on generic dialects).
2. **`sos_packets`**: Adds `geom geometry(Point, 4326)` with GIST spatial index `ix_sos_packets_spatial_geom`.
3. **`node_status`**: Adds `geom geometry(Point, 4326)` with GIST spatial index `ix_node_status_spatial_geom`.
4. **`responders`**: Adds `geom geometry(Point, 4326)` with GIST spatial index `ix_responders_spatial_geom` (indexing `last_latitude`, `last_longitude`).

### Migration Upgrade Command
```bash
cd cloud/backend
alembic upgrade head
```

### Migration Downgrade Command (Tested Roundtrip)
```bash
cd cloud/backend
alembic downgrade -1
```

The migration was verified with automated roundtrip testing (`test_alembic_migrations_roundtrip`): upgrading from `1366b03464eb` to head, inspecting the table indexes, rolling back to `1366b03464eb`, and re-applying cleanly without orphan constraints.

---

## 4. Production Backup and Disaster Recovery Runbook

### 4.1 Automated Logical Backup (`pg_dump`)
Run scheduled atomic snapshots of the `rescuemesh` database:
```bash
docker exec -t rescuemesh-postgres-1 pg_dump \
    -U rescuemesh \
    -d rescuemesh \
    -F c \
    -b \
    -v \
    -f /var/lib/postgresql/data/backups/rescuemesh_$(date +%Y%m%d_%H%M%S).dump
```

### 4.2 Restoring from Logical Backup
```bash
# 1. Stop backend service
docker compose stop backend

# 2. Re-create target database
docker exec -it rescuemesh-postgres-1 dropdb -U rescuemesh --if-exists rescuemesh
docker exec -it rescuemesh-postgres-1 createdb -U rescuemesh rescuemesh

# 3. Restore snapshot
docker exec -it rescuemesh-postgres-1 pg_restore \
    -U rescuemesh \
    -d rescuemesh \
    -v /var/lib/postgresql/data/backups/rescuemesh_target.dump

# 4. Verify table integrity and start backend
docker compose start backend
```

---

## 5. Exit Gate Checklist

- [x] Fail-fast SQLite refusal implemented and verified by automated unit tests.
- [x] Alembic migration `3b9a1f28e05c` created with PostGIS geometry and spatial index definitions.
- [x] Migration upgrade/downgrade round-trip tested and passing.
- [x] Connection pooling configuration parameters defined with environment variable overrides.
- [x] Full backend regression test suite passing (80/80 passed).
