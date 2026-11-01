"""Unit and integration tests for Phase PG-P4: Responders, Teams, and Dispatch Operations."""

import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.rbac import create_access_token
from app.core.security import hash_password
from app.models.assignment import AssignmentModel
from app.models.incident import IncidentModel
from app.models.responder import ResponderModel
from app.models.team import TeamModel
from app.models.user import UserModel
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


# ── PG-P4-01: Responders, Teams & Availability ───────────────────────────────

def test_team_and_responder_roster_management(test_client, db_session, commander_token):
    """Verify team creation, responder registration, and roster association."""
    # 1. Create tactical team
    team_name = f"Alpha SAR {uuid.uuid4().hex[:6]}"
    team_resp = test_client.post(
        "/api/v1/teams",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"name": team_name, "callsign": "ALPHA-1"},
    )
    assert team_resp.status_code == 201
    team_id = team_resp.json()["id"]

    # 2. Create responder
    resp_callsign = f"MEDIC-{uuid.uuid4().hex[:4].upper()}"
    resp = test_client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={
            "callsign": resp_callsign,
            "responder_type": "MEDICAL",
            "skills": ["TRIAGE", "CPR", "HEMORRHAGE_CONTROL"],
        },
    )
    assert resp.status_code == 201
    responder_id = resp.json()["id"]
    assert resp.json()["availability"] == "AVAILABLE"

    # 3. Add responder to team
    mem_resp = test_client.post(
        f"/api/v1/teams/{team_id}/members",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"responder_id": responder_id, "role_in_team": "LEAD_MEDIC"},
    )
    assert mem_resp.status_code == 200
    assert mem_resp.json()["status"] == "SUCCESS"


# ── PG-P4-02: Concurrent Dispatch & Partial Unique Index ─────────────────────

def test_dispatch_and_double_dispatch_prevention(test_client, db_session, commander_token, dispatcher_token):
    """
    Verify dispatch creates active assignment and double-dispatch of the same responder
    is rejected with HTTP 409 Conflict by partial unique index protection.
    """
    # Create 2 incidents
    r1 = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"title": "Incident 1", "category": "MEDICAL", "description": "Crash", "priority": 1, "severity": 4},
    )
    inc1_id = r1.json()["id"]

    r2 = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"title": "Incident 2", "category": "FIRE", "description": "Brush Fire", "priority": 2, "severity": 3},
    )
    inc2_id = r2.json()["id"]

    # Register an available responder
    c_sign = f"K9-{uuid.uuid4().hex[:4].upper()}"
    resp_obj = test_client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"callsign": c_sign, "responder_type": "K9_SEARCH", "skills": ["TRACKING"]},
    )
    responder_id = resp_obj.json()["id"]

    # 1. Dispatch responder to Incident 1 (Succeeds)
    asgn_resp = test_client.post(
        f"/api/v1/incidents/{inc1_id}/assignments",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"responder_id": responder_id},
    )
    assert asgn_resp.status_code == 201
    asgn1_id = asgn_resp.json()["id"]
    assert asgn_resp.json()["status"] == "DISPATCHED"

    # Verify responder availability is now ASSIGNED
    resp_check = test_client.get(
        f"/api/v1/responders/{responder_id}",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
    )
    assert resp_check.json()["availability"] == "ASSIGNED"

    # 2. Attempt double-dispatch to Incident 2 (Must be rejected with 409 Conflict)
    asgn2_resp = test_client.post(
        f"/api/v1/incidents/{inc2_id}/assignments",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"responder_id": responder_id},
    )
    assert asgn2_resp.status_code == 409
    assert "already has an active assignment" in asgn2_resp.json()["detail"].lower()


