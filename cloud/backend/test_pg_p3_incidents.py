"""Unit and integration tests for Phase PG-Slice (Thin Vertical Slice) and Phase PG-P3 (Incident Domain)."""

import asyncio
import os
import sys
import time
import uuid
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.device_auth import sign_device_request
from app.core.rbac import create_access_token
from app.core.security import hash_password
from app.models.incident import IncidentModel
from app.models.incident_event import IncidentEventModel
from app.models.incident_link import IncidentLinkModel
from app.models.outbox_event import OutboxEventModel
from app.models.stream_event import StreamEventModel
from app.models.user import UserModel
from app.services import incident_service
from app.services.alert_manager import alert_manager
from app.services.outbox_publisher import process_outbox_batch
from main import app


@pytest.fixture
def test_client():
    return TestClient(app)


@pytest.fixture
def db_session():
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


@pytest.fixture
def commander_token(db_session):
    u_id = uuid.uuid4().hex[:8]
    user = UserModel(
        username=f"cmd_{u_id}",
        email=f"cmd_{u_id}@rescuemesh.org",
        password_hash=hash_password("CmdPass123!"),
        role="commander",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(user)
    db_session.commit()
    return create_access_token(user.id, user.username, user.role, user.token_version)


@pytest.fixture
def dispatcher_token(db_session):
    u_id = uuid.uuid4().hex[:8]
    user = UserModel(
        username=f"disp_{u_id}",
        email=f"disp_{u_id}@rescuemesh.org",
        password_hash=hash_password("DispPass123!"),
        role="dispatcher",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(user)
    db_session.commit()
    return create_access_token(user.id, user.username, user.role, user.token_version)


@pytest.fixture
def viewer_token(db_session):
    u_id = uuid.uuid4().hex[:8]
    user = UserModel(
        username=f"view_{u_id}",
        email=f"view_{u_id}@rescuemesh.org",
        password_hash=hash_password("ViewPass123!"),
        role="viewer",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(user)
    db_session.commit()
    return create_access_token(user.id, user.username, user.role, user.token_version)


# ── PG-SLC-01: Thin Vertical Slice ───────────────────────────────────────────

def test_thin_vertical_slice_sos_to_stream_and_broadcast(test_client, db_session, commander_token):
    """
    Verify complete vertical slice:
    Device/Operator SOS -> Incident created -> Outbox event emitted -> Stream seq assigned -> Event broadcast.
    """
    # 1. Mock WebSocket connection to alert manager
    received_messages = []

    class MockWebSocket:
        async def send_text(self, text: str):
            received_messages.append(text)

    mock_ws = MockWebSocket()

    async def connect_mock():
        await alert_manager.connect(mock_ws)

    asyncio.run(connect_mock())

    try:
        # 2. Create Incident via API
        resp = test_client.post(
            "/api/v1/incidents",
            headers={"Authorization": f"Bearer {commander_token}"},
            json={
                "title": "Flash Flood Trapped Residents",
                "category": "FLOOD",
                "priority": 1,
                "severity": 4,
                "description": "Rising water in basement, 2 adults 1 child",
                "source": "MESH_SOS",
                "latitude": 34.0522,
                "longitude": -118.2437,
                "location_description": "Sector 4 Riverbank",
            },
        )
        assert resp.status_code == 201
        inc_data = resp.json()
        inc_id = inc_data["id"]
        assert inc_data["status"] == "REPORTED"

        # 3. Verify Outbox has been processed into StreamEventModel with monotonic stream_seq
        stream_events = (
            db_session.query(StreamEventModel)
            .filter(StreamEventModel.aggregate_id == inc_id)
            .all()
        )
        assert len(stream_events) >= 1
        st_event = stream_events[0]
        assert st_event.stream_seq > 0
        assert st_event.event_type == "incident.created"
        assert st_event.payload["title"] == "Flash Flood Trapped Residents"

        # 4. Verify mock WebSocket client received broadcast
        assert len(received_messages) >= 1
        last_msg = received_messages[-1]
        assert "incident.created" in last_msg
        assert inc_id in last_msg
    finally:
        asyncio.run(alert_manager.disconnect(mock_ws))


# ── PG-P3-01: 3-State-Machine Incident Domain ────────────────────────────────

def test_incident_state_machine_allowed_transitions(test_client, db_session, commander_token):
    """Verify standard happy-path progression through state machine."""
    # Create incident
    resp = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Structure Fire",
            "category": "FIRE",
            "priority": 1,
            "severity": 5,
            "description": "Commercial building fire",
            "source": "RADIO",
        },
    )
    inc_id = resp.json()["id"]

    # REPORTED -> TRIAGED
    tr1 = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "TRIAGED", "notes": "Engine company 4 dispatched to investigate"},
    )
    assert tr1.status_code == 200
    assert tr1.json()["status"] == "TRIAGED"
    assert tr1.json()["version"] == 2

    # TRIAGED -> DISPATCHED
    tr2 = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "DISPATCHED", "notes": "Team En-Route"},
    )
    assert tr2.status_code == 200
    assert tr2.json()["status"] == "DISPATCHED"

    # DISPATCHED -> ON_SCENE
    tr3 = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "ON_SCENE", "notes": "First responders arrived at scene"},
    )
    assert tr3.status_code == 200
    assert tr3.json()["status"] == "ON_SCENE"

    # ON_SCENE -> RESOLVED
    tr4 = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "RESOLVED", "notes": "Fire extinguished, all occupants evacuated"},
    )
    assert tr4.status_code == 200
    assert tr4.json()["status"] == "RESOLVED"
    assert tr4.json()["resolved_at"] is not None

    # RESOLVED -> CLOSED
    tr5 = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "CLOSED", "notes": "After-action report filed"},
    )
    assert tr5.status_code == 200
    assert tr5.json()["status"] == "CLOSED"


