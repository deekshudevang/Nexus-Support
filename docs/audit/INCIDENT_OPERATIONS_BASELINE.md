# Incident Operations Baseline Audit (PG-P0)

**Audit Timestamp:** 2026-11-01T20:05:00+05:30  
**Audit Scope:** Incident lifecycle, triage workflows, responder assignment models, and idempotency guarantees.  

---

## 1. Current State Inventory

In the existing codebase (`cloud/backend/main.py`), there is no dedicated `incidents` table separate from raw `sos_packets`. 

- Raw SOS packets double as incidents.
- Status values currently used: `PENDING`, `SENT`, `DELIVERED`, `ACKNOWLEDGED`, `RELAYED`, `CANCELLED`, `RESPONDED`.
- No tracking of incident commander, responder dispatch, assignment lifecycle, or triage progression.

---

## 2. Target Three-State-Machine Architecture (PG-P3 & PG-P4)

To prevent conflation and duplicate sources of truth, the incident domain must be structured into three distinct lifecycles:

1. **Incident Lifecycle (Stored, Incident Level):**
   `REPORTED` ➔ `TRIAGED` ➔ `ACTIVE` ➔ `CONTAINED` ➔ `RESOLVED` ➔ `CLOSED`
   *(Branch: `CANCELLED` from REPORTED/TRIAGED/ACTIVE; `REOPEN` from RESOLVED/CLOSED)*
2. **Assignment Lifecycle (Stored, Assignment Level):**
   `PROPOSED` ➔ `DISPATCHED` ➔ `ACCEPTED` ➔ `EN_ROUTE` ➔ `ON_SCENE` ➔ `COMPLETED`
   *(Branches: `DECLINED`, `CANCELLED`, `EXPIRED`)*
3. **Responder Availability (Stored, Responder Level):**
   `OFFLINE`, `AVAILABLE`, `BUSY`, `UNAVAILABLE`
   *Responder Operational State is derived dynamically from their active assignment (`ASSIGNED`, `EN_ROUTE`, `ON_SCENE`).*
4. **Derived Response State for Kanban Columns:**
   - No assignment ➔ `UNASSIGNED`
   - Any DISPATCHED ➔ `AWAITING_ACK`
   - Any ACCEPTED / EN_ROUTE ➔ `RESPONDING`
   - Any ON_SCENE ➔ `ON_SCENE`

---

## 3. Database Constraints Required for PG-P1 / PG-P3

- **Single Active Assignment per Responder:** Partial unique index:
  ```sql
  CREATE UNIQUE INDEX uq_active_responder_assignment 
  ON assignments(responder_id) 
  WHERE status IN ('DISPATCHED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE');
  ```
- **Unique Incident-Responder Pair:** Non-terminal assignments enforce `UNIQUE (incident_id, responder_id)`.
- **Nullable Coordinates:** Both lat/lon null or both valid (`CHECK ((lat IS NULL AND lon IS NULL) OR (lat BETWEEN -90 AND 90 AND lon BETWEEN -180 AND 180))`).
- **Append-Only Event Ledger:** Every lifecycle transition writes to `incident_events` in the same transaction.
