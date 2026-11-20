<div align="center">

# Nexus Support
### Offline-First Emergency Mesh Communication & Tactical Incident Coordination

[![CI](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml/badge.svg)](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Backend](https://img.shields.io/badge/FastAPI-1.0.0-009688?logo=fastapi&logoColor=white)](cloud/backend)
[![Frontend](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](cloud/frontend)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**An academic engineering prototype (3rd-Year Capstone Project) for peer-to-peer emergency distress messaging, encrypted local persistence, and tactical command center coordination.**

</div>

---

> **Engineering Honesty & Status Disclosure:**  
> Nexus Support is an active working prototype and proof-of-concept for disaster relief operations. Automated CI test suites are passing across all tiers (128 backend pytest, 17 frontend Vitest, 149 Android Gradle tasks). However, live over-the-air multi-hop RF testing on physical LoRa hardware, physical multi-device deployment in austere terrain, and production multi-region cloud clustering remain future release milestones. See [docs/LIMITATIONS_AND_FUTURE_WORK.md](docs/LIMITATIONS_AND_FUTURE_WORK.md).

---

## What is Nexus Support?

When municipal power, cellular towers, and internet connections collapse during catastrophic weather or earthquakes, civilians and first responders are cut off. Nexus Support bridges this gap through two operational tiers:

1. **Austere Edge Tier (Mobile Mesh)**: Android devices communicate directly over peer-to-peer wireless links (Google Nearby Connections BLE/Wi-Fi Direct) forming an ad-hoc multi-hop mesh. SOS distress beacons receive highest priority (P10) preemption, while messages are encrypted locally using SQLCipher and in transit using Google Tink AEAD.
2. **Tactical Command Tier (Operations Center)**: When a mobile node reaches cellular, satellite, or local field LAN coverage, it acts as a Gateway. Distress beacons are ingested into a FastAPI backend with PostgreSQL/PostGIS and streamed live to an incident command dashboard with offline tile fallback support.

---

## System Architecture

```text
               +--------------------------------------------+
               |         Austere Field Mesh Network         |
               | (Google Nearby Connections / BLE / P2P)    |
               +--------------------------------------------+
                     |                 |                |
             [Victim Node]      [Relay Node]    [Responder Node]
               (SOS P10)          (Mesh Hop)       (Downlinks)
                     \                 /
                      v               v
                     +------------------+
                     |   Gateway Node   |  (OutboxEntity / GatewaySyncWorker)
                     +------------------+
                               |
                   HTTPS POST /api/v1/sync/sos
                   (X-Gateway-Key Authentication)
                               |
                               v
                     +------------------+
                     | FastAPI Backend  |  (PostGIS / Transition State Machine)
                     +------------------+
                               |
                        WebSocket Events
                               |
                               v
                     +------------------+
                     | React Dashboard  |  (TacticalMap.jsx / Offline Tiles)
                     +------------------+
```

For complete architecture diagrams, module ownership, and design decisions, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Repository Guide

| Directory | Subsystem | Key Technologies & Responsibilities |
| :--- | :--- | :--- |
| **`app/`** | Android Application | Jetpack Compose UI, Material 3, ViewModels, `GatewaySyncWorker.kt`, `AssignmentNotificationHelper.kt`, `OfflineRagManager.kt`. |
| **`core/mesh/`** | Mesh Network Engine | `MeshRouter.kt` (P10 priority preemption, deduplication, TTL hops), `NearbyConnectionsTransport.kt`, packet serialization. |
| **`core/data/`** | Persistence & Network | Encrypted Room DB (`DatabaseModule.kt` via SQLCipher 4.9.0), `OutboxEntity.kt`, Retrofit API service (`MeshBackendService.kt`). |
| **`core/crypto/`** | Security & Cryptography | Google Tink AEAD (AES-256-GCM) and ECIES hybrid encryption (`TinkCryptoManager.kt`), Android Keystore. |
| **`core/domain/`** | Pure Domain Models | Pure Kotlin domain entities (`Incident.kt`, `Node.kt`, `Responder.kt`, `Message.kt`), zero Android dependencies. |
| **`cloud/backend/`** | Cloud Backend API | FastAPI service, PostgreSQL 15 + PostGIS spatial queries, Alembic migrations (`6f1a8b92c431`), WebSocket event hub. |
| **`cloud/frontend/`** | Command Center UI | React 18, Vite, React-Leaflet (`TacticalMap.jsx`), sound alerts, offline tile fallback (`docs/OFFLINE_TILES_GUIDE.md`). |
| **`docs/`** | Technical Documentation | System architecture, security threat models, testing guides, limitations, and examiner Viva Q&A. |
| **`scripts/`** | Operations & Automation | `seed_demo.py`, `demo_flow.md` walkthrough, legacy SQLite-to-PG migration CLI (`import_sqlite_to_pg.py`). |

---

## Getting Started

### 1. Prerequisites
- **Android**: Android Studio Hedgehog+, JDK 21 (`JAVA_HOME`), Android SDK API 26+ (Target API 34).
- **Backend**: Python 3.11+, PostgreSQL 15 with PostGIS extension (or Docker).
- **Frontend**: Node.js 18+ (tested on Node 20/24) and npm.

---

### 2. Cloud Backend Setup

```bash
cd cloud/backend

# Create and activate virtual environment
python -m venv .venv
# On Windows PowerShell:
.venv\Scripts\Activate.ps1
# On Linux/macOS:
source .venv/bin/activate

# Install dependencies
pip install --upgrade pip
pip install -r requirements.txt

# Run database migrations
alembic upgrade head

# Start local server
python main.py
# Server runs on http://localhost:8000
```

Verify backend health:
```bash
curl -s http://localhost:8000/health
# Returns: {"status":"healthy","version":"1.0.0","database_connected":true,"migration_head":"6f1a8b92c431"}
```

**Environment Variables (`cloud/backend/.env`):**
| Variable | Default / Development | Production Requirement |
| :--- | :--- | :--- |
| `ENVIRONMENT` | `development` | Set to `production` |
| `DATABASE_URL` | `postgresql://meshlink_user:meshlink_pass@localhost:5432/meshlink_db` | High-availability PostgreSQL URL with PostGIS |
| `GATEWAY_API_KEYS` | `meshlink_gw_dev_secret` | High-entropy secrets. Default dev key is **strictly rejected** in production. |
| `JWT_SECRET` | Development secret | 256-bit cryptographic secret |

---

### 3. Tactical Command Center Dashboard Setup

```bash
cd cloud/frontend

# Install dependencies
npm install

# Run Vitest test suite
npm run test

# Build production bundle
npm run build

# Start local development server
npm run dev
# Dashboard runs on http://localhost:5173
```

**Offline Disaster Mapping (`cloud/frontend/.env.local`):**
```env
# Optional: point to local offline XYZ tile HTTP server (see docs/OFFLINE_TILES_GUIDE.md)
VITE_LOCAL_TILE_URL=http://localhost:8080/{z}/{x}/{y}.png
```

---

### 4. Android Mobile Application Setup

```bash
# Clone and enter repo
git clone https://github.com/deekshudevang/Nexus-Support.git
cd Nexus-Support

# Optional: configure production gateway key in local.properties
echo "MESHLINK_GATEWAY_KEY=meshlink_gw_dev_secret" >> local.properties

# Build debug APK
# On Windows:
.\gradlew.bat assembleDebug
# On Linux/macOS:
./gradlew assembleDebug

# Output APK located at: app/build/outputs/apk/debug/app-debug.apk
```

---

### 5. Running with Docker Compose

To launch the full cloud stack (PostgreSQL + PostGIS, Backend, Frontend) with a single command:
```bash
docker compose up --build -d
```
- Frontend Dashboard: `http://localhost:3000` (or `http://localhost:5173`)
- Backend API & Swagger: `http://localhost:8000/docs`
- Health Endpoint: `http://localhost:8000/health`

---

## Live Demonstration Walkthrough

To execute a complete end-to-end incident coordination walkthrough:
1. **Walkthrough Guide**: Follow [scripts/demo_flow.md](scripts/demo_flow.md) for 7 copy-paste `curl` commands demonstrating SOS distress ingestion, auto-created incidents, dispatch assignment, gateway downlinks, and responder status sync.
2. **Database Seeding**: Run `python scripts/seed_demo.py` to seed `org-nexus-demo`, dispatcher `demo_dispatcher` (`DemoPass123!`), and field responders.
3. **Automated Integration Test**: Run `py -m pytest cloud/backend/test_pg_p33_demo_flow_integration.py -v` to verify the entire flow programmatically.

---

## Verification & Quality Gates

Run clean test suites across all tiers:

```bash
# 1. Backend tests (128 passed)
py -m pytest cloud/backend -q

# 2. Frontend tests & production build (17 passed)
npm --prefix cloud/frontend test -- --run
npm --prefix cloud/frontend run build

# 3. Android unit tests (149 actionable tasks passed)
.\gradlew.bat clean testDebugUnitTest
```

---

## Project Documentation Index

- [System Architecture](docs/ARCHITECTURE.md) — System topology, component ownership, data flows, and design decision analysis.
- [Security Policy & Threat Model](docs/SECURITY.md) — STRIDE threat model, safety invariants, trust boundaries, and known gaps.
- [Testing & Verification Guide](docs/TESTING.md) — Execution commands, baselines, and scope across all three tiers.
- [Limitations & Future Work](docs/LIMITATIONS_AND_FUTURE_WORK.md) — Honest review of prototype constraints and future roadmap.
- [Viva Examination Q&A Guide](docs/VIVA_QA.md) — 25 technical questions and answers for academic examination.
- [Offline Tiles Hosting Guide](docs/OFFLINE_TILES_GUIDE.md) — Operational instructions for serving local map tiles in disaster zones.
- [Acceptance Criteria](docs/ACCEPTANCE_CRITERIA.md) — Formal milestone acceptance criteria.

---

## License

MIT License — see [LICENSE](LICENSE).
