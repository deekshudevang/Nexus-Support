# GSD Runbook

> Operational procedures for debugging, validation, and recovery.

---

## Quick Commands

### Status Check

**PowerShell:**
```powershell
# Current git status
git status

# Recent commits
git log --oneline -10

# Current branch
git branch --show-current
```

**Bash:**
```bash
# Current git status
git status

# Recent commits
git log --oneline -10

# Current branch
git branch --show-current
```

---

## Wave Validation

### Verify Wave Completion

**Before marking a wave complete:**

1. All tasks have commits:
   ```powershell
   git log --oneline -N  # N = number of tasks in wave
   ```

2. All verifications passed (documented in SUMMARY.md)

3. STATE.md updated with current position

4. State snapshot created

### Wave Rollback

**If a wave needs to be reverted:**

```powershell
# Find commit before wave started
git log --oneline -20

# Reset to that commit (keeps changes staged)
git reset --soft <commit-hash>

# Or hard reset (discards changes)
git reset --hard <commit-hash>
```

---

## Debugging Procedures

### 3-Strike Rule

After 3 consecutive failed debug attempts:

1. **Stop** — Don't try a 4th approach in same session

2. **Document** in STATE.md:
   ```markdown
   ## Debug Session
   
   **Problem:** {description}
   
   **Attempts:**
   1. {approach 1} → {result}
   2. {approach 2} → {result}
   3. {approach 3} → {result}
   
   **Hypothesis:** {current theory}
   
   **Recommended next:** {suggested approach}
   ```

3. **Fresh session** — Start new conversation with documented context

### Log Inspection

**Find relevant logs:**

```powershell
# Search for error patterns
Select-String -Path "*.log" -Pattern "error|exception|failed" -CaseSensitive:$false
```

```bash
# Search for error patterns
grep -ri "error\|exception\|failed" *.log
```

---

## Verification Commands

### Build Verification

```powershell
# Node.js
npm run build
if ($LASTEXITCODE -eq 0) { Write-Host "✅ Build passed" }

# Python
python -m py_compile src/**/*.py
```

### Test Verification

```powershell
# Node.js
npm test

# Python
pytest -v

# Go
go test ./...
```

### Lint Verification

```powershell
# Node.js
npm run lint

# Python
ruff check .

# Go
golangci-lint run
```

---

## State Recovery

### From STATE.md

When resuming work:

1. Read STATE.md for current position
2. Check "Last Action" for context
3. Follow "Next Steps" to continue
4. Verify recent commits match documented progress

### From Git History

If STATE.md is outdated:

```powershell
# See recent work
git log --oneline -20

# Check specific commit details
git show <commit-hash> --stat

# View file at specific commit
git show <commit-hash>:path/to/file
```

### Context Pollution Recovery

If quality is degrading mid-session:

1. Create state snapshot immediately
2. Update STATE.md with full context
3. Commit any pending work
4. Start fresh session
5. Run `/resume` to reload context

---

## Search Commands

### Find in Codebase

**PowerShell:**
```powershell
# Find pattern in files
Select-String -Path "src/**/*.ts" -Pattern "TODO" -Recurse

# Find files by name
Get-ChildItem -Recurse -Filter "*.config.*"
```

**Bash:**
```bash
# Find pattern in files (with ripgrep)
rg "TODO" --type ts

# Find pattern in files (with grep)
grep -r "TODO" src/

# Find files by name
find . -name "*.config.*"
```

### Search-First Workflow

Before reading any file:

1. Search for relevant terms:
   ```powershell
   Select-String -Path "**/*.md" -Pattern "architecture" -Recurse
   ```

2. Identify candidate files from results

3. Read only relevant sections:
   ```powershell
   Get-Content file.md | Select-Object -Skip 49 -First 20  # Lines 50-70
   ```

---

## Common Issues

### "SPEC.md not FINALIZED"

**Cause:** Planning lock prevents implementation

**Fix:**
1. Open `.gsd/SPEC.md`
2. Complete all required sections
3. Change status to `Status: FINALIZED`
4. Retry command

