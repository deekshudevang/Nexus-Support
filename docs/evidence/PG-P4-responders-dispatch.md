# Evidence: Phase PG-P4 — Responders, Teams & Dispatch Operations

## Overview
Phase PG-P4 establishes the responder roster management, team organizational hierarchy, and concurrency-safe assignment workflow for tactical disaster dispatch.

## Verification Evidence

### 1. Responder & Team Operational Domain
- **Files**:
  - `cloud/backend/app/models/responder.py`
  - `cloud/backend/app/models/team.py`
  - `cloud/backend/app/models/team_member.py`
  - `cloud/backend/app/services/dispatch_service.py`
  - `cloud/backend/app/api/v1/dispatch.py`
- **Capabilities**:
  - Responders: Callsign, certified skills, responder type (`SAR`, `MEDICAL`, `K9_SEARCH`, etc.), availability state (`AVAILABLE`, `ASSIGNED`, `OFFLINE`).
  - Teams: Multi-responder units with unique names, callsigns, and leader designations.
  - Team Membership: Tracked via `team_members` with unique pair constraint `uq_team_members_team_responder`.

### 2. Concurrency-Safe Dispatch & Partial Unique Index Protection
- **Index**: `uq_assignments_active_responder` on `assignments(responder_id)`
  $$\text{WHERE } \text{status} \in (\text{'DISPATCHED'}, \text{'ACCEPTED'}, \text{'EN\_ROUTE'}, \text{'ON\_SCENE'})$$
- **Double-Dispatch Prevention**:
  - When an operator or dispatcher attempts to assign an already-engaged responder, the service layer rejects the call with HTTP 409 Conflict.
  - Concurrent simultaneous race conditions are prevented at the database engine level via the partial unique index.

### 3. Assignment Lifecycle & Availability Release
- **State Machine**:
  $$\text{DISPATCHED} \longrightarrow \text{ACCEPTED} \longrightarrow \text{EN\_ROUTE} \longrightarrow \text{ON\_SCENE} \longrightarrow \text{COMPLETED}$$
- **Automatic Release**:
  - Upon completion (`COMPLETED`), cancellation (`CANCELLED`), or rejection (`DECLINED`), the assignment is sealed with `completed_at` timestamp.
  - The responder's availability state immediately transitions from `ASSIGNED` back to `AVAILABLE`.
  - The responder can subsequently be assigned to future missions.

### 4. IDOR Protection
- A field responder attempting to accept, update, or transition an assignment belonging to another responder is rejected with HTTP 403 Forbidden.
- Dispatchers and Commanders retain system-wide dispatch control.

### 5. Automated Test Pass
- Test command:
  ```powershell
  python -m pytest test_pg_p4_dispatch.py
  ```
- Result: **4 passed** in 1.76s.
  - `test_team_and_responder_roster_management`: PASSED
  - `test_dispatch_and_double_dispatch_prevention`: PASSED
  - `test_assignment_full_workflow_and_availability_release`: PASSED
  - `test_assignment_idor_protection`: PASSED
