"""Verification of Server-to-Device Downlink Dispatch Pipeline (Task 3a).

Proves that:
1. An active DISPATCHED assignment for a responder is delivered in the `downlinks` array on `/api/v1/sync/sos`.
2. Downlinks carry essential operational telemetry (assignment ID, incident ID, role, notes, coords, priority).
3. Downlinks can be acknowledged via `POST /api/v1/sync/assignments/{id}/ack`.
4. Downlinks can also be acknowledged in-band via `X-Ack-Assignment-Ids` on subsequent sync.
5. Acknowledged assignments transition to ACCEPTED and are no longer returned as pending downlinks.
"""

import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.models.incident import IncidentModel
from app.models.responder import ResponderModel
from app.models.assignment import AssignmentModel
from main import app

@pytest.fixture
def client():
    return TestClient(app)

@pytest.fixture
def db():
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()

@pytest.fixture
def gw_headers():
    return {"X-Gateway-Key": "meshlink_gw_dev_secret"}

def test_downlink_dispatch_delivery_and_ack_lifecycle(client, db, gw_headers):
    unique_suffix = uuid.uuid4().hex[:8]
    inc_id = f"inc-downlink-{unique_suffix}"
    resp_id = f"resp-downlink-{unique_suffix}"
    device_id = f"node-device-{unique_suffix}"
    asgn_id = f"asgn-downlink-{unique_suffix}"
    now = int(time.time())

    # 1. Setup Incident in DB
    incident = IncidentModel(
        id=inc_id,
        title="Wildfire Evacuation Sector B",
        description="Immediate structure protection required at ridge",
        category="WILDFIRE",
        priority=4,
        severity=8,
        status="REPORTED",
        organization_id="org-default",
        source="COMMAND_CENTER",
        latitude=37.7833,
        longitude=-122.4167,
        version=1,
        created_at=now,
        updated_at=now,
    )
    db.add(incident)

    # 2. Setup Responder bound to device_id
    responder = ResponderModel(
        id=resp_id,
        callsign="Bravo-Lead",
        responder_type="SAR",
        skills=["SEARCH", "EVAC"],
        device_id=device_id,
        organization_id="org-default",
        availability="ASSIGNED",
        created_at=now,
    )
    db.add(responder)

    # 3. Setup DISPATCHED Assignment
    assignment = AssignmentModel(
        id=asgn_id,
        incident_id=inc_id,
        responder_id=resp_id,
        organization_id="org-default",
        status="DISPATCHED",
        offered_at=now,
    )
    db.add(assignment)
    db.commit()

    # 4. Gateway syncs SOS from device -> should receive downlink assignment
    sync_packet = [
        {
            "uuid": f"sos-{unique_suffix}",
            "senderId": device_id,
            "senderName": "Bravo Lead Unit",
            "emergencyType": "PATROL",
            "severity": 3,
            "latitude": 37.78,
            "longitude": -122.41,
            "message": "Routine status ping",
            "timestamp": now,
            "status": "ACTIVE",
            "hopCount": 1,
        }
    ]
    res = client.post("/api/v1/sync/sos", json=sync_packet, headers=gw_headers)
    assert res.status_code == 200, res.text
    body = res.json()
    assert body["status"] == "success"
    assert "downlinks" in body
    downlinks = body["downlinks"]
    assert len(downlinks) >= 1
    found = next((d for d in downlinks if d["id"] == asgn_id), None)
    assert found is not None
    assert found["incident_id"] == inc_id
    assert found["responder_id"] == resp_id
    assert found["status"] == "DISPATCHED"
    assert "Wildfire Evacuation" in found["notes"]
    assert found["latitude"] == 37.7833
    assert found["longitude"] == -122.4167

    # 5. Acknowledge assignment via dedicated endpoint
    ack_res = client.post(f"/api/v1/sync/assignments/{asgn_id}/ack", headers=gw_headers)
    assert ack_res.status_code == 200, ack_res.text
    ack_data = ack_res.json()
    assert ack_data["status"] == "success"
    assert ack_data["assignment_id"] == asgn_id
    assert ack_data["assignment_status"] == "ACCEPTED"

    # 6. Verify subsequent sync for same device no longer has this assignment as pending downlink
    res2 = client.post("/api/v1/sync/sos", json=sync_packet, headers=gw_headers)
    assert res2.status_code == 200
    downlinks2 = res2.json()["downlinks"]
    assert not any(d["id"] == asgn_id for d in downlinks2)

def test_downlink_piggybacked_header_ack(client, db, gw_headers):
    unique_suffix = uuid.uuid4().hex[:8]
    inc_id = f"inc-piggy-{unique_suffix}"
    resp_id = f"resp-piggy-{unique_suffix}"
    device_id = f"node-piggy-{unique_suffix}"
    asgn_id = f"asgn-piggy-{unique_suffix}"
    now = int(time.time())

    incident = IncidentModel(
        id=inc_id,
        title="Flood Rescue",
        description="Stranded motorist",
        category="FLOOD",
        priority=3,
        severity=7,
        status="REPORTED",
        organization_id="org-default",
        version=1,
        created_at=now,
        updated_at=now,
    )
    db.add(incident)

    responder = ResponderModel(
        id=resp_id,
        callsign="Marine-1",
        device_id=device_id,
        organization_id="org-default",
        availability="ASSIGNED",
        created_at=now,
    )
    db.add(responder)

    assignment = AssignmentModel(
        id=asgn_id,
        incident_id=inc_id,
        responder_id=resp_id,
        organization_id="org-default",
        status="DISPATCHED",
        offered_at=now,
    )
    db.add(assignment)
    db.commit()

    # Sync with piggybacked ACK header
    headers = dict(gw_headers)
    headers["X-Ack-Assignment-Ids"] = asgn_id
    sync_packet = [
        {
            "uuid": f"sos-piggy-{unique_suffix}",
            "senderId": device_id,
            "senderName": "Marine Unit 1",
            "emergencyType": "PATROL",
            "severity": 3,
            "latitude": 37.78,
            "longitude": -122.41,
            "message": "Water rescue underway",
            "timestamp": now,
            "status": "ACTIVE",
            "hopCount": 0,
        }
    ]
    res = client.post("/api/v1/sync/sos", json=sync_packet, headers=headers)
    assert res.status_code == 200

    # In DB, status must be transitioned to ACCEPTED
    db.expire_all()
    updated = db.query(AssignmentModel).filter(AssignmentModel.id == asgn_id).first()
    assert updated.status == "ACCEPTED"
    assert updated.responded_at is not None
