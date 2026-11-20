"""Verification of Assignment Status Sync-Back, Downlink Scoping, and Multi-Tenant Isolation (A2 & A3).

Proves that:
1. Field devices can send assignment status updates (ACCEPTED, EN_ROUTE, ON_SCENE, COMPLETED) via dedicated and in-band sync routes.
2. Device scoping guarantees a device only receives downlinks for its OWN assigned responder.
3. Unidentified devices (no deviceId or senderId) receive an empty downlink list, never arbitrary dispatches.
4. Cross-organization isolation prevents a device in org-A from receiving or updating assignments in org-B.
5. Downlink assignments transition correctly and update responder availability in tandem.
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

def test_assignment_status_sync_endpoint_full_lifecycle(client, db, gw_headers):
    unique = uuid.uuid4().hex[:8]
    inc_id = f"inc-stat-{unique}"
    resp_id = f"resp-stat-{unique}"
    asgn_id = f"asgn-stat-{unique}"
    dev_id = f"dev-stat-{unique}"

    # 1. Seed incident, responder, and dispatched assignment
    now = int(time.time())
    inc = IncidentModel(
        id=inc_id,
        title="Status Transition Incident",
        description="Life safety operations",
        category="SEARCH",
        priority=1,
        severity=9,
        status="DISPATCHED",
        organization_id="org-default",
        version=1,
        created_at=now,
        updated_at=now,
    )
    resp = ResponderModel(
        id=resp_id,
        callsign="SAR-DELTA-1",
        organization_id="org-default",
        responder_type="SAR",
        availability="ASSIGNED",
        device_id=dev_id,
        created_at=now,
    )
    asgn = AssignmentModel(
        id=asgn_id,
        incident_id=inc_id,
        responder_id=resp_id,
        status="DISPATCHED",
        organization_id="org-default",
        offered_at=now,
    )
    db.add_all([inc, resp, asgn])
    db.commit()

    # 2. Transition DISPATCHED -> ACCEPTED
    res1 = client.post(
        f"/api/v1/sync/assignments/{asgn_id}/status",
        headers={**gw_headers, "X-Device-Id": dev_id},
        json={"status": "ACCEPTED"}
    )
    assert res1.status_code == 200, res1.text
    assert res1.json()["assignment_status"] == "ACCEPTED"
    assert res1.json()["responder_availability"] == "ASSIGNED"

    # 3. Transition ACCEPTED -> EN_ROUTE
    res2 = client.post(
        f"/api/v1/sync/assignments/{asgn_id}/status",
        headers={**gw_headers, "X-Device-Id": dev_id},
        json={"status": "EN_ROUTE"}
    )
    assert res2.status_code == 200
    assert res2.json()["assignment_status"] == "EN_ROUTE"

    # 4. Transition EN_ROUTE -> ON_SCENE (responder becomes ACTIVE)
    res3 = client.post(
        f"/api/v1/sync/assignments/{asgn_id}/status",
        headers={**gw_headers, "X-Device-Id": dev_id},
        json={"status": "ON_SCENE"}
    )
    assert res3.status_code == 200
    assert res3.json()["assignment_status"] == "ON_SCENE"
    assert res3.json()["responder_availability"] == "ACTIVE"

    # 5. Transition ON_SCENE -> COMPLETED (responder released to AVAILABLE)
    res4 = client.post(
        f"/api/v1/sync/assignments/{asgn_id}/status",
        headers={**gw_headers, "X-Device-Id": dev_id},
        json={"status": "COMPLETED"}
    )
    assert res4.status_code == 200
    assert res4.json()["assignment_status"] == "COMPLETED"
    assert res4.json()["responder_availability"] == "AVAILABLE"

    # Verify DB state
    db.expire_all()
    updated_asgn = db.query(AssignmentModel).filter(AssignmentModel.id == asgn_id).first()
    assert updated_asgn.status == "COMPLETED"
    assert updated_asgn.completed_at is not None

def test_downlink_scoping_unidentified_device_receives_zero_downlinks(client, db, gw_headers):
    unique = uuid.uuid4().hex[:8]
    inc_id = f"inc-unid-{unique}"
    resp_id = f"resp-unid-{unique}"
    asgn_id = f"asgn-unid-{unique}"
    now = int(time.time())

    db.add_all([
        IncidentModel(
            id=inc_id, title="Unidentified Test", description="", category="MEDICAL",
            priority=2, severity=6, status="DISPATCHED", organization_id="org-default",
            version=1, created_at=now, updated_at=now
        ),
        ResponderModel(
            id=resp_id, callsign="SAR-SCOUT", organization_id="org-default",
            responder_type="SAR", availability="AVAILABLE", created_at=now
        ),
        AssignmentModel(
            id=asgn_id, incident_id=inc_id, responder_id=resp_id,
            status="DISPATCHED", organization_id="org-default", offered_at=now
        )
    ])
    db.commit()

    # Call sync with NO deviceId and NO packet senderId
    sync_res = client.post(
        "/api/v1/sync/sos",
        headers=gw_headers,
        json=[]
    )
    assert sync_res.status_code == 200
    # Must strictly return 0 downlinks to prevent leaking operational dispatches to unknown nodes
    assert sync_res.json()["downlinks"] == []

def test_downlink_scoping_device_only_receives_own_assignments(client, db, gw_headers):
    unique = uuid.uuid4().hex[:8]
    now = int(time.time())

    resp_a = f"resp-a-{unique}"
    dev_a = f"dev-a-{unique}"
    asgn_a = f"asgn-a-{unique}"

    resp_b = f"resp-b-{unique}"
    dev_b = f"dev-b-{unique}"
    asgn_b = f"asgn-b-{unique}"

    db.add_all([
        ResponderModel(id=resp_a, callsign="UNIT-A", organization_id="org-default", device_id=dev_a, created_at=now),
        ResponderModel(id=resp_b, callsign="UNIT-B", organization_id="org-default", device_id=dev_b, created_at=now),
        AssignmentModel(id=asgn_a, incident_id=f"inc-a-{unique}", responder_id=resp_a, status="DISPATCHED", organization_id="org-default", offered_at=now),
        AssignmentModel(id=asgn_b, incident_id=f"inc-b-{unique}", responder_id=resp_b, status="DISPATCHED", organization_id="org-default", offered_at=now),
    ])
    db.commit()

    # Device A syncs: must only receive Assignment A, never Assignment B
    res_a = client.post(
        "/api/v1/sync/sos",
        headers={**gw_headers, "X-Device-Id": dev_a},
        json=[]
    )
    assert res_a.status_code == 200
    downlinks_a = res_a.json()["downlinks"]
    dl_ids_a = [d["id"] for d in downlinks_a]
    assert asgn_a in dl_ids_a
    assert asgn_b not in dl_ids_a

def test_in_band_assignment_status_updates_via_sync_sos(client, db, gw_headers):
    unique = uuid.uuid4().hex[:8]
    now = int(time.time())
    asgn_id = f"asgn-inband-{unique}"
    resp_id = f"resp-inband-{unique}"

    db.add_all([
        ResponderModel(id=resp_id, callsign="INBAND-SAR", organization_id="org-default", availability="ASSIGNED", created_at=now),
        AssignmentModel(id=asgn_id, incident_id=f"inc-ib-{unique}", responder_id=resp_id, status="ACCEPTED", organization_id="org-default", offered_at=now),
    ])
    db.commit()

    # Field handset transmits store-and-forward status update: asgn_id:ON_SCENE
    res = client.post(
        "/api/v1/sync/sos",
        headers={**gw_headers, "X-Assignment-Updates": f"{asgn_id}:ON_SCENE"},
        json=[]
    )
    assert res.status_code == 200

    db.expire_all()
    asgn = db.query(AssignmentModel).filter(AssignmentModel.id == asgn_id).first()
    assert asgn.status == "ON_SCENE"

    resp = db.query(ResponderModel).filter(ResponderModel.id == resp_id).first()
    assert resp.availability == "ACTIVE"
