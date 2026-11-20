# Nexus-Support: Complete Operational Demo Walkthrough

This document outlines the complete, copy-pasteable end-to-end operational flow of **Nexus-Support**:
from emergency SOS initiation on an offline mesh node, through edge gateway cloud synchronization, real-time tactical command center incident display, dispatcher tasking, downlink delivery to responder handset, field status updates, to incident resolution.

---

## Pre-requisites & Environment Setup

1. **Start Backend & Database Services:**
   ```bash
   # Option A: Local Python & PostgreSQL / SQLite dev server
   cd cloud/backend
   uvicorn main:app --host 0.0.0.0 --port 8000

   # Option B: Docker Compose (all services)
   docker compose up --build
   ```

2. **Start Tactical Command Dashboard:**
   ```bash
   cd cloud/frontend
   npm install && npm run dev
   # Dashboard available at http://localhost:5173
   ```

3. **Seed Demo Entities:**
   ```bash
   python scripts/seed_demo.py
   ```
   *Creates:*
   - Organization: `org-nexus-demo`
   - Dispatcher: `demo_dispatcher` (Password: `DemoPass123!`)
   - Responders: `SAR-EAGLE-1` (Device: `node-alpha-phone`), `MEDIC-HAWK-2` (Device: `node-bravo-phone`)
   - Initial Incidents: `inc-demo-001` (Sector 4 Search), `inc-demo-002` (Creek Crossing Flood)

---

## Step 1: Emergency SOS Triggered on Mobile Handset

A civilian or field operative triggers an SOS alert on the Android app (or ad-hoc Bluetooth Low Energy mesh).

* **Android Screen State:**
  - Emergency SOS Screen displays active red pulsating beacon: `"SOS TRANSMITTING OVER MESH"`.
  - Local SQLite/SQLCipher DB stores packet with `status = "PENDING"`.

* **Mesh Handset / Gateway Ingestion (Copy-Pasteable cURL):**
  ```bash
  curl -X POST http://localhost:8000/api/v1/sync/sos \
    -H "Content-Type: application/json" \
    -H "X-Gateway-Key: meshlink_gw_dev_secret" \
    -H "X-Organization-Id: org-nexus-demo" \
    -H "X-Device-Id: node-alpha-phone" \
    -d '[
      {
        "uuid": "sos-live-9001",
        "senderId": "node-alpha-phone",
        "senderName": "Civilian Hiker Alpha",
        "emergencyType": "SEARCH",
        "severity": 8,
        "latitude": 37.7765,
        "longitude": -122.4172,
        "gpsFixAvailable": true,
        "message": "Trapped on steep ridge after trail collapse. Need extraction.",
        "timestamp": 1700000000,
        "status": "ACTIVE",
        "hopCount": 2
      }
    ]'
  ```

* **Expected API Response (HTTP 200):**
  ```json
  {
    "status": "success",
    "synced_count": 1,
    "receipts": [
      {
        "uuid": "sos-live-9001",
        "status": "ACKNOWLEDGED",
        "synced": true
      }
    ],
    "downlinks": []
  }
  ```

* **Expected Log Output:**
  ```text
  INFO: DEMO FLOW: SOS ingested from sender node-alpha-phone (UUID: sos-live-9001, type: SEARCH, severity: 8)
  ```

---

## Step 2: Real-Time Incident Display on Tactical Command Dashboard

The backend ingests the SOS packet, automatically generates an official incident in PostgreSQL, and broadcasts the event via WebSocket.

