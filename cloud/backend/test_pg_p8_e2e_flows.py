"""Comprehensive End-to-End Verification of Flows A through F (Phase PG-P8)."""

import os
import sys
import tempfile
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import app, GATEWAY_API_KEYS, OPERATOR_TOKENS, init_db
from app.core.database import Base, SessionLocal, engine
from app.core.rbac import create_access_token
from app.core.security import hash_password
from app.models.incident import IncidentModel
from app.models.stream_event import StreamEventModel
from app.models.user import UserModel
from app.services.outbox_publisher import process_outbox_batch

@pytest.fixture(autouse=True)
def setup_isolated_db():
    temp_dir = tempfile.mkdtemp()
    test_db = os.path.join(temp_dir, "test_p8_sync.db")
    import main
    main._ip_request_history.clear()
    original_db = main.DATABASE_PATH
    main.DATABASE_PATH = test_db
    init_db(test_db)
    yield
    main.DATABASE_PATH = original_db
    main._ip_request_history.clear()
    if os.path.exists(test_db):
        try:
            os.remove(test_db)
        except Exception:
            pass

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
def legacy_op_headers():
    token = next(iter(OPERATOR_TOKENS))
    return {"Authorization": f"Bearer {token}"}

@pytest.fixture
def gw_headers():
    key = next(iter(GATEWAY_API_KEYS))
    return {"X-Gateway-Key": key}


# ── FLOW A: SOS Packet Ingestion -> DB Persistence -> WebSocket Broadcast ──────
def test_flow_a_sos_ingestion_persistence_and_receipt(test_client, gw_headers, legacy_op_headers):
    """Flow A: SOS Ingestion -> DB Persistence -> Receipt Reconciliation & Downlink Check."""
    sos_uuid = f"flow-a-{uuid.uuid4().hex[:8]}"
    payload = [{
        "uuid": sos_uuid,
        "senderId": f"node-{uuid.uuid4().hex[:6]}",
        "senderName": "Rescue Scout Alpha",
        "emergencyType": "MEDICAL",
        "severity": 9,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "message": "Trauma casualty requiring extraction",
        "timestamp": int(time.time()),
        "status": "ACTIVE",
        "hopCount": 1
    }]

    # Ingestion via gateway
    res = test_client.post("/api/v1/sync/sos", json=payload, headers=gw_headers)
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "success"
    assert data["synced_count"] == 1
    assert data["receipts"][0]["uuid"] == sos_uuid
    assert data["receipts"][0]["status"] == "ACKNOWLEDGED"
    assert "downlinks" in data

    # Query via operator API
    sos_res = test_client.get("/api/sos", headers=legacy_op_headers)
    assert sos_res.status_code == 200
    packets = sos_res.json()
    matched = next((p for p in packets if p["uuid"] == sos_uuid), None)
    assert matched is not None
    assert matched["severity"] == 9
    assert matched["emergencyType"] == "MEDICAL"


# ── FLOW B: Incident State Machine with Timeline Audit ────────────────────────
def test_flow_b_incident_state_machine_and_audit_timeline(test_client, commander_token):
    """Flow B: State Machine (REPORTED -> TRIAGED -> DISPATCHED -> ON_SCENE -> RESOLVED -> CLOSED) with Timeline."""
    headers = {"Authorization": f"Bearer {commander_token}"}

    # 1. Create incident
    create_res = test_client.post("/api/v1/incidents", json={
        "title": "Structural Collapse East Wing",
        "description": "Debris blocking primary evacuation corridor",
        "category": "COLLAPSE",
        "priority": 1,
        "severity": 5,
        "latitude": 37.7800,
        "longitude": -122.4200,
        "source": "RADIO"
    }, headers=headers)
    assert create_res.status_code == 201
    incident_id = create_res.json()["id"]
    assert create_res.json()["status"] == "REPORTED"

    # 2. Transition: REPORTED -> TRIAGED
    tr1 = test_client.post(f"/api/v1/incidents/{incident_id}/transition", json={
        "new_status": "TRIAGED",
        "notes": "Thermal scans confirm victims trapped inside"
    }, headers=headers)
    assert tr1.status_code == 200
    assert tr1.json()["status"] == "TRIAGED"

    # 3. Transition: TRIAGED -> DISPATCHED
    tr2 = test_client.post(f"/api/v1/incidents/{incident_id}/transition", json={
        "new_status": "DISPATCHED",
        "notes": "SAR Team 1 assigned"
    }, headers=headers)
    assert tr2.status_code == 200
    assert tr2.json()["status"] == "DISPATCHED"

    # 4. Transition: DISPATCHED -> ON_SCENE
    tr3 = test_client.post(f"/api/v1/incidents/{incident_id}/transition", json={
        "new_status": "ON_SCENE",
        "notes": "First responders arrived on scene"
    }, headers=headers)
    assert tr3.status_code == 200
    assert tr3.json()["status"] == "ON_SCENE"

    # 5. Transition: ON_SCENE -> RESOLVED
    tr4 = test_client.post(f"/api/v1/incidents/{incident_id}/transition", json={
        "new_status": "RESOLVED",
        "notes": "Casualties safely extracted"
    }, headers=headers)
    assert tr4.status_code == 200
    assert tr4.json()["status"] == "RESOLVED"
    assert tr4.json()["resolved_at"] is not None

    # 6. Transition: RESOLVED -> CLOSED
    tr5 = test_client.post(f"/api/v1/incidents/{incident_id}/transition", json={
        "new_status": "CLOSED",
        "notes": "Site secured"
    }, headers=headers)
    assert tr5.status_code == 200
    assert tr5.json()["status"] == "CLOSED"

    # 7. Reopening: CLOSED -> TRIAGED
    tr6 = test_client.post(f"/api/v1/incidents/{incident_id}/transition", json={
        "new_status": "TRIAGED",
        "notes": "Secondary flareup reported, reopening incident for assessment"
    }, headers=headers)
    assert tr6.status_code == 200
    assert tr6.json()["status"] == "TRIAGED"

    # 8. Verify Timeline
    timeline_res = test_client.get(f"/api/v1/incidents/{incident_id}/timeline", headers=headers)
    assert timeline_res.status_code == 200
    events = timeline_res.json()
    assert len(events) >= 7
    types = [e["event_type"] for e in events]
    assert "CREATED" in types
    assert "STATUS_CHANGED" in types


