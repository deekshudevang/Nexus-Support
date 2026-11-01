# M3: Disaster Recovery, Backup & Restoration Protocol

**Milestone:** M3 — Production Integration, Device Validation & Release Engineering  
**Date:** 2026-11-01  
**Target Recovery Metrics:** Recovery Point Objective (RPO) < 1 minute | Recovery Time Objective (RTO) < 15 minutes  

---

## 1. Resilience Philosophy & Architectural Redundancy

Nexus Support is designed specifically for disaster scenarios involving total grid collapses, infrastructure failure, and partial network partitions.
- **Edge Resilience**: Mobile nodes continue operating P2P without internet connectivity using BLE and Nearby Connections. All location events, SOS packets, and chat messages buffer durably in SQLCipher-encrypted Room databases.
- **Gateway Buffering**: Edge gateways store packets in local WAL SQLite databases until cellular or satellite connectivity is opportunistically restored.
- **Cloud Persistence**: Cloud transactions commit to PostgreSQL with append-only event timelines (`incident_events`) and transactional outbox logs (`stream_events`).

---

## 2. Automated PostgreSQL Backup Procedures

### 2.1 Daily Scheduled Physical & Logical Dumps
A cron job executes daily compressed logical backups with automated retention pruning:
```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR="/var/backups/rescuemesh"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/rescuemesh_backup_${DATE}.sql.gz"

mkdir -p "${BACKUP_DIR}"

# Execute streaming dump with custom directory format
pg_dump -U rescuemesh_prod_user -h localhost -d rescuemesh_prod \
  --format=c \
  --blobs \
  --clean \
  --if-exists \
  | gzip > "${BACKUP_FILE}"

# Compute SHA-256 integrity checksum
sha256sum "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"

# Prune backups older than 30 days
find "${BACKUP_DIR}" -type f -name "rescuemesh_backup_*.sql.gz*" -mtime +30 -delete

echo "Backup completed successfully: ${BACKUP_FILE}"
```

### 2.2 Continuous WAL Archiving
PostgreSQL `archive_mode` is enabled with `archive_command = 'cp %p /var/lib/postgresql/wal_archive/%f'` for point-in-time recovery (PITR).

---

## 3. Database Restoration & Recovery Drills

### 3.1 Restoring PostgreSQL from Logical Backup
```bash
# 1. Stop backend service to prevent write tearing
systemctl stop rescuemesh-backend

# 2. Verify backup checksum integrity
sha256sum -c rescuemesh_backup_20261101_000000.sql.gz.sha256

# 3. Restore database schema and data
gunzip -c rescuemesh_backup_20261101_000000.sql.gz | pg_restore -U rescuemesh_prod_user -h localhost -d rescuemesh_prod --clean --if-exists

# 4. Run Alembic migration check
cd /opt/rescuemesh/cloud/backend
alembic upgrade head

# 5. Restart backend and verify readiness
systemctl start rescuemesh-backend
curl -f http://127.0.0.1:8000/health/ready
```

### 3.2 Restoring from Edge Gateway SQLite Cache (`import_sqlite_to_pg.py`)
If cloud database storage was wiped or corrupted while edge gateways retained buffered records:
```bash
# Execute safe dry-run (does not modify source SQLite or commit target changes)
python -m app.tools.import_sqlite_to_pg \
  --source /path/to/edge/meshlink_sync.db \
  --dry-run \
  --output-report dry_run_report.json

# Review dry_run_report.json, then execute non-destructive idempotent upsert
python -m app.tools.import_sqlite_to_pg \
  --source /path/to/edge/meshlink_sync.db \
  --output-report restore_report.json
```
- **Integrity Guarantee**: `import_sqlite_to_pg.py` computes pre- and post-migration SHA-256 hashes of the source SQLite file. The source file is opened read-only (`mode=ro`) and is never modified.
- **Truthful Coordinates**: Historical records without GPS fix are imported with `latitude: null`, `longitude: null`, and `gpsFixAvailable: false`.

---

## 4. Rollback Strategy

If a newly deployed backend version introduces unexpected defects:
1. **Rollback Backend Code**:
   ```bash
   git checkout <PREVIOUS_STABLE_COMMIT>
   systemctl restart rescuemesh-backend
   ```
2. **Rollback Database Migration**:
   ```bash
   alembic downgrade -1
   ```
3. **Frontend Rollback**:
   Restore previous compiled bundle in webroot:
   ```bash
   cp -r /var/www/command-center-prev/* /var/www/command-center/
   ```
4. Verify readiness probe:
   ```bash
   curl -f http://127.0.0.1:8000/health/ready
   ```