def test_incident_state_machine_illegal_transitions_rejected(test_client, db_session, commander_token):
    """Verify illegal transitions are rejected with HTTP 409 Conflict."""
    resp = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Minor Sprain",
            "category": "MEDICAL",
            "priority": 4,
            "severity": 2,
            "description": "Hiker sprained ankle",
            "source": "FIELD_APP",
        },
    )
    inc_id = resp.json()["id"]

    # REPORTED -> RESOLVED is illegal (must be triaged and handled)
    bad_resp = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "RESOLVED"},
    )
    assert bad_resp.status_code == 409
    assert "Illegal state transition" in bad_resp.json()["detail"]

    # Close incident properly
    test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "CANCELLED"},
    )

    # Terminal state rejection: cannot transition from CANCELLED to DISPATCHED
    terminal_resp = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "DISPATCHED"},
    )
    assert terminal_resp.status_code == 409
    assert "Illegal state transition" in terminal_resp.json()["detail"]


def test_transition_permission_boundaries(test_client, db_session, dispatcher_token, viewer_token):
    """Verify role authorization boundaries on incident state transitions."""
    # Dispatcher creates incident
    resp = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={
            "title": "Wildfire Warning",
            "category": "FIRE",
            "priority": 2,
            "severity": 4,
            "description": "Brush fire spotted near ridge",
            "source": "LOOKOUT",
        },
    )
    inc_id = resp.json()["id"]

    # Viewer CANNOT triage or transition
    view_attempt = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {viewer_token}"},
        json={"new_status": "TRIAGED"},
    )
    assert view_attempt.status_code == 403

    # Dispatcher CAN triage (has INCIDENT_TRIAGE)
    disp_tr = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"new_status": "TRIAGED"},
    )
    assert disp_tr.status_code == 200

    # Dispatcher CANNOT close incident (requires INCIDENT_CLOSE, reserved for Commander/Admin)
    disp_close = test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"new_status": "CLOSED"},
    )
    assert disp_close.status_code == 403
    assert "incident:close" in disp_close.json()["detail"]


# ── PG-P3-02: Idempotency, Coordinates & Duplicate Clustering ────────────────

def test_incident_coordinates_and_missing_gps_handling(test_client, db_session, commander_token):
    """Verify honest missing GPS (null coordinates) and validation bounds."""
    # 1. Missing GPS: explicit null coordinates allowed
    resp_null = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Lost Hiker Without GPS Fix",
            "category": "SEARCH_RESCUE",
            "priority": 2,
            "severity": 3,
            "description": "GPS unavailable due to dense canyon canopy",
            "source": "VOICE_RADIO",
            "latitude": None,
            "longitude": None,
            "location_description": "Near Bear Creek Trailhead",
        },
    )
    assert resp_null.status_code == 201
    assert resp_null.json()["latitude"] is None
    assert resp_null.json()["longitude"] is None

    # 2. Out of bounds coordinates rejected
    resp_invalid = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Invalid Coordinate Test",
            "category": "OTHER",
            "priority": 3,
            "severity": 1,
            "description": "Bad coordinates",
            "source": "TEST",
            "latitude": 99.5,  # > 90
            "longitude": 12.0,
        },
    )
    assert resp_invalid.status_code == 400
    assert "out of bounds" in resp_invalid.json()["detail"].lower()


def test_duplicate_incident_detection_and_linking(test_client, db_session, commander_token):
    """Verify automatic clustering of duplicate incidents within 500m proximity."""
    # Primary incident at coordinates (37.7749, -122.4194)
    r1 = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Vehicle Collision 1",
            "category": "TRAUMA",
            "priority": 2,
            "severity": 4,
            "description": "Two car rollover on Highway 101",
            "source": "PASSERBY_1",
            "latitude": 37.7749,
            "longitude": -122.4194,
        },
    )
    inc1_id = r1.json()["id"]

    # Secondary incident reported 50 meters away: (37.7753, -122.4190) in same category
    r2 = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Car Accident Near Exit 5",
            "category": "TRAUMA",
            "priority": 2,
            "severity": 4,
            "description": "Heavy crash with smoke",
            "source": "PASSERBY_2",
            "latitude": 37.7753,
            "longitude": -122.4190,
        },
    )
    inc2_id = r2.json()["id"]

    # Inspect incident 2 details
    detail_resp = test_client.get(
        f"/api/v1/incidents/{inc2_id}",
        headers={"Authorization": f"Bearer {commander_token}"},
    )
    assert detail_resp.status_code == 200
    links = detail_resp.json()["links"]
    assert len(links) >= 1
    linked_target_ids = {l["target_incident_id"] for l in links}
    assert inc1_id in linked_target_ids
    assert any(l["link_type"] == "DUPLICATE" for l in links)


def test_incident_timeline_event_history(test_client, db_session, commander_token):
    """Verify append-only event stream tracks creation, triage, and manual links."""
    r = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "title": "Power Line Down",
            "category": "HAZARD",
            "priority": 3,
            "severity": 3,
            "description": "Live wire sparking on roadway",
            "source": "PATROL",
        },
    )
    inc_id = r.json()["id"]

    # Transition
    test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "TRIAGED", "notes": "Utility company notified"},
    )

    # Timeline fetch
    tl_resp = test_client.get(
        f"/api/v1/incidents/{inc_id}/timeline",
        headers={"Authorization": f"Bearer {commander_token}"},
    )
    assert tl_resp.status_code == 200
    events = tl_resp.json()
    assert len(events) >= 2
    types = [e["event_type"] for e in events]
    assert "CREATED" in types
    assert "STATUS_CHANGED" in types
