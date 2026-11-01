# Nexus Support (RescueMesh) — Legacy API Contract Snapshot

**Contract Frozen Date:** 2026-11-01  
**Status:** FROZEN (Protected against breaking regressions)  
**Reference Source:** `cloud/backend/main.py`, `cloud/backend/test_backend.py`  

---

## 1. Authentication & Role Separation

### 1.1 Credentials and Authorization Models
- **Gateway Sync Write Credential:** Passed via `X-Gateway-Key` header or `Authorization: Bearer <key>`.
- **Operator Read Credential:** Passed via `Authorization: Bearer <token>` or `X-Operator-Token` header.
- **Revocation:** Header credentials matching `REVOKED_CREDENTIALS` return HTTP 403 Forbidden.

---

## 2. Ingestion Endpoints (Write-Only, Gateway)

### 2.1 SOS Packet Ingestion
- **Route:** `POST /api/sync/sos` and `POST /api/v1/sync/sos`
- **Auth:** Requires Gateway credential.
- **Request Body:** Array of JSON objects, max 500 packets per batch.
```json
[
  {
    "uuid": "string (1-128 chars)",
    "senderId": "string (1-128 chars)",
    "senderName": "string (1-128 chars)",
    "emergencyType": "string (1-64 chars)",
    "severity": 1,
    "latitude": 37.7749,
    "longitude": -122.4194,
    "gpsFixAvailable": true,
    "message": "string (max 4096 chars)",
    "timestamp": 1726815600,
    "status": "string (1-32 chars)",
    "hopCount": 0
  }
]
```
- **Response Body (200 OK):**
```json
{
  "status": "success",
  "synced_count": 1,
  "receipts": [
    {
      "uuid": "string",
      "status": "ACKNOWLEDGED",
      "synced": true
    }
  ]
}
```

### 2.2 Node Telemetry Ingestion
- **Route:** `POST /api/sync/nodes`
- **Auth:** Requires Gateway credential.
- **Request Body:** Array of JSON objects, max 1000 nodes per batch.
```json
[
  {
    "deviceId": "string (1-128 chars)",
    "displayName": "string (1-128 chars)",
    "role": "string (1-64 chars)",
    "batteryLevel": 85,
    "needsHelp": false,
    "latitude": 37.7749,
    "longitude": -122.4194,
    "timestamp": 1726815600
  }
]
```
- **Response Body (200 OK):**
```json
{
  "status": "success",
  "synced_count": 1
}
```

---

## 3. Operations Endpoints (Read-Only, Operator)

### 3.1 Retrieve SOS Incidents
- **Route:** `GET /api/sos`
- **Query Params:** `limit` (int, default 100, 1-1000), `severity_min` (optional int, 1-10)
- **Auth:** Requires Operator credential.
- **Response Body (200 OK):** Array of objects matching `SosPacket`.

### 3.2 Retrieve Node Telemetry
- **Route:** `GET /api/nodes`
- **Query Params:** `limit` (int, default 200, 1-1000)
- **Auth:** Requires Operator credential.
- **Response Body (200 OK):** Array of objects matching `NodeStatus`.

---

## 4. Operator Session & WebSocket

### 4.1 Operator Password Login
- **Route:** `POST /api/auth/token`
- **Request Body:**
```json
{
  "username": "string",
  "password": "string"
}
```
- **Response Body (200 OK):**
```json
{
  "access_token": "string",
  "token_type": "bearer",
  "role": "operator"
}
```

### 4.2 WebSocket Ticket Issuance
- **Route:** `POST /api/auth/ws-ticket`
- **Auth:** Requires Operator credential.
- **Response Body (200 OK):**
```json
{
  "ticket": "string",
  "expires_in": 30
}
```

### 4.3 WebSocket Live Alert Stream
- **Route:** `GET /ws/alerts?ticket=<ticket>`
- **Auth:** Single-use 30s ticket or initial JSON payload `{"type": "auth", "token": "<operator_token>"}`.
- **Real-Time Delivery Envelope:**
```json
{
  "type": "sos_alert",
  "packet": { ... }
}
```

---

## 5. System Health
- **Route:** `GET /health`
- **Auth:** None (Public)
- **Response Body (200 OK):**
```json
{
  "status": "ok",
  "environment": "development",
  "timestamp": 1726815600,
  "service": "MeshLink Gateway Backend"
}
```
