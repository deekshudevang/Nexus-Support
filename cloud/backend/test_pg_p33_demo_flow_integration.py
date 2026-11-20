"""Automated E2E Integration Test for the Operational Demo Flow (D1 Requirement).

Executes the complete operational lifecycle in a single test through the API:
1. SOS Ingestion (Mesh Handset -> Gateway)
2. Auto-Incident Creation & Verification
3. Dispatcher Assignment Creation
4. Downlink Retrieval by Authorized Device
5. Field Lifecycle Transitions (ACCEPTED -> EN_ROUTE -> ON_SCENE -> COMPLETED)
6. Responder Availability Release & Verification
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

def test_complete_demo_operational_flow_e2e(client, db):
    unique = uuid.uuid4().hex[:8]
    org_id = f"org-demo-{unique}"
    sos_uuid = f"sos-flow-{unique}"
    device_id = f"dev-phone-{unique}"
    responder_id = f"resp-sar-{unique}"

    gw_headers = {
        "X-Gateway-Key": "meshlink_gw_dev_secret",
        "X-Organization-Id": org_id,
        "X-Device-Id": device_id,
    }

    # ── STEP 1: SOS Ingestion ───────────────────────────────────────────────
    sos_payload = [
        {
            "uuid": sos_uuid,
            "senderId": device_id,
            "senderName": "Civilian Hiker Alpha",
            "emergencyType": "SEARCH",
            "severity": 8,
            "latitude": 37.7765,
            "longitude": -122.4172,
            "gpsFixAvailable": True,
            "message": "Trapped on steep ridge after trail collapse. Need extraction.",
            "timestamp": int(time.time()),
            "status": "ACTIVE",
            "hopCount": 2,
        }
    ]

    res_sos = client.post("/api/v1/sync/sos", headers=gw_headers, json=sos_payload)
    assert res_sos.status_code == 200, res_sos.text
    sos_body = res_sos.json()
    assert sos_body["status"] == "success"
    assert sos_body["synced_count"] == 1
    assert len(sos_body["receipts"]) == 1
    assert sos_body["receipts"][0]["uuid"] == sos_uuid
    assert sos_body["receipts"][0]["status"] == "ACKNOWLEDGED"

    # ── STEP 2: Verify Auto-Created Incident in Database ───────────────────
    db.expire_all()
    created_incident = db.query(IncidentModel).filter(
        IncidentModel.id == sos_uuid,
        IncidentModel.organization_id == org_id
    ).first()
    assert created_incident is not None
    assert created_incident.title == f"SOS: SEARCH from Civilian Hiker Alpha"
    assert created_incident.status == "REPORTED"
    assert created_incident.latitude == 37.7765
    assert created_incident.longitude == -122.4172
    assert created_incident.severity == 8

    # ── STEP 3: Setup Authorized Responder with Device ──────────────────────
    now = int(time.time())
    responder = ResponderModel(
        id=responder_id,
        callsign="SAR-EAGLE-1",
        responder_type="SAR",
        organization_id=org_id,
        device_id=device_id,
        availability="AVAILABLE",
        last_latitude=37.7750,
        last_longitude=-122.4180,
        created_at=now,
    )
    db.add(responder)
    db.commit()

    # ── STEP 4: Dispatcher Creates Assignment ───────────────────────────────
    asgn_id = f"asgn-flow-{unique}"
    assignment = AssignmentModel(
        id=asgn_id,
        incident_id=created_incident.id,
        responder_id=responder_id,
        status="DISPATCHED",
        organization_id=org_id,
        offered_at=now,
    )
    db.add(assignment)
    db.commit()

    # ── STEP 5: Mobile Handset Fetches Downlinks ────────────────────────────
    res_downlink = client.post("/api/v1/sync/sos", headers=gw_headers, json=[])
    assert res_downlink.status_code == 200
    downlinks = res_downlink.json()["downlinks"]
    assert len(downlinks) == 1
    dl = downlinks[0]
    assert dl["id"] == asgn_id
    assert dl["incident_id"] == created_incident.id
    assert dl["responder_id"] == responder_id
    assert dl["status"] == "DISPATCHED"
    assert dl["latitude"] == 37.7765
    assert dl["longitude"] == -122.4172

    # ── STEP 6: Lifecycle Transitions via Dedicated Endpoint ───────────────
    status_url = f"/api/v1/sync/assignments/{asgn_id}/status"

    # 6A. ACCEPT
    res_accept = client.post(status_url, headers=gw_headers, json={"status": "ACCEPTED"})
    assert res_accept.status_code == 200
    assert res_accept.json()["assignment_status"] == "ACCEPTED"
    assert res_accept.json()["responder_availability"] == "ASSIGNED"

    # 6B. EN_ROUTE
    res_enroute = client.post(status_url, headers=gw_headers, json={"status": "EN_ROUTE"})
    assert res_enroute.status_code == 200
    assert res_enroute.json()["assignment_status"] == "EN_ROUTE"
    assert res_enroute.json()["responder_availability"] == "ASSIGNED"

    # 6C. ON_SCENE
    res_scene = client.post(status_url, headers=gw_headers, json={"status": "ON_SCENE"})
    assert res_scene.status_code == 200
    assert res_scene.json()["assignment_status"] == "ON_SCENE"
    assert res_scene.json()["responder_availability"] == "ACTIVE"

    # 6D. COMPLETED
    res_complete = client.post(status_url, headers=gw_headers, json={"status": "COMPLETED"})
    assert res_complete.status_code == 200
    assert res_complete.json()["assignment_status"] == "COMPLETED"
    assert res_complete.json()["responder_availability"] == "AVAILABLE"

    # ── STEP 7: Final Database State Verification ───────────────────────────
    db.expire_all()
    final_asgn = db.query(AssignmentModel).filter(AssignmentModel.id == asgn_id).first()
    assert final_asgn.status == "COMPLETED"
    assert final_asgn.completed_at is not None
    assert final_asgn.responded_at is not None

    final_resp = db.query(ResponderModel).filter(ResponderModel.id == responder_id).first()
    assert final_resp.availability == "AVAILABLE"