### "Context degrading"

**Symptoms:** Shorter responses, skipped steps, inconsistency

**Fix:**
1. Create state snapshot
2. Commit current work
3. Start fresh session
4. Run `/resume`

### "Commit failed"

**Causes:** Staged conflicts, hook failures

**Debug:**
```powershell
git status
git diff --staged
```

---

## Checklist Templates

### Pre-Execution Checklist

- [ ] SPEC.md is FINALIZED
- [ ] ROADMAP.md has current phase
- [ ] STATE.md loaded and understood
- [ ] Previous wave verified complete

### Post-Wave Checklist

- [ ] All tasks committed
- [ ] Verifications documented
- [ ] STATE.md updated
- [ ] State snapshot created
- [ ] No uncommitted changes

### Session End Checklist

- [ ] Current work committed
- [ ] STATE.md has "Next Steps"
- [ ] JOURNAL.md updated (if milestone)
- [ ] No loose ends

---

## Connectivity, Sync & Deployment Operations Runbook

### 1. Environment & Single-Source Configuration
- **Root Compose Stack:** Use root `docker-compose.yml` (single canonical compose file). Copy `.env.example` -> `.env` and set non-dev `GATEWAY_API_KEYS`, `OPERATOR_TOKENS`, `JWT_SECRET` (>= 32 chars), HTTPS `CORS_ORIGINS`, `VITE_API_URL`, and `VITE_WS_URL`.
- **Backend Single Config Source (`cloud/backend/app/core/config.py`):** Production (`ENVIRONMENT=production`) fails closed on SQLite URLs, default dev secrets, insecure CORS (`*`, `http://`, or `localhost`), or weak/missing `JWT_SECRET`.
- **Frontend Production Build Guard (`cloud/frontend/vite.config.js`):** `npm run build` fails cleanly unless `VITE_API_URL` and `VITE_WS_URL` are explicitly supplied.

### 2. Android Connectivity & Field Provisioning
- **Build Types (`core/data/build.gradle.kts`, `app/build.gradle.kts`):**
  - `debug`: defaults to `http://10.0.2.2:8000/` (`ALLOW_CLEARTEXT_HTTP = true`), overridable via `MESHLINK_BACKEND_URL` / `meshlink.backend.url` and debug `network_security_config.xml` (permits LAN/Tailscale HTTP).
  - `staging` / `release`: `ALLOW_CLEARTEXT_HTTP = false`; release manifest (`app/src/main/res/xml/network_security_config.xml`) blocks all cleartext HTTP.
  - Release Gateway Key: supply `-PMESHLINK_GATEWAY_KEY=<key>` at build time or provision at runtime in **App Settings -> Cloud Gateway Connectivity** (stored encrypted via Google Tink AES-256-GCM).
- **Live Verification in App:** Open **Settings -> Cloud Gateway Connectivity**, enter the backend URL + Gateway API Key, tap **Test Connection** (verifies `/health` and `/health/ready` DB + migration head), and **Sync Now** to flush pending SOS (`GatewaySyncWorker`) and location events (`CloudSyncWorker`).

### 3. Docker Compose, PostGIS & Backup/Restore Operations
- **Canonical Stack (`docker-compose.yml`):** Starts `rescuemesh-postgres` (`postgis/postgis:16-3.4-alpine`), `rescuemesh-redis` (`redis:7-alpine`), `rescuemesh-backend` (port `8000`), and `rescuemesh-frontend` (port `5173`).
- **Production Stack (`docker-compose.prod.yml`):** Uses `.env.production.example` as template, terminates TLS via `cloud/Caddyfile` (`caddy:2-alpine`), binds DB/Redis internally only (no published Postgres/Redis ports), enforces healthchecks, memory/CPU limits, `no-new-privileges:true`, and non-root backend execution.
- **Database Backup & Restore Drill (Tested against PostGIS 16):**
  ```powershell
  # Backup custom-format dump
  docker compose exec -T db pg_dump -U rescuemesh -Fc rescuemesh > rescuemesh_backup.dump

  # Restore into clean PostGIS DB (run DROP/CREATE outside transaction block)
  docker compose exec -T db psql -U rescuemesh -d postgres -c "CREATE DATABASE rescuemesh_restore;"
  docker compose exec -T db psql -U rescuemesh -d rescuemesh_restore -c "CREATE EXTENSION IF NOT EXISTS postgis;"
  Get-Content -Encoding Byte -Raw rescuemesh_backup.dump | docker compose exec -T db pg_restore -U rescuemesh -d rescuemesh_restore --no-owner --no-privileges
  ```

