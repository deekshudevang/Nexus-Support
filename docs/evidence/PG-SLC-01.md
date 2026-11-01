# Evidence: Phase PG-SLC-01 — Thin Vertical Slice

## Overview
Phase PG-SLC-01 proves the foundational architecture end-to-end through a thin vertical slice before building remaining domain areas:
$$\text{Device/Operator Ingestion} \longrightarrow \text{Domain Validation \& Database Persistence} \longrightarrow \text{Transactional Outbox} \longrightarrow \text{Monotonic Stream Sequence} \longrightarrow \text{Real-Time WebSocket Broadcast}$$

## Verification Evidence

### 1. Ingestion & Persistence
- **API**: `POST /api/v1/incidents`
- **Domain Persistence**:
  - `IncidentModel`: Persisted with `status="REPORTED"`, GPS coordinates, category, priority, severity.
  - `IncidentEventModel`: Append-only audit record created in the same database transaction.

### 2. Transactional Outbox Pattern
- **Table**: `outbox_events`
- **Event**: `incident.created` committed atomically within the domain transaction.
- **Processing**: `app.services.outbox_publisher.process_outbox_batch(db)` claims unprocessed events and records publication time.

### 3. Monotonic Stream Sequencing
- **Table**: `stream_events`
- **Sequence Assignment**: Monotonic `stream_seq` allocated upon promotion from `outbox_events`.
- **Replay Capability**: Enables gapless cursor-based replay (`?since_seq=<n>`).

### 4. Live Broadcast Delivery
- **Service**: `app.services.alert_manager.alert_manager`
- **Delivery**: Mock WebSocket client subscribed to `alert_manager` received live payload:
  ```json
  {
    "type": "event",
    "stream_seq": 1,
    "event_type": "incident.created",
    "aggregate_type": "INCIDENT",
    "aggregate_id": "inc_...",
    "payload": {
      "title": "Flash Flood Trapped Residents",
      "category": "FLOOD",
      "status": "REPORTED",
      "priority": 1,
      "severity": 4
    }
  }
  ```

### 5. Automated Test Pass
- Test: `test_pg_p3_incidents.py::test_thin_vertical_slice_sos_to_stream_and_broadcast`
- Status: **PASSED** (1.90s execution).