* **Dashboard Screen State (http://localhost:5173):**
  - Audio emergency alert chime plays.
  - Incident Queue displays urgent Red Card: `#1003 — SEARCH: Civilian Hiker Alpha (Severity 8/10)`.
  - Tactical Map centers on `(37.7765, -122.4172)` displaying flashing high-severity beacon marker.
  - Active tile layer indicates `"ONLINE TILES"` (or `"OFFLINE TILES"` if local raster server active).

* **Verification cURL (Fetch Created Incident):**
  ```bash
  curl -X GET "http://localhost:8000/api/v1/incidents" \
    -H "X-Gateway-Key: meshlink_gw_dev_secret" \
    -H "X-Organization-Id: org-nexus-demo"
  ```

* **Expected Response:**
  List containing incident with `id: "inc-sos-live-9001"`, `title: "SEARCH: Civilian Hiker Alpha"`, `priority: 1`, `status: "REPORTED"`.

---

## Step 3: Dispatcher Dispatches SAR Responder

In the Tactical Dashboard Dispatch Panel (or via Dispatch API), the incident commander assigns `SAR-EAGLE-1` (`resp-demo-alpha`) to the incident.

* **Dashboard Action:**
  - Commander clicks "DISPATCH" button on Incident `#inc-sos-live-9001`.
  - Selects `SAR-EAGLE-1 (node-alpha-phone)` from available responders list.
  - Sets assignment role to `LEAD`.
  - Clicks "CONFIRM DISPATCH".

* **Copy-Pasteable cURL:**
  ```bash
  curl -X POST "http://localhost:8000/api/v1/dispatch/incidents/inc-sos-live-9001/assignments" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer meshlink_op_dev_secret" \
    -H "X-Organization-Id: org-nexus-demo" \
    -d '{
      "responder_id": "resp-demo-alpha",
      "role": "LEAD",
      "notes": "Deploy to North Ridge trail coordinates with rescue litter"
    }'
  ```

* **Expected API Response (HTTP 201 Created):**
  ```json
  {
    "id": "asgn_xxxx",
    "incident_id": "inc-sos-live-9001",
    "responder_id": "resp-demo-alpha",
    "status": "DISPATCHED",
    "organization_id": "org-nexus-demo"
  }
  ```

* **Expected Log Output:**
  ```text
  INFO: DEMO FLOW: Assignment created for incident inc-sos-live-9001 to responder resp-demo-alpha (Assignment: asgn_xxxx)
  ```

---

## Step 4: Downlink Delivered to Mobile Handset

When `node-alpha-phone` syncs with the mesh gateway via `GatewaySyncWorker`:

* **Downlink Fetch cURL:**
  ```bash
  curl -X POST http://localhost:8000/api/v1/sync/sos \
    -H "Content-Type: application/json" \
    -H "X-Gateway-Key: meshlink_gw_dev_secret" \
    -H "X-Organization-Id: org-nexus-demo" \
    -H "X-Device-Id: node-alpha-phone" \
    -d '[]'
  ```

* **Expected API Response (HTTP 200):**
  ```json
  {
    "status": "success",
    "ingested": 0,
    "receipts": [],
    "downlinks": [
      {
        "id": "asgn_xxxx",
        "incident_id": "inc-sos-live-9001",
        "responder_id": "resp-demo-alpha",
        "status": "DISPATCHED",
        "role": "LEAD",
        "notes": "SEARCH: Civilian Hiker Alpha",
        "latitude": 37.7765,
        "longitude": -122.4172,
        "severity": 8,
        "priority": 1
      }
    ]
  }
  ```

* **Android Screen State:**
  - Android System Notification triggers: `"DISPATCH ASSIGNMENT: SEARCH: Civilian Hiker Alpha"`.
  - Field Assignments screen highlights urgent unaccepted mission with "ACCEPT" / "DECLINE" buttons.
  - Tapping the notification deep-links directly into the Assignment Details screen.

---

## Step 5: Responder Advances Mission Lifecycle

The responder accepts the mission and progresses through field milestones.
Each step uses the dedicated REST status endpoint: `POST /api/v1/sync/assignments/{id}/status`.

### 5A. Unit ACCEPTS Assignment:
```bash
curl -X POST "http://localhost:8000/api/v1/sync/assignments/asgn_xxxx/status" \
  -H "Content-Type: application/json" \
  -H "X-Gateway-Key: meshlink_gw_dev_secret" \
  -H "X-Organization-Id: org-nexus-demo" \
  -H "X-Device-Id: node-alpha-phone" \
  -d '{"status": "ACCEPTED"}'
```
*Response:* `{"status": "success", "assignment_status": "ACCEPTED", "responder_availability": "ASSIGNED"}`.

### 5B. Unit Reports EN_ROUTE:
```bash
curl -X POST "http://localhost:8000/api/v1/sync/assignments/asgn_xxxx/status" \
  -H "Content-Type: application/json" \
  -H "X-Gateway-Key: meshlink_gw_dev_secret" \
  -H "X-Organization-Id: org-nexus-demo" \
  -H "X-Device-Id: node-alpha-phone" \
  -d '{"status": "EN_ROUTE"}'
```
*Response:* `{"status": "success", "assignment_status": "EN_ROUTE", "responder_availability": "ASSIGNED"}`.

### 5C. Unit Arrives ON_SCENE:
```bash
curl -X POST "http://localhost:8000/api/v1/sync/assignments/asgn_xxxx/status" \
  -H "Content-Type: application/json" \
  -H "X-Gateway-Key: meshlink_gw_dev_secret" \
  -H "X-Organization-Id: org-nexus-demo" \
  -H "X-Device-Id: node-alpha-phone" \
  -d '{"status": "ON_SCENE"}'
```
*Response:* `{"status": "success", "assignment_status": "ON_SCENE", "responder_availability": "ACTIVE"}`.

### 5D. Unit Completes Mission (COMPLETED):
```bash
curl -X POST "http://localhost:8000/api/v1/sync/assignments/asgn_xxxx/status" \
  -H "Content-Type: application/json" \
  -H "X-Gateway-Key: meshlink_gw_dev_secret" \
  -H "X-Organization-Id: org-nexus-demo" \
  -H "X-Device-Id: node-alpha-phone" \
  -d '{"status": "COMPLETED"}'
```
*Response:* `{"status": "success", "assignment_status": "COMPLETED", "responder_availability": "AVAILABLE"}`.

* **Expected Log Output for each transition:**
  ```text
  INFO: DEMO FLOW: Assignment asgn_xxxx status updated to ACCEPTED by device node-alpha-phone
  INFO: DEMO FLOW: Assignment asgn_xxxx status updated to EN_ROUTE by device node-alpha-phone
  INFO: DEMO FLOW: Assignment asgn_xxxx status updated to ON_SCENE by device node-alpha-phone
  INFO: DEMO FLOW: Assignment asgn_xxxx status updated to COMPLETED by device node-alpha-phone
  ```

---

## Step 6: Mission Closure & Verification

* **Dashboard Final State:**
  - Responder `SAR-EAGLE-1` icon on Tactical Map turns Green (`AVAILABLE`).
  - Incident Status updates to `RESOLVED` / `CLOSED`.
  - Assignment record in timeline indicates completion timestamp.
