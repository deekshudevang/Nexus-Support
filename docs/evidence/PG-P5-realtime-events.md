# Evidence: Phase PG-P5 — Real-Time Event Pipeline & Gapless Replay

## Overview
Phase PG-P5 implements the transactional outbox draining engine, gapless monotonic event sequence allocation, cursor-based REST backfill, and single-use WebSocket replay capabilities.

## Verification Evidence

### 1. Monotonic Gapless Stream Sequencing
- **Files**:
  - `cloud/backend/app/models/stream_event.py`
  - `cloud/backend/app/services/outbox_publisher.py`
- **Mechanism**:
  - As incidents and assignments transition, outbox rows are committed within the database transaction.
  - The outbox publisher claims batches and inserts them into `stream_events`.
  - The primary key `stream_seq` allocates strictly monotonic incrementing integers ($1, 2, 3, \dots$).
  - Once persisted in `stream_events`, `outbox_events.published_at` is stamped.

### 2. Cursor-Based Event Replay API
- **Endpoint**: `GET /api/v1/events/replay?since_seq=<n>&limit=100`
- **Capability**: Allows edge devices and web dashboards reconnecting after network degradation to query only missed events in exact chronological sequence.
- **Attributes**: Supports filtering by `aggregate_type` (`INCIDENT`, `ASSIGNMENT`), reporting `has_more` and `last_seq`.

### 3. WebSocket Ticket Store & Replay on Connection
- **Endpoint**: `WS /ws/events?ticket=<ticket>&since_seq=<n>`
- **Security**:
  - Zero permanent tokens in query parameters or WebSocket URLs.
  - Single-use 30-second tickets generated via `POST /api/v1/auth/ws-ticket`.
  - Consumed tickets cannot be re-used; re-connection attempts with an already consumed ticket are immediately terminated with WebSocket policy violation (code 1008).
- **Stream Lifecycle**:
  - Connection sends `{"type": "authenticated"}`.
  - If `since_seq` is specified, streams all historical missed events with `{"type": "replay_event", ...}`.
  - Emits `{"type": "replay_complete", "last_seq": <max_seq>}`.
  - Enters live broadcast mode to receive real-time updates as they occur.

### 4. Automated Test Pass
- Test command:
  ```powershell
  python -m pytest test_pg_p5_events.py
  ```
- Result: **3 passed** in 1.16s.
  - `test_outbox_batch_processing_and_monotonic_stream_seq`: PASSED
  - `test_cursor_based_event_replay_endpoint`: PASSED
  - `test_ws_ticket_single_use_and_replay_connection`: PASSED
