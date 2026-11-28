# Pull Request: Release Verification, End-to-End Connectivity, and Production Hardening (`m3-15-to-m3-24-production`)

## Summary
Brings Nexus-Support on `m3-15-to-m3-24-production` from conditional readiness to full end-to-end verified connectivity across **Android (physical handset + API 34 emulator)**, **FastAPI + PostgreSQL 16/PostGIS 3.4**, and **React 18/Tailwind v4/Leaflet Command Center**.

### Critical Bugs Caught & Fixed During Real Stack / Hardware Testing
1. **PostgreSQL 32-bit Timestamp Overflow (`8a2c4f91d3b0`):** Android transmits 13-digit `System.currentTimeMillis()` epochs, which passed SQLite tests (`int64`) but crashed PostgreSQL (`INTEGER` 32-bit max `2.14B`) with `NumericValueOutOfRange`. Added Alembic migration `8a2c4f91d3b0` and updated SQLAlchemy models (`sos_packets`, `node_status`, `incidents`, `location_events`, `responders`) to `BigInteger().with_variant(Integer, "sqlite")`.
2. **HTTP 422 on Mobile SOS With Blank `senderName`:** `SosViewModel` initialized `SosPacketData.senderName = ""`, causing FastAPI `SosPacket` (`min_length=1`) to reject real phone SOS uploads with HTTP 422. Normalized both server (`senderName` default `""` + `senderId` fallback) and `GatewaySyncWorker`.
3. **Downlink Assignments Blocked When 0 SOS Uploads Pending:** Added `KEY_PULL_DOWNLINKS` so manual **Sync Now**, assignment transitions, and periodic sync fetch server-to-device `DISPATCHED` assignments even when no local SOS packets are queued.
4. **PostgreSQL `postgresql://` SQLAlchemy 2.1 Driver Normalization:** Updated `_resolve_database_url()` in `cloud/backend/app/core/config.py` to normalize bare `postgresql://` to `postgresql+psycopg2://` in all environments.
5. **Mobile Web Console Layout & Leaflet `NaN` Crash:** Added responsive `< 768px` tab navigation (`QUEUE` / `RADAR MAP` / `ASSETS`) in `App.jsx`, timestamp ms-vs-sec normalization in `IncidentQueue.jsx` & `EventTimeline.jsx`, and `ResizeObserver` + size guard in `TacticalMap.jsx`'s `MapFlyController` to prevent `Invalid LatLng object: (NaN, NaN)` when initialized hidden on mobile.
6. **Production Packaging & Release Fail-Closed Config:** Added `.env.production.example`, `cloud/Caddyfile`, `docker-compose.prod.yml`, fail-closed `https://unconfigured.meshlink.invalid/` default & empty-key rejection in `GatewaySyncWorker`, and env/property-driven `signingConfigs.release` in `app/build.gradle.kts`.

---

## Verification Evidence
- **Docker + PostGIS Stack:** `docs/evidence/docker_bringup_2026-11-28.txt` (`alembic current = 8a2c4f91d3b0`, PG round-trip `downgrade base` -> `upgrade head` PASSED)
- **PostgreSQL Backup/Restore Drill:** `docs/evidence/pg_backup_restore_drill_2026-11-28.txt` (`pg_dump` -> `pg_restore` into scratch PostGIS DB, 20 tables verified)
- **Physical Phone (`ZD222M3BNH` Android 16) + Emulator (`meshlink_api34` API 34):** `docs/evidence/device_emulator_test_2026-11-28.txt`
- **Web Console Playwright E2E (Desktop + Mobile + WS Restart Recovery):** `docs/evidence/playwright/`
- **Security & Test Gates:** `docs/evidence/security_quality_gates_2026-11-28.txt` (`pytest` **131/131**, `Vitest` **19/19**, `npm audit` **0**, `pip check` **0**)

---

## Risks, Rollback & Repository Maintenance

### Important Host Warning (OneDrive File Locks)
- This local clone currently resides under OneDrive (`c:\Users\deeks\OneDrive\Desktop\NEXUS SUPPORT`), which intermittently locks Gradle test output directories (`app\build\test-results\...\output.bin`) during sync. Move the clone outside OneDrive (e.g., `C:\src\nexus-support`) for smooth CI/IDE builds.

### Optional Git History Rewrite for Previously Tracked Artifacts
- `logs/` (including `logs/startup_logcat.txt`) and legacy SQLite files (`*.db`) are **untracked and ignored in `HEAD`**, but still exist in historical commits prior to cleanup (`6bcf7e1`, `4cbe2c0`, `072b556`). History was **not** rewritten automatically. To purge them cleanly from repository history (coordinate force-push with collaborators first):
  ```bash
  pip install git-filter-repo
  git filter-repo --path logs --path-glob "*.db" --invert-paths
  git push --force-with-lease origin m3-15-to-m3-24-production
  ```

### Rollback Plan
1. **Schema Rollback (PostgreSQL):**
   ```bash
   docker compose exec -T backend alembic downgrade 6f1a8b92c431
   ```
2. **Code Rollback:** Revert commits on `m3-15-to-m3-24-production` in reverse order (`git revert <sha>`) or re-deploy prior image tag; restore pre-upgrade `pg_dump` snapshot if reverting schema changes after ingestion.
3. **Field Checklist:** See `docs/evidence/PHONE_TEST_CHECKLIST.md` for multi-phone BLE/Wi-Fi Direct hop testing and LoRa RF hardware verification.
