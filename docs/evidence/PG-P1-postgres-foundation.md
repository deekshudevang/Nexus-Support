# Evidence: Phase PG-P1 — PostgreSQL & Database Backbone

## Overview
Phase PG-P1 establishes the database backbone for Nexus Support / RescueMesh, providing production-grade schema management, SQLAlchemy 2.0 ORM models, Alembic migrations, dual-dialect support (PostgreSQL and SQLite fallback), and SQLite-to-Postgres import tooling.

## Verification Evidence

### 1. Docker Compose Infrastructure
- **File**: `docker-compose.yml`
- **Services**:
  - `postgres`: PostgreSQL 16 Alpine (`rescuemesh` db, healthcheck with `pg_isready -U postgres`, persistent volume `pgdata`)
  - `redis`: Redis 7 Alpine (`redis-server --save 60 1 --loglevel warning`, healthcheck with `redis-cli ping`, persistent volume `redisdata`)

### 2. Database & ORM Architecture
- **Location**: `cloud/backend/app/`
  - `app/core/config.py`: Environment-aware configuration with production guards rejecting default dev secrets.
  - `app/core/database.py`: SQLAlchemy 2.x engine abstraction with deterministic naming conventions (`pk_`, `fk_`, `ix_`, `uq_`, `ck_`), thread-safe session generators, and raw SQLite WAL mode fallback.
  - `app/core/security.py`: PBKDF2-HMAC-SHA256 password hashing (100,000 iterations), constant-time credential comparisons, single-use WebSocket ticket store.
  - `app/models/`:
    - `UserModel` (`users`): RBAC, token versioning, password hashing.
    - `TeamModel` (`teams`): Search & rescue operational units.
    - `ResponderModel` (`responders`): Status, capabilities, location, assignment tracking.
    - `TeamMemberModel` (`team_members`): Team roster association with role in team.
    - `IncidentModel` (`incidents`): Complete incident tracking with coordinate validation check `ck_incidents_coordinates_valid` (allowing null or valid range [-90, 90], [-180, 180]).
    - `IncidentLinkModel` (`incident_links`): Directed incident relationship/clustering with unique pair constraint.
    - `AssignmentModel` (`assignments`): Dispatch workflow with partial unique index `uq_assignments_active_responder` preventing overlapping active assignments.
    - `IncidentEventModel` (`incident_events`): Append-only timeline log.
    - `AuditLogModel` (`audit_logs`): Immutable audit trail with actor/action tracking.
    - `OutboxEventModel` (`outbox_events`): Transactional outbox for event publishing.
    - `StreamEventModel` (`stream_events`): Gapless replay log with monotonic sequence numbers.
    - `IdempotencyKeyModel` (`idempotency_keys`): Scope + key deduplication.
    - `SosPacketModel` (`sos_packets`): Backward-compatible SOS packet store with nullable coordinates.
    - `NodeStatusModel` (`node_status`): Mesh node heartbeat & telemetry store.

### 3. Alembic Migration Chain
- Migrations:
  - `7f9227a7fd69_create_initial_rescuemesh_tables.py`: Legacy baseline (`sos_packets`, `node_status`, `users`).
  - `1366b03464eb_add_incident_operations_and_command_.py`: Incident operations, teams, responders, assignments, outbox, stream events, audit logs, idempotency.
- Verification commands executed:
  - `alembic upgrade head`: Applied successfully (exit code 0).
  - `alembic check`: Clean, 0 drift reported (`No new upgrade operations detected.`).
  - `alembic downgrade -1 && alembic upgrade head`: Reversible, successfully downgraded and upgraded cleanly.

### 4. Admin CLI & Data Migration Tool
- `app/cli.py`: Administrative CLI providing `python -m app.cli create-admin --username <user> --password <pass> --email <email>`.
- `app/tools/import_sqlite_to_pg.py`: Idempotent data import tool with `--dry-run`, checksum validation, and JSON reporting.

### 5. Automated Test Pass
- Test command:
  ```powershell
  python -m pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py
  ```
- Result: **40 passed** in 4.83s.
  - `test_backend.py`: 31 passed (legacy endpoints, auth, sync, metrics).
  - `test_legacy_contract.py`: 4 passed (frozen contract assertions).
  - `test_pg_p1_entities.py`: 5 passed (coordinate validation constraints, partial unique active responder constraints, user password hashing, outbox model, idempotency keys).
