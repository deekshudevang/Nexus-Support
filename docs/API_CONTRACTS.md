# Nexus Support (RescueMesh) — Canonical API & Contract Specification

**Specification Version:** 1.0  
**Effective Date:** 2026-11-01  
**Status:** Approved / Enforced  

---

## 1. Authentication & Role Separation

The platform strictly isolates write ingestion capabilities from operations center monitoring to prevent unauthorized injection of false SOS beacons or telemetry poisoning.

| Role | Permitted Headers | Permissions | Endpoints |
|---|---|---|---|
| **Gateway Node** | `X-Gateway-Key: <secret>` or `Authorization: Bearer <secret>` | WRITE-ONLY (Ingestion) | `POST /api/v1/sync/sos`, `POST /api/v1/sync/nodes` |
| **Tactical Operator** | `Authorization: Bearer <operator_token>` | READ-ONLY + WS Ticket | `GET /api/sos`, `GET /api/nodes`, `POST /api/auth/ws-ticket` |
| **Anonymous/Client** | None | Public status only | `GET /health` |

---

## 2. Gateway Ingestion Contracts

### 2.1 SOS Packet Ingestion Envelope
- **Endpoint:** `POST /api/v1/sync/sos` (Alias: `POST /api/sync/sos`)
- **Headers:** `X-Gateway-Key: <secret>`
- **Content-Type:** `application/json`
- **Batch Limit:** 500 items per request

#### Payload Schema
```json
[
  {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "node-alpha-99",
    "senderName": "Search Team Lead",
    "emergencyType": "MEDICAL",
    "severity": 8,
    "latitude": 37.774929,
    "longitude": -122.419416,
    "gpsFixAvailable": true,
    "message": "Trauma victim stabilized, stretcher required",
    "timestamp": 1726815600000,
    "status": "PENDING",
    "hopCount": 2
  }
]
```

#### Field Specifications:
- `uuid` (string, 1-128 chars, required): Cryptographically unique UUID.
- `senderId` (string, 1-128 chars, required): Public cryptographic identity or node ID.
- `senderName` (string, 0-128 chars): Human-readable alias or unit name.
- `emergencyType` (string, required): `MEDICAL` | `FIRE` | `FLOOD` | `EARTHQUAKE` | `SECURITY` | `TRAPPED` | `LOST` | `OTHER`.
- `severity` (integer, 1-10, required): Triage priority level (1 = lowest, 10 = critical).
- `latitude` (float, nullable, -90.0 to 90.0): GPS latitude or `null` if no fix available.
- `longitude` (float, nullable, -180.0 to 180.0): GPS longitude or `null` if no fix available.
- `gpsFixAvailable` (boolean, optional, default true): Explicit indication of whether GPS hardware had a truthful fix when beacon was created.
- `message` (string, max 4096 chars, required): Emergency text or triage notes.
- `timestamp` (integer, epoch milliseconds or seconds, required).
- `status` (string, required): `PENDING` | `SENT` | `DELIVERED` | `ACKNOWLEDGED` | `RELAYED` | `CANCELLED`.
- `hopCount` (integer, 0-255, optional, default 0): Number of mesh hops traversed.

#### Ingestion Response Schema:
```json
{
  "status": "success",
  "synced_count": 1,
  "receipts": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "status": "ACKNOWLEDGED",
      "synced": true
    }
  ]
}
```

---

## 3. WebSocket Real-Time Alert Stream

- **Endpoint:** `GET /ws/alerts?ticket=<ticket>`
- **Ticket Expiration:** 30 seconds, single-use, issued via `POST /api/auth/ws-ticket`.
- **Payload Event Type:** `sos_alert`

```json
{
  "type": "sos_alert",
  "packet": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "node-alpha-99",
    "senderName": "Search Team Lead",
    "emergencyType": "MEDICAL",
    "severity": 8,
    "latitude": 37.774929,
    "longitude": -122.419416,
    "gpsFixAvailable": true,
    "message": "Trauma victim stabilized, stretcher required",
    "timestamp": 1726815600000,
    "status": "PENDING",
    "hopCount": 2
  }
}
```