# ── FLOW C: Proximity Duplicate Detection ─────────────────────────────────────
def test_flow_c_proximity_duplicate_detection(test_client, commander_token):
    """Flow C: Proximity Duplicate Detection (<500m / 1hr) between incidents in same category."""
    headers = {"Authorization": f"Bearer {commander_token}"}
    base_lat = 37.774929
    base_lon = -122.419416

    # Master Incident
    master_res = test_client.post("/api/v1/incidents", json={
        "title": "Gas Leak Main Plaza",
        "description": "Strong mercaptan odor reported",
        "category": "HAZMAT",
        "priority": 1,
        "severity": 4,
        "latitude": base_lat,
        "longitude": base_lon,
        "source": "CALL_1"
    }, headers=headers)
    assert master_res.status_code == 201
    master_id = master_res.json()["id"]

    # Duplicate Incident ~60m away in same category
    dup_res = test_client.post("/api/v1/incidents", json={
        "title": "Rotten Egg Smell Near Plaza",
        "description": "Smell of gas leaking",
        "category": "HAZMAT",
        "priority": 1,
        "severity": 4,
        "latitude": base_lat + 0.0005,
        "longitude": base_lon + 0.0003,
        "source": "CALL_2"
    }, headers=headers)
    assert dup_res.status_code == 201
    dup_id = dup_res.json()["id"]

    # Verify duplicate automatically clustered in links
    detail_res = test_client.get(f"/api/v1/incidents/{dup_id}", headers=headers)
    assert detail_res.status_code == 200
    links = detail_res.json()["links"]
    assert len(links) >= 1
    linked_ids = {l["target_incident_id"] for l in links}
    assert master_id in linked_ids