def test_assignment_full_workflow_and_availability_release(test_client, db_session, commander_token, dispatcher_token):
    """
    Verify complete assignment lifecycle:
    DISPATCHED -> ACCEPTED -> EN_ROUTE -> ON_SCENE -> COMPLETED,
    and confirm responder availability is automatically released upon completion.
    """
    # Setup incident & responder
    inc_resp = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"title": "Cave Evacuation", "category": "SEARCH_RESCUE", "description": "Trapped spelunkers", "priority": 1, "severity": 4},
    )
    inc_id = inc_resp.json()["id"]

    resp_obj = test_client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"callsign": f"CAVE-{uuid.uuid4().hex[:4].upper()}", "responder_type": "SAR"},
    )
    responder_id = resp_obj.json()["id"]

    # Dispatch
    asgn_id = test_client.post(
        f"/api/v1/incidents/{inc_id}/assignments",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"responder_id": responder_id},
    ).json()["id"]

    # DISPATCHED -> ACCEPTED
    t1 = test_client.post(
        f"/api/v1/assignments/{asgn_id}/transition",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"new_status": "ACCEPTED"},
    )
    assert t1.status_code == 200
    assert t1.json()["status"] == "ACCEPTED"
    assert t1.json()["responded_at"] is not None

    # ACCEPTED -> EN_ROUTE
    t2 = test_client.post(
        f"/api/v1/assignments/{asgn_id}/transition",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"new_status": "EN_ROUTE"},
    )
    assert t2.status_code == 200
    assert t2.json()["status"] == "EN_ROUTE"

    # EN_ROUTE -> ON_SCENE
    t3 = test_client.post(
        f"/api/v1/assignments/{asgn_id}/transition",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"new_status": "ON_SCENE"},
    )
    assert t3.status_code == 200
    assert t3.json()["status"] == "ON_SCENE"

    # ON_SCENE -> COMPLETED
    t4 = test_client.post(
        f"/api/v1/assignments/{asgn_id}/transition",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"new_status": "COMPLETED"},
    )
    assert t4.status_code == 200
    assert t4.json()["status"] == "COMPLETED"
    assert t4.json()["completed_at"] is not None

    # Verify responder availability is now released back to AVAILABLE
    resp_check = test_client.get(
        f"/api/v1/responders/{responder_id}",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
    )
    assert resp_check.json()["availability"] == "AVAILABLE"

    # Confirm the responder can now be dispatched to a subsequent incident without 409
    inc2_id = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"title": "Next Mission", "category": "MEDICAL", "description": "Checkup", "priority": 3, "severity": 2},
    ).json()["id"]

    new_asgn = test_client.post(
        f"/api/v1/incidents/{inc2_id}/assignments",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"responder_id": responder_id},
    )
    assert new_asgn.status_code == 201


def test_assignment_idor_protection(test_client, db_session, commander_token, dispatcher_token):
    """Verify that a responder cannot modify another responder's assignment."""
    # Create two responder users
    u1_id = uuid.uuid4().hex[:8]
    u2_id = uuid.uuid4().hex[:8]

    user1 = UserModel(
        username=f"resp1_{u1_id}",
        email=f"resp1_{u1_id}@test.org",
        password_hash=hash_password("Pass123!"),
        role="responder",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    user2 = UserModel(
        username=f"resp2_{u2_id}",
        email=f"resp2_{u2_id}@test.org",
        password_hash=hash_password("Pass123!"),
        role="responder",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add_all([user1, user2])
    db_session.commit()

    token1 = create_access_token(user1.id, user1.username, user1.role, user1.token_version)
    token2 = create_access_token(user2.id, user2.username, user2.role, user2.token_version)

    # Register responders associated with these users
    resp1 = test_client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"callsign": f"UNIT-1-{u1_id[:4]}", "user_id": user1.id},
    ).json()["id"]

    resp2 = test_client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"callsign": f"UNIT-2-{u2_id[:4]}", "user_id": user2.id},
    ).json()["id"]

    # Create incident & dispatch Responder 1
    inc_id = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"title": "IDOR Test Incident", "category": "MEDICAL", "description": "Test", "priority": 2, "severity": 3},
    ).json()["id"]

    asgn_id = test_client.post(
        f"/api/v1/incidents/{inc_id}/assignments",
        headers={"Authorization": f"Bearer {dispatcher_token}"},
        json={"responder_id": resp1},
    ).json()["id"]

    # Responder 2 attempts to transition Responder 1's assignment (Must fail with 403 Forbidden)
    idor_attempt = test_client.post(
        f"/api/v1/assignments/{asgn_id}/transition",
        headers={"Authorization": f"Bearer {token2}"},
        json={"new_status": "ACCEPTED"},
    )
    assert idor_attempt.status_code == 403
    assert "IDOR violation" in idor_attempt.json()["detail"]

    # Responder 1 transitions their own assignment (Succeeds)
    valid_attempt = test_client.post(
        f"/api/v1/assignments/{asgn_id}/transition",
        headers={"Authorization": f"Bearer {token1}"},
        json={"new_status": "ACCEPTED"},
    )
    assert valid_attempt.status_code == 200
    assert valid_attempt.json()["status"] == "ACCEPTED"
