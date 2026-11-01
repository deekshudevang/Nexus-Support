# Milestone M2 Evidence: Cloud Backend Modernization & Database Backbone

**Milestone:** M2  
**Date:** 2026-11-01  
**Branch:** `m2-backend-database-backbone`  

---

## 1. Modular Backend Architecture (`cloud/backend/app/`)

- **`app/core/config.py`**:
  - Environment-based configuration, production security validator, strict CORS policies, and credential loading.
- **`app/core/security.py`**:
  - PBKDF2-HMAC-SHA256 password hashing with 100,000 iterations and 16-byte cryptographically secure salt.
  - Constant-time secret comparison via `secrets.compare_digest`.
  - Single-use 30-second WebSocket ticket store.
- **`app/core/database.py`**:
  - Dual-mode SQLAlchemy engine supporting PostgreSQL/PostGIS and SQLite edge nodes.
  - SQLite WAL mode and busy timeout pragmas.
- **`app/models/`**:
  - `SosPacketModel`: SQLAlchemy model with nullable GPS coordinates, `gpsFixAvailable`, and descending timestamp/severity indexes.
  - `NodeStatusModel`: SQLAlchemy model for mesh node telemetry.
  - `UserModel`: Persistent user account model with role-based access control.
- **`app/schemas/`**:
  - Pydantic models for request/response serialization and validation.
- **`app/services/alert_manager.py`**:
  - Thread-safe WebSocket connection pool with auto-pruning of dead connections.

---

## 2. Database Migrations (Alembic)

- Initialized Alembic configuration in `cloud/backend/alembic.ini` and `alembic/`.
- Configured dynamic database URL wiring from `app.core.config.DATABASE_URL`.
- Generated and executed baseline migration `7f9227a7fd69_create_initial_rescuemesh_tables.py`:
  - Created tables: `sos_packets`, `node_status`, `users`.
  - Created composite descending indexes on timestamps and triage severities.
- `alembic upgrade head` verified with exit code 0.

---

## 3. Persistent User Authentication & Defect E2 Remediation

- Remediated Defect E2 (in-memory token management):
  - Created `users` table with persistent password hashing.
  - Auto-seeded default admin operator account.
  - Added operator registration endpoint `POST /api/auth/register` protected by operator token.
  - Authenticated login via `POST /api/auth/token` checks persistent database before bootstrap tokens.
- Remediated Defect E6 (Idempotent ingestion):
  - Ingestion utilizes primary key deduplication (`uuid`, `deviceId`) on `INSERT OR REPLACE` / `ON CONFLICT`.

---

## 4. Test Verification

- **Pytest (Python 3.14)**: **31 PASSED / 0 FAILED** (includes `test_user_registration_and_persistent_login` and `test_password_hashing_security`).
- **Vitest (Node.js 22)**: **6 PASSED / 0 FAILED**.

