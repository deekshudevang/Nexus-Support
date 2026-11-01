# Real-Time Event Architecture Baseline Audit (PG-P0)

**Audit Timestamp:** 2026-11-01T20:05:00+05:30  
**Audit Scope:** WebSocket management, real-time alert delivery, ticket lifecycle, and event replay reliability.  

---

## 1. Current State Inventory

- **WebSocket Stream:** `/ws/alerts?ticket=<ticket>`
- **Ticket Generation:** In-memory dictionary `_ws_tickets` mapping token to expiration (30 seconds). Single use.
- **Connection Registry:** In-memory `AlertConnectionManager` holding set of active WebSocket instances.
- **Broadcast:** On SOS ingestion, iterates active connections and sends JSON payload.

---

## 2. Identified Deficiencies

1. **Process-Bound Concurrency:** Multiple backend instances cannot share in-memory WebSocket connections or tickets without an external pub/sub layer.
2. **Missing Outbox & Event Durability:** Events are broadcast directly in memory. If a client is disconnected or reconnects during an outage, intermediate events are permanently lost without a replay log.
3. **Sequence Visibility Trap Risk:** Replay cannot rely on a naive autoincrement ID because uncommitted transactions can assign sequences out of commit order.

---

## 3. Target Outbox & Event Replay Architecture (PG-P5)

1. **Transactional Outbox:**
   Domain modification + `outbox_events` row committed in the same database transaction.
2. **Gapless Stream Sequencing (`stream_events`):**
   A dedicated publisher claims batches via `SELECT ... FOR UPDATE SKIP LOCKED` and appends to `stream_events`, assigning a gapless `stream_seq` under an advisory lock.
3. **Cursor Replay:**
   Clients reconnect specifying their last seen `stream_seq` cursor:
   `GET /api/v1/events/replay?since_seq=<cursor>`
   Filtered through the client's current permissions at replay time.
4. **Multi-Instance Fan-Out & Fallback:**
   - Primary: Redis pub/sub for low-latency WebSocket fan-out.
   - Fallback: If Redis is unavailable, instances tail `stream_events` using Postgres `LISTEN/NOTIFY` or polling, ensuring zero event loss.
