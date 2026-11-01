# M3: Pre-Release Verification & Promotion Checklist

**Milestone:** M3 — Production Integration, Device Validation & Release Engineering  
**Target Release Candidate:** Nexus Support / RescueMesh v1.0.0-rc1  
**Classification:** `STAGED RELEASE CANDIDATE (INTEGRATION & REGRESSION VERIFIED)`  

---

## 1. Automated Test Quality Gates

| Gate ID | Target Component | Mandatory Criteria | Verification Evidence | Pass / Fail |
|---|---|---|---|---|
| **GATE-01** | **Backend Pytest Suite** | 100% green across all unit, RBAC, domain, and flow tests | 74 / 74 tests passing | **PASS** |
| **GATE-02** | **Frontend Vitest Suite** | 100% green across all Command Center tests | 9 / 9 tests passing | **PASS** |
| **GATE-03** | **Frontend Production Build** | Compiles with 0 warnings/errors, 0 leaked credentials | Built in 611ms, clean dist scan | **PASS** |
| **GATE-04** | **Android Data Layer Tests** | Room migration tests v1 through v14 green with SQLCipher | 58 / 58 tests passing | **PASS** |
| **GATE-05** | **Android App Unit Tests** | ViewModels, Navigation, WorkManager downlinks, CRDT green | 87 / 87 tests passing | **PASS** |
| **GATE-06** | **Android Mesh Core Tests** | BLE codecs, Bloom filter, routing, and simulation green | 87 / 87 actionable tasks passed | **PASS** |
| **GATE-07** | **Alembic Migration Chain** | Linear, reversible chain with zero drift | Head `1366b03464eb`, verified SQL | **PASS** |
| **GATE-08** | **Health & Readiness Probes** | `/health` 200, `/health/ready` 200 / 503 degraded | Verified in `test_pg_p9_readiness.py` | **PASS** |

---

## 2. Release Artifact Verification

| Artifact Name | Path | Size | Description |
|---|---|---|---|
| **Android Universal Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | 192.42 MB | Multi-ABI testing build (x86, x86_64, arm64, armeabi) |
| **Android App Bundle (AAB)** | `app/build/outputs/bundle/debug/app-debug.aab` | 91.52 MB | Production distribution bundle (arm64 installs ~45MB) |
| **Tactical Command Center JS** | `cloud/frontend/dist/assets/index-Bqh3PbRP.js` | 349.39 kB | Compiled production SPA (gzipped: 106.33 kB) |
| **Tactical Command Center CSS**| `cloud/frontend/dist/assets/index-CM4-3qLG.css` | 17.87 kB | Stitch MCP Design Token CSS (gzipped: 7.29 kB) |
| **Alembic Migration Scripts** | `cloud/backend/alembic/versions/` | Linear | Revision heads `7f9227a7fd69`, `1366b03464eb` |

---

## 3. Operational Security & Configuration Checks

- [x] Fail-closed production configuration verified in `cloud/backend/main.py`.
- [x] No plaintext passwords or static tokens in `cloud/frontend/dist/`.
- [x] Hardcoded demonstration keys removed from `LoginTerminal.jsx`.
- [x] Missing GPS coordinates strictly handled as `null` with `gpsFixAvailable: false`.
- [x] Single-use WebSocket ticket issuance verified.
- [x] Partial unique index `uq_assignments_active_responder` prevents double-dispatch.
- [x] SQLCipher at-rest encryption active on Android client.
- [x] StrongBox / TEE key fallback verified on Android KeyStore.

---

## 4. Release Decision Sign-Off

- **Release Status**: **STAGED RELEASE CANDIDATE**
- **Recommendation**: Deploy backend to staging environment, execute ADB hardware testing on connected Motorola Edge 50 Pro devices, and initiate smoke testing on live mesh hardware nodes.
