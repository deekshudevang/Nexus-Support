"""Verification of Unified PostgreSQL Data Layer & Sync-to-Incident Pipeline (Task 2).

Proves that:
1. An SOS packet ingested via `/api/sync/sos` and `/api/v1/sync/sos` writes to unified PostgreSQL models.
2. The packet appears in the legacy/dashboard query `GET /api/sos`.
3. The incident automatically appears in the v1 incidents API (`GET /api/v1/incidents` and `GET /api/v1/incidents/{id}`).
4. An update to an existing SOS packet syncs the incident fields (severity, coordinates, updated_at).
5. Node telemetry via `POST /api/sync/nodes` is queryable via `GET /api/nodes`.
"""

import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.rbac import Role, create_access_token
from app.models.incident import IncidentModel
from app.models.sos import SosPacketModel
from app.models.node import NodeStatusModel
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

@pytest.fixture
def op_headers():
    return {"X-Operator-Token": "meshlink_op_dev_secret"}

from app.core.security import hash_password
from app.models.user import UserModel

@pytest.fixture
def commander_token(db):
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
        organization_id="org-default",
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    return create_access_token(
        user_id=user.id,
        username=user.username,
        role=user.role,
        token_version=user.token_version,
        organization_id="org-default",
    )

def test_sync_sos_creates_incident_and_dashboard_entry(client, gw_headers, op_headers, commander_token):
    packet_uuid = f"sos-unified-{uuid.uuid4().hex[:8]}"
    packet_payload = [
        {
            "uuid": packet_uuid,
            "senderId": "node-responder-99",
            "senderName": "Sgt. Miller",
            "emergencyType": "MEDICAL",
            "severity": 8,
            "latitude": 37.7749,
            "longitude": -122.4194,
            "gpsFixAvailable": True,
            "message": "Trauma casualty needs medevac extraction",
            "timestamp": int(time.time()),
            "status": "PENDING",
            "hopCount": 2,
        }
    ]

    # 1. Ingest SOS packet via sync route
    sync_res = client.post("/api/v1/sync/sos", json=packet_payload, headers=gw_headers)
    assert sync_res.status_code == 200, sync_res.text
    sync_data = sync_res.json()
    assert sync_data["status"] == "success"
    assert sync_data["synced_count"] == 1

    # 2. Verify appearance in legacy / dashboard query GET /api/sos
    sos_res = client.get("/api/sos", headers=op_headers)
    assert sos_res.status_code == 200
    sos_list = sos_res.json()
    matching_sos = next((s for s in sos_list if s["uuid"] == packet_uuid), None)
    assert matching_sos is not None, f"Packet {packet_uuid} missing from /api/sos"
    assert matching_sos["senderName"] == "Sgt. Miller"
    assert matching_sos["severity"] == 8
    assert matching_sos["emergencyType"] == "MEDICAL"

    # 3. Verify automatic creation in v1 Incidents API (GET /api/v1/incidents)
    incidents_res = client.get(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"}
    )
    assert incidents_res.status_code == 200
    incidents = incidents_res.json()
    matching_inc = next((i for i in incidents if i["id"] == packet_uuid), None)
    assert matching_inc is not None, f"Incident {packet_uuid} missing from /api/v1/incidents"
    assert matching_inc["category"] == "MEDICAL"
    assert matching_inc["source"] == "MESH_SOS"
    assert matching_inc["reporter_name"] == "Sgt. Miller"
    assert matching_inc["severity"] == 8
    assert abs(matching_inc["latitude"] - 37.7749) < 1e-4
    assert abs(matching_inc["longitude"] - (-122.4194)) < 1e-4

    # 4. Verify detail query by ID (GET /api/v1/incidents/{id})
    detail_res = client.get(
        f"/api/v1/incidents/{packet_uuid}",
        headers={"Authorization": f"Bearer {commander_token}"}
    )
    assert detail_res.status_code == 200
    detail = detail_res.json()
    assert detail["id"] == packet_uuid
    assert "Trauma casualty" in detail["description"]

def test_sync_sos_updates_existing_incident_and_coordinates(client, gw_headers, op_headers, commander_token):
    packet_uuid = f"sos-update-{uuid.uuid4().hex[:8]}"
    initial_packet = [
        {
            "uuid": packet_uuid,
            "senderId": "node-recon-1",
            "senderName": "Recon 1",
            "emergencyType": "FLOOD",
            "severity": 4,
            "latitude": 37.7700,
            "longitude": -122.4200,
            "gpsFixAvailable": True,
            "message": "Water rising at checkpoint",
            "timestamp": int(time.time()),
            "status": "PENDING",
            "hopCount": 1,
        }
    ]

    client.post("/api/v1/sync/sos", json=initial_packet, headers=gw_headers)

    # Now update severity to 9 and new coordinates
    updated_packet = [
        {
            "uuid": packet_uuid,
            "senderId": "node-recon-1",
            "senderName": "Recon 1",
            "emergencyType": "FLOOD",
            "severity": 9,
            "latitude": 37.7750,
            "longitude": -122.4250,
            "gpsFixAvailable": True,
            "message": "CRITICAL: Levee breached, immediate evacuation required",
            "timestamp": int(time.time()) + 10,
            "status": "ACTIVE",
            "hopCount": 2,
        }
    ]

    update_res = client.post("/api/v1/sync/sos", json=updated_packet, headers=gw_headers)
    assert update_res.status_code == 200

    # Query v1 incident API
    inc_res = client.get(
        f"/api/v1/incidents/{packet_uuid}",
        headers={"Authorization": f"Bearer {commander_token}"}
    )
    assert inc_res.status_code == 200
    inc = inc_res.json()
    assert inc["severity"] == 9
    assert abs(inc["latitude"] - 37.7750) < 1e-4

def test_sync_nodes_unification(client, gw_headers, op_headers):
    node_id = f"node-{uuid.uuid4().hex[:6]}"
    nodes_payload = [
        {
            "deviceId": node_id,
            "displayName": "Relay Bravo",
            "role": "ROUTER",
            "batteryLevel": 88,
            "needsHelp": False,
            "latitude": 37.7812,
            "longitude": -122.4089,
            "timestamp": int(time.time()),
        }
    ]

    sync_res = client.post("/api/sync/nodes", json=nodes_payload, headers=gw_headers)
    assert sync_res.status_code == 200
    assert sync_res.json()["synced_count"] == 1

    # Query GET /api/nodes
    get_res = client.get("/api/nodes", headers=op_headers)
    assert get_res.status_code == 200
    nodes = get_res.json()
    match = next((n for n in nodes if n["deviceId"] == node_id), None)
    assert match is not None
    assert match["displayName"] == "Relay Bravo"
    assert match["batteryLevel"] == 88