### 4. Remote Field Access (Tailscale / Cloudflare Tunnel) & Firewall
- **Windows Firewall (LAN Wi-Fi Access):** If physical phones on the same Wi-Fi time out reaching host port `8000`, run in an **Administrator PowerShell**:
  ```powershell
  netsh advfirewall firewall add rule name="MeshLink Backend 8000" dir=in action=allow protocol=TCP localport=8000 profile=private
  ```
- **Private Field Pilot via Tailscale:** Install Tailscale on host/server + field phones and enable Tailscale HTTPS MagicDNS (`tailscale serve https / http://127.0.0.1:8000`) or use Tailscale IP `http://100.x.y.z:8000/` in debug builds (`allow_cleartext_http=true`).
- **Quick HTTPS Pilot via Cloudflare Tunnel:** `cloudflared tunnel --url http://127.0.0.1:8000` provides an immediate `https://<random>.trycloudflare.com/` endpoint compatible with release APKs (`cleartextTrafficPermitted="false"`).
- **Android Release Signing:** Provide `MESHLINK_KEYSTORE_PATH`, `MESHLINK_KEYSTORE_PASSWORD`, `MESHLINK_KEY_ALIAS`, `MESHLINK_KEY_PASSWORD` (or `meshlink.keystore.*` Gradle properties) plus `-PMESHLINK_PROD_URL=https://...` and `-PMESHLINK_GATEWAY_KEY=...`:
  ```powershell
  keytool -genkeypair -v -keystore meshlink-release.jks -alias meshlink_release -keyalg RSA -keysize 4096 -validity 3650
  ```

### 5. Troubleshooting Checklist
| Symptom | Root Cause | Resolution |
|---|---|---|
| Connection timeout (`failed to connect ... after 15000ms`) | Wrong IP/port, backend stopped, or Windows Firewall blocking port `8000` | Check `docker compose ps`, curl `http://127.0.0.1:8000/health`, open Private Firewall rule on port `8000`, or use Tailscale/Cloudflare URL. |
| Android HTTP 401/403 on sync | Missing/mismatched `X-Gateway-Key` | Check `GATEWAY_API_KEYS` on backend vs Settings -> Gateway API Key; `GatewaySyncWorker` stops retries cleanly (`Result.failure()`) with explicit error. |
| Cleartext HTTP blocked in release APK | `network_security_config.xml` forbids `http://` in release builds | Serve backend behind TLS (`cloud/Caddyfile`, Cloudflare Tunnel, or Tailscale HTTPS) and configure `https://...` URL. |
| `/health` returns `200` but `/health/ready` returns `503` | Database unreachable, pending Alembic migrations, or Postgres schema mismatch | Run `docker compose exec -T backend alembic upgrade head` and inspect `docker compose logs backend db`. |
| Frontend `WSS OFFLINE (RETRY nS)` | Backend unreachable or WS ticket expired | Verify `/health/ready`, `CORS_ORIGINS`, and `VITE_WS_URL`; client retries with exponential backoff up to 30s (stops on 401/403). |
| Physical phone cannot reach `10.0.2.2` | Emulator-only host IP | Set LAN IP (`http://192.168.1.4:8000/`), Tailscale IP, or HTTPS tunnel URL in Settings -> Cloud Gateway Connectivity. |

---

*See PROJECT_RULES.md for canonical rules.*
*See docs/model-selection-playbook.md for model guidance.*
