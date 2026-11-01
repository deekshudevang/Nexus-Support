# Evidence: Phase PG-P3 — Incident Operations Domain & State Machine

## Overview
Phase PG-P3 implements the core incident domain model, 3-state-machine validation, duplicate clustering, geographic proximity detection, and append-only audit event history.

## Verification Evidence

### 1. Incident State Machine
- **States**: `REPORTED`, `TRIAGED`, `DISPATCHED`, `ON_SCENE`, `RESOLVED`, `CLOSED`, `CANCELLED`.
- **Transitions Matrix**:
  - `REPORTED` $\to$ `TRIAGED`, `CANCELLED`
  - `TRIAGED` $\to$ `DISPATCHED`, `REPORTED`, `CANCELLED`
  - `DISPATCHED` $\to$ `ON_SCENE`, `TRIAGED`, `CANCELLED`
  - `ON_SCENE` $\to$ `RESOLVED`, `DISPATCHED`, `CANCELLED`
  - `RESOLVED` $\to$ `CLOSED`, `TRIAGED`
  - `CLOSED` & `CANCELLED`: Terminal states (immutable).
- **Validation**: Enforced via `app.services.incident_service.transition_incident`.
- **Conflict Handling**: Illegal transitions (e.g. `REPORTED` $\to$ `RESOLVED` or transitions out of `CANCELLED`) fail closed with HTTP 409 Conflict.

### 2. Authorization Boundaries
- `incident:triage`: Required for advancing status to `TRIAGED`, `DISPATCHED`, `ON_SCENE`, `RESOLVED`.
- `incident:close`: Reserved capability required for closing (`CLOSED`) or cancelling (`CANCELLED`) incidents.
- Viewers and field responders attempting unauthorized transitions are rejected with HTTP 403 Forbidden.

### 3. Truthful Coordinate Validation & Honest Missing GPS
- Database constraint: `ck_incidents_coordinates_valid` enforces either both `latitude` and `longitude` are null (truthful missing GPS indication) or both fall within $[-90, 90]$ and $[-180, 180]$.
- Out-of-bounds coordinates (e.g. $\text{lat} = 99.5$) are rejected at the API boundary with HTTP 400 Bad Request.

### 4. Proximity Duplicate Detection
- Haversine distance formula calculates Great Circle separation in meters.
- Incidents created within 500 meters and 1 hour in the same emergency category automatically generate a `DUPLICATE` link record in `incident_links` with confidence score.

### 5. Append-Only Incident Timeline
- Every creation, transition, and linking event writes to `incident_events` with actor attribution, previous/target status, reason notes, and timestamp.
- Available via `GET /api/v1/incidents/{incident_id}/timeline`.

### 6. Automated Test Pass
- Test command:
  ```powershell
  python -m pytest test_pg_p3_incidents.py
  ```
- Result: **7 passed** in 1.90s.
  - `test_thin_vertical_slice_sos_to_stream_and_broadcast`: PASSED
  - `test_incident_state_machine_allowed_transitions`: PASSED
  - `test_incident_state_machine_illegal_transitions_rejected`: PASSED
  - `test_transition_permission_boundaries`: PASSED
  - `test_incident_coordinates_and_missing_gps_handling`: PASSED
  - `test_duplicate_incident_detection_and_linking`: PASSED
  - `test_incident_timeline_event_history`: PASSED
