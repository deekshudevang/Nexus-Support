# TASK.md — Milestone M3.15 to M3.24: Production Readiness Execution

## Master Progress Dashboard

| Phase | Description | Status | Evidence / Report |
|---|---|---|---|
| **M3.15** | Repository and Architecture Audit | **AUTOMATED TESTED** | `docs/M3_15_ARCHITECTURE_AUDIT.md` (CI updated, baseline confirmed) |
| **M3.16** | Production PostgreSQL & PostGIS | **AUTOMATED TESTED** | `docs/M3_16_POSTGRES_POSTGIS.md` (Invariant 9 verified, spatial migrations roundtrip tested) |
| **M3.17** | Incident & Responder Management | **IN PROGRESS** | `cloud/backend/test_pg_p3_incidents.py`, `app/services/incident_service.py` |
| **M3.18** | SOS Reliability & Delivery Guarantees | **NOT STARTED** | `cloud/backend/test_pg_p5_events.py`, idempotency & outbox fault injection |
| **M3.19** | BLE, Wi-Fi Direct & Meshtastic Reliability | **NOT STARTED** | `core/mesh`, simulated mesh routing & priority queue tests |
| **M3.20** | Offline Gemma AI & Emergency RAG | **NOT STARTED** | `app/ai`, safety guard & non-blocking invariant tests |
| **M3.21** | Web Tactical Command Center Integration | **NOT STARTED** | `cloud/frontend`, Vitest E2E workflow & real WSS integration |
| **M3.22** | Security & Privacy Audit | **NOT STARTED** | Threat model, audit logs, `pip-audit`, `npm audit`, Keystore review |
| **M3.23** | Physical Device & Network QA | **BLOCKED (HARDWARE)** | `docs/M3_23_PHYSICAL_DEVICE_QA.md` (Physical device required for L5) |
| **M3.24** | Production Deployment & Release Certification | **NOT STARTED** | `docs/M3_24_RELEASE_CERTIFICATION.md` (Final GO / NO-GO) |

---

## Task Details Table

| Task ID | Phase | Description | Status | Files Modified | Tests Executed | Results | Commit Hash | Outstanding Defects | External Deps |
|---|---|---|---|---|---|---|---|---|---|
| **TASK-315-01** | M3.15 | Step 0 Baseline Verification | AUTOMATED TESTED | N/A | `pytest`, `npm test`, `./gradlew testDebugUnitTest` | 74 backend passed, 9 frontend passed, 142 Android tasks passed | `b085a38` | None | Clean baseline |
| **TASK-315-02** | M3.15 | Full Architecture & Gap Audit | AUTOMATED TESTED | `docs/M3_15_ARCHITECTURE_AUDIT.md` | Doc validation & review | Requirements classified, risks registered | Staged | None | None |
| **TASK-315-03** | M3.15 | CI Configuration & Dep Fixes | AUTOMATED TESTED | `.github/workflows/ci.yml`, `cloud/backend/requirements.txt` | `pytest cloud/backend` | 74 passed | Staged | None | GitHub Actions |
| **TASK-316-01** | M3.16 | Fail-Fast SQLite Fallback Invariant | AUTOMATED TESTED | `app/core/config.py`, `app/core/database.py`, `main.py` | `test_pg_p16_postgres_postgis.py` | 6 passed | Staged | None | None |
| **TASK-316-02** | M3.16 | PostGIS Geometry Migration & Spatial Index | AUTOMATED TESTED | `alembic/versions/3b9a1f28e05c_*`, `alembic/env.py` | `test_pg_p16_postgres_postgis.py` | Roundtrip passed | Staged | None | PostGIS |
| **TASK-317-01** | M3.17 | State Machine & Illegal Transition Matrix | NOT STARTED | `app/services/incident_service.py`, `app/services/dispatch_service.py` | `test_state_machine.py` | Pending | Pending | DEF-03 | None |
| **TASK-318-01** | M3.18 | Outbox Fault Injection & WebSocket Replay | NOT STARTED | `app/routers/events.py`, `app/services/outbox_publisher.py` | `test_fault_injection.py` | Pending | Pending | DEF-04 | None |
| **TASK-319-01** | M3.19 | Simulated Mesh Priority & Invariant 7 Test | NOT STARTED | `core/mesh` | `testMeshPriority.kt` | Pending | Pending | DEF-05 | Hardware for L5 |
| **TASK-320-01** | M3.20 | Non-Blocking AI Invariant 8 & Safety Guard | NOT STARTED | `app/ai` | `testAiPreemption.kt` | Pending | Pending | DEF-06 | NPU for L5 |
| **TASK-321-01** | M3.21 | Web Command E2E Workflow Automation | NOT STARTED | `cloud/frontend` | `npm test` | Pending | Pending | DEF-07 | Browser |
| **TASK-322-01** | M3.22 | Security Audit & Vulnerability Scanning | NOT STARTED | `docs/M3_22_SECURITY_AUDIT.md` | `pip-audit`, `npm audit` | Pending | Pending | DEF-08 | None |
| **TASK-323-01** | M3.23 | Physical Device Protocol & Evidence | BLOCKED (HARDWARE) | `docs/M3_23_PHYSICAL_DEVICE_QA.md` | ADB & LoRa tests | BLOCKED (no physical device connected) | Pending | Physical USB | USB Debugging |
| **TASK-324-01** | M3.24 | Final Release Checklist & GO / NO-GO | NOT STARTED | `docs/M3_24_RELEASE_CERTIFICATION.md` | Release drill | Pending | Pending | None | Pilot Signoff |

---

## Resume Notes & Instructions

- **Current Branch:** `m3-15-to-m3-24-production`
- **Current Phase:** Transitioning from M3.15 (Audit complete) to M3.16 (PostgreSQL & PostGIS production readiness).
- **How to Re-verify Entire System:**
  - Backend: `cd cloud/backend; py -m pytest -v` (74 tests)
  - Frontend: `cd cloud/frontend; npm test -- --run` (9 tests)
  - Android: `.\gradlew.bat testDebugUnitTest :core:domain:test` (142 tasks)
- **Open Blockers:** Physical hardware tests (Motorola Edge 50 Pro and SX1262 LoRa module) are marked `BLOCKED (HARDWARE)`. Simulated tests are used for all automated algorithmic validation.