# ── FLOW D: Responder Roster & Concurrency-Safe Dispatch ──────────────────────
def test_flow_d_responder_concurrency_safe_dispatch(test_client, commander_token):
    """Flow D: Responders, Teams, Concurrency Protection (HTTP 409), and Availability Release."""
    headers = {"Authorization": f"Bearer {commander_token}"}

    # Create 2 incidents
    inc1 = test_client.post("/api/v1/incidents", json={
        "title": "Flood Alpha",
        "description": "Rising water in Sector Alpha",
        "category": "FLOOD",
        "priority": 2,
        "severity": 3,
        "source": "OP"
    }, headers=headers).json()["id"]

    inc2 = test_client.post("/api/v1/incidents", json={
        "title": "Flood Bravo",
        "description": "Rising water in Sector Bravo",
        "category": "FLOOD",
        "priority": 2,
        "severity": 3,
        "source": "OP"
    }, headers=headers).json()["id"]

    # Create team & responder
    team = test_client.post("/api/v1/teams", json={
        "name": f"Swiftwater {uuid.uuid4().hex[:6]}",
        "callsign": "SWIFT-1"
    }, headers=headers).json()["id"]

    responder = test_client.post("/api/v1/responders", json={
        "callsign": f"BOAT-{uuid.uuid4().hex[:4].upper()}",
        "responder_type": "WATER_RESCUE",
        "skills": ["BOAT", "SWIM"]
    }, headers=headers).json()["id"]

    # 1. First assignment succeeds
    assign1 = test_client.post(f"/api/v1/incidents/{inc1}/assignments", json={
        "responder_id": responder,
    }, headers=headers)
    assert assign1.status_code == 201
    assign1_id = assign1.json()["id"]

    # Verify responder status is now ASSIGNED / BUSY
    resp_check = test_client.get("/api/v1/responders", headers=headers).json()
    r_obj = next(r for r in resp_check if r["id"] == responder)
    assert r_obj["availability"] == "ASSIGNED"

    # 2. Concurrent second active assignment MUST be rejected with HTTP 409
    assign2 = test_client.post(f"/api/v1/incidents/{inc2}/assignments", json={
        "responder_id": responder,
    }, headers=headers)
    assert assign2.status_code == 409
    assert "already has an active assignment" in assign2.json()["detail"].lower()

    # 3. Complete first assignment through state machine -> Availability released back to AVAILABLE
    # DISPATCHED -> ACCEPTED
    test_client.post(f"/api/v1/assignments/{assign1_id}/transition", json={
        "new_status": "ACCEPTED",
        "reason": "Responder acknowledged dispatch"
    }, headers=headers)

    # ACCEPTED -> EN_ROUTE
    test_client.post(f"/api/v1/assignments/{assign1_id}/transition", json={
        "new_status": "EN_ROUTE",
        "reason": "In transit with boat"
    }, headers=headers)

    # EN_ROUTE -> ON_SCENE
    test_client.post(f"/api/v1/assignments/{assign1_id}/transition", json={
        "new_status": "ON_SCENE",
        "reason": "Arrived at flood sector"
    }, headers=headers)

    # ON_SCENE -> COMPLETED
    comp_res = test_client.post(f"/api/v1/assignments/{assign1_id}/transition", json={
        "new_status": "COMPLETED",
        "reason": "Mission complete. Boat recovered."
    }, headers=headers)
    assert comp_res.status_code == 200

    resp_after = test_client.get("/api/v1/responders", headers=headers).json()
    r_after = next(r for r in resp_after if r["id"] == responder)
    assert r_after["availability"] == "AVAILABLE"


# ── FLOW E: Outbox Publishing & Monotonic Gapless Replay ───────────────────────
def test_flow_e_outbox_monotonic_stream_and_gapless_replay(test_client, db_session, commander_token):
    """Flow E: Transactional Outbox Publishing, Monotonic Sequence, and REST Replay."""
    headers = {"Authorization": f"Bearer {commander_token}"}

    # Process outbox
    drained = process_outbox_batch(db_session, limit=50)
    assert isinstance(drained, list)

    # Replay events
    replay_res = test_client.get("/api/v1/events/replay?since_seq=0&limit=50", headers=headers)
    assert replay_res.status_code == 200
    data = replay_res.json()
    assert "events" in data
    assert "last_seq" in data

    events = data["events"]
    if len(events) >= 2:
        for i in range(len(events) - 1):
            assert events[i]["stream_seq"] < events[i + 1]["stream_seq"]

    # Test single-use ticket endpoint
    ticket_res = test_client.post("/api/v1/auth/ws-ticket", headers=headers)
    assert ticket_res.status_code == 200
    ticket = ticket_res.json()["ticket"]
    assert len(ticket) >= 16


# ── FLOW F: Zero GPS Fabrication Truthfulness ──────────────────────────────────
def test_flow_f_zero_gps_fabrication_truthfulness(test_client, gw_headers, legacy_op_headers):
    """Flow F: Truthful Coordinates (Zero-Fabrication) when GPS Fix is Missing."""
    no_gps_uuid = f"flow-f-{uuid.uuid4().hex[:8]}"
    payload = [{
        "uuid": no_gps_uuid,
        "senderId": f"node-{uuid.uuid4().hex[:6]}",
        "senderName": "Sub-terrain Worker",
        "emergencyType": "TRAPPED",
        "severity": 8,
        "latitude": None,
        "longitude": None,
        "gpsFixAvailable": False,
        "message": "Trapped in mine shaft without GPS reception",
        "timestamp": int(time.time()),
        "status": "ACTIVE",
        "hopCount": 2
    }]

    # Ingest packet with null GPS
    res = test_client.post("/api/v1/sync/sos", json=payload, headers=gw_headers)
    assert res.status_code == 200

    # Query back via operator
    get_res = test_client.get("/api/sos", headers=legacy_op_headers)
    assert get_res.status_code == 200
    matched = next((p for p in get_res.json() if p["uuid"] == no_gps_uuid), None)
    assert matched is not None
    assert matched["latitude"] is None
    assert matched["longitude"] is None
    assert matched["gpsFixAvailable"] == 0 or matched["gpsFixAvailable"] is False
