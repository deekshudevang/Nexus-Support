# M3.17 — INCIDENT, RESPONDER MANAGEMENT & RBAC SPECIFICATION
**Milestone:** M3.17 Production Readiness Execution  
**Execution Timestamp:** 2026-11-03T18:40:00+05:30  
**Branch:** `m3-15-to-m3-24-production`  
**Test Suite:** `cloud/backend/test_pg_p17_incident_responder_lifecycle.py` (85/85 total backend tests green)  
**Status:** COMPLETE & VERIFIED

---

## 1. Incident Lifecycle & Formal State Machine

Incidents follow a deterministic state machine defined in `app/services/incident_service.py`:

```
               ┌───────────────────────┐
               │       REPORTED        │
               └───────────┬───────────┘
                           │ (Triage / Cancel)
                           ▼
               ┌───────────────────────┐
               │        TRIAGED        │◄──────────┐
               └───────────┬───────────┘           │
                           │ (Dispatch)            │ (Reopen)
                           ▼                       │
               ┌───────────────────────┐           │
               │      DISPATCHED       │           │
               └───────────┬───────────┘           │
                           │ (Arrive On Scene)     │
                           ▼                       │
               ┌───────────────────────┐           │
               │       ON_SCENE        │           │
               └───────────┬───────────┘           │
                           │ (Stabilize / Resolve) │
                           ▼                       │
               ┌───────────────────────┐           │
               │       RESOLVED        │───────────┘
               └───────────┬───────────┘
                           │ (Commander Close)
                           ▼
               ┌───────────────────────┐
               │        CLOSED         │
               └───────────────────────┘
```

### Complete Incident Transition Matrix

| From State | Allowed Target States | Illegal Target States (Rejected with HTTP 409) |
|---|---|---|
| `REPORTED` | `TRIAGED`, `CANCELLED` | `DISPATCHED`, `ON_SCENE`, `RESOLVED`, `CLOSED` |
| `TRIAGED` | `DISPATCHED`, `REPORTED`, `CANCELLED` | `ON_SCENE`, `RESOLVED`, `CLOSED` |
| `DISPATCHED` | `ON_SCENE`, `TRIAGED`, `CANCELLED` | `REPORTED`, `RESOLVED`, `CLOSED` |
| `ON_SCENE` | `RESOLVED`, `DISPATCHED`, `CANCELLED` | `REPORTED`, `TRIAGED`, `CLOSED` |
| `RESOLVED` | `CLOSED`, `TRIAGED` | `REPORTED`, `DISPATCHED`, `ON_SCENE`, `CANCELLED` |
| `CLOSED` | `TRIAGED` (Commander Reopen) | `REPORTED`, `DISPATCHED`, `ON_SCENE`, `RESOLVED`, `CANCELLED` |
| `CANCELLED` | *None (Terminal State)* | All transitions rejected |

---

## 2. Assignment Lifecycle & Double-Dispatch Prevention

Field assignments follow `ALLOWED_ASSIGNMENT_TRANSITIONS` in `app/services/dispatch_service.py`:

`PROPOSED` -> `DISPATCHED` -> `ACCEPTED` -> `EN_ROUTE` -> `ON_SCENE` -> `COMPLETED`  
(Alternative rejection paths: `DECLINED`, `CANCELLED`, `EXPIRED`)

### Concurrency Protection & Responder Availability
- When an assignment is created in `DISPATCHED` state, the responder's status transitions to `ASSIGNED`.
- Database partial unique index (`ix_assignments_active_responder` on `responder_id` where `status IN ('DISPATCHED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE')`) guarantees that a responder cannot be double-dispatched even under concurrent race conditions.
- Reaching a terminal status (`COMPLETED`, `DECLINED`, `CANCELLED`, `EXPIRED`) automatically releases the responder's availability back to `AVAILABLE`, enabling safe reassignment.

---

## 3. Server-Side Resource-Level RBAC Matrix

Enforced via `require_permission()` dependencies in `app/core/rbac.py`:

| Action / Endpoint | Admin | Commander | Dispatcher | Responder | Viewer | Anonymous |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `POST /api/v1/incidents` (Create) | ALLOW | ALLOW | ALLOW | DENY (403) | DENY (403) | DENY (401) |
| `POST /api/v1/incidents/{id}/transition` (Triage) | ALLOW | ALLOW | ALLOW | DENY (403) | DENY (403) | DENY (401) |
| `POST /api/v1/incidents/{id}/transition` (Close) | ALLOW | ALLOW | DENY (403) | DENY (403) | DENY (403) | DENY (401) |
| `POST /api/v1/incidents/{id}/assignments` (Dispatch) | ALLOW | ALLOW | ALLOW | DENY (403) | DENY (403) | DENY (401) |
| `POST /api/v1/assignments/{id}/transition` (Self) | ALLOW | ALLOW | ALLOW | ALLOW (Owner) | DENY (403) | DENY (401) |
| `GET /api/v1/incidents` (Read) | ALLOW | ALLOW | ALLOW | ALLOW | ALLOW | DENY (401) |

---

## 4. Immutable Audit Trail

Every lifecycle action persists an immutable audit event:
1. `IncidentEventModel`: Captures `incident_id`, `event_type`, `from_status`, `to_status`, `actor_id`, `reason`, `payload`, and `created_at`.
2. `AuditLogModel`: Logs actor identity, client IP, action timestamp, and cryptographic event hashes.
3. `OutboxEventModel`: Publishes monotonic stream events for real-time WebSocket distribution to command dashboards.

---

## 5. Exit Gate Checklist

- [x] All 7x7 status transition pairs verified via `test_exhaustive_incident_state_machine_matrix`.
- [x] Concurrency protection and double-dispatch prevention verified via `test_concurrent_dispatch_race_rejection`.
- [x] Assignment lifecycle and automatic responder availability release verified via `test_assignment_full_lifecycle_and_reassignment`.
- [x] Server-side RBAC allow/deny matrix tested and passing for all roles.
- [x] Immutable audit trail completeness verified with monotonic event sequence.
- [x] 85/85 backend tests green.
