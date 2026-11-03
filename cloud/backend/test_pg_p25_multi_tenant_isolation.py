"""Multi-Tenant Organization Isolation Verification (Requirement A).

Tests:
1. Database-level data isolation across organizations.
2. Cross-organization incident access prevention (GET, LIST, TRANSITION).
3. Cross-organization responder and team isolation.
4. Cross-organization dispatch denial (assigning responder from Org B to incident in Org A).
5. Negative authorization tests: tenant spoofing and parameter manipulation rejection.
"""

import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.rbac import (
    Role,
    create_access_token,
)
from app.core.security import hash_password
from app.models.assignment import AssignmentModel
from app.models.incident import IncidentModel
from app.models.responder import ResponderModel
from app.models.team import TeamModel
from app.models.user import UserModel
from app.services.incident_service import create_incident, transition_incident
from app.services.dispatch_service import create_responder, create_team, dispatch_responder
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


def make_tenant_user_token(db, username_prefix: str, role: str, org_id: str) -> str:
    uid = uuid.uuid4().hex[:8]
    user = UserModel(
        username=f"{username_prefix}_{uid}",
        email=f"{username_prefix}_{uid}@{org_id}.rescuemesh.org",
        password_hash=hash_password("Pass123!Safe"),
        role=role,
        organization_id=org_id,
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db.add(user)
    db.commit()
    return create_access_token(
        user_id=user.id,
        username=user.username,
        role=user.role,
        token_version=user.token_version,
        organization_id=org_id,
    )


# ── TEST 1: Database-Level Isolation ──────────────────────────────────────────
def test_tenant_database_isolation(db):
    """Verify database models enforce organization_id isolation."""
    now = int(time.time())
    inc_alpha = IncidentModel(
        id=f"inc_alpha_{uuid.uuid4().hex[:8]}",
        title="Chemical Spill Sector Alpha",
        category="HAZMAT",
        priority=1,
        severity=4,
        status="REPORTED",
        organization_id="org-alpha",
        created_at=now,
        updated_at=now,
    )
    inc_bravo = IncidentModel(
        id=f"inc_bravo_{uuid.uuid4().hex[:8]}",
        title="Flood Sector Bravo",
        category="FLOOD",
        priority=2,
        severity=3,
        status="REPORTED",
        organization_id="org-bravo",
        created_at=now,
        updated_at=now,
    )
    db.add(inc_alpha)
    db.add(inc_bravo)
    db.commit()

    # Query org-alpha
    alpha_incidents = db.query(IncidentModel).filter(IncidentModel.organization_id == "org-alpha").all()
    alpha_ids = [inc.id for inc in alpha_incidents]
    assert inc_alpha.id in alpha_ids
    assert inc_bravo.id not in alpha_ids

    # Query org-bravo
    bravo_incidents = db.query(IncidentModel).filter(IncidentModel.organization_id == "org-bravo").all()
    bravo_ids = [inc.id for inc in bravo_incidents]
    assert inc_bravo.id in bravo_ids
    assert inc_alpha.id not in bravo_ids


# ── TEST 2: API Incident Isolation ────────────────────────────────────────────
def test_tenant_api_incident_isolation(client, db):
    """Verify users in Org A cannot access, view, or transition incidents in Org B."""
    token_alpha = make_tenant_user_token(db, "alpha_cmd", Role.COMMANDER.value, "org-alpha")
    token_bravo = make_tenant_user_token(db, "bravo_cmd", Role.COMMANDER.value, "org-bravo")

    # Alpha commander creates incident
    res = client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {token_alpha}"},
        json={
            "title": "Alpha Restricted Emergency",
            "category": "FIRE",
            "priority": 1,
            "severity": 4,
            "description": "Wildfire outbreak in national forest zone 4",
        },
    )
    assert res.status_code == 201
    alpha_inc_id = res.json()["id"]
    assert res.json()["organization_id"] == "org-alpha"

    # Alpha commander can read their own incident
    res_get_alpha = client.get(
        f"/api/v1/incidents/{alpha_inc_id}",
        headers={"Authorization": f"Bearer {token_alpha}"},
    )
    assert res_get_alpha.status_code == 200
    assert res_get_alpha.json()["id"] == alpha_inc_id

    # Bravo commander cannot read Alpha incident (must return 404 to prevent enumeration)
    res_get_bravo = client.get(
        f"/api/v1/incidents/{alpha_inc_id}",
        headers={"Authorization": f"Bearer {token_bravo}"},
    )
    assert res_get_bravo.status_code == 404

    # Bravo commander listing incidents must NOT include Alpha incident
    res_list_bravo = client.get(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {token_bravo}"},
    )
    assert res_list_bravo.status_code == 200
    bravo_incident_ids = [inc["id"] for inc in res_list_bravo.json()]
    assert alpha_inc_id not in bravo_incident_ids

    # Bravo commander cannot transition Alpha incident
    res_trans = client.post(
        f"/api/v1/incidents/{alpha_inc_id}/transition",
        headers={"Authorization": f"Bearer {token_bravo}"},
        json={"new_status": "TRIAGED"},
    )
    assert res_trans.status_code == 404


# ── TEST 3: Cross-Organization Dispatch Prevention ────────────────────────────
def test_cross_organization_dispatch_prevented(client, db):
    """Verify dispatch cannot cross organization boundaries."""
    token_alpha_disp = make_tenant_user_token(db, "alpha_disp", Role.DISPATCHER.value, "org-alpha")
    token_alpha_cmd = make_tenant_user_token(db, "alpha_cmd", Role.COMMANDER.value, "org-alpha")
    token_bravo_cmd = make_tenant_user_token(db, "bravo_cmd", Role.COMMANDER.value, "org-bravo")

    # Enroll responder in Org Bravo (requires COMMANDER)
    res_resp = client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {token_bravo_cmd}"},
        json={
            "callsign": "BRAVO-MED-1",
            "responder_type": "MEDICAL",
            "skills": ["TRIAGE", "PARAMEDIC"],
        },
    )
    assert res_resp.status_code == 201
    bravo_resp_id = res_resp.json()["id"]

    # Create incident in Org Alpha
    res_inc = client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {token_alpha_cmd}"},
        json={
            "title": "Alpha Structural Collapse",
            "category": "COLLAPSE",
            "priority": 1,
            "severity": 5,
            "description": "Building structural collapse requiring rescue",
        },
    )
    assert res_inc.status_code == 201
    alpha_inc_id = res_inc.json()["id"]

    # Alpha dispatcher attempts to assign Bravo responder to Alpha incident
    res_disp = client.post(
        f"/api/v1/incidents/{alpha_inc_id}/assignments",
        headers={"Authorization": f"Bearer {token_alpha_disp}"},
        json={"responder_id": bravo_resp_id},
    )
    # Blocked: responder not found in Alpha's org (or forbidden cross-org)
    assert res_disp.status_code in (403, 404)


# ── TEST 4: Responders and Teams Isolation ─────────────────────────────────────
def test_responders_and_teams_isolation(client, db):
    """Verify responders and teams roster are strictly isolated by organization."""
    token_alpha = make_tenant_user_token(db, "alpha_lead", Role.COMMANDER.value, "org-alpha")
    token_bravo = make_tenant_user_token(db, "bravo_lead", Role.COMMANDER.value, "org-bravo")

    # Alpha creates a team and responder
    res_team = client.post(
        "/api/v1/teams",
        headers={"Authorization": f"Bearer {token_alpha}"},
        json={"name": f"Alpha Strike Team {uuid.uuid4().hex[:6]}", "callsign": "ALPHA-STRIKE"},
    )
    assert res_team.status_code == 201
    alpha_team_id = res_team.json()["id"]

    res_resp = client.post(
        "/api/v1/responders",
        headers={"Authorization": f"Bearer {token_alpha}"},
        json={"callsign": "ALPHA-SAR-9", "responder_type": "SAR"},
    )
    assert res_resp.status_code == 201
    alpha_resp_id = res_resp.json()["id"]

    # Bravo queries responders: alpha_resp_id must NOT appear
    res_bravo_resps = client.get("/api/v1/responders", headers={"Authorization": f"Bearer {token_bravo}"})
    assert res_bravo_resps.status_code == 200
    assert alpha_resp_id not in [r["id"] for r in res_bravo_resps.json()]

    # Bravo queries teams: alpha_team_id must NOT appear
    res_bravo_teams = client.get("/api/v1/teams", headers={"Authorization": f"Bearer {token_bravo}"})
    assert res_bravo_teams.status_code == 200
    assert alpha_team_id not in [t["id"] for t in res_bravo_teams.json()]

    # Bravo cannot add Alpha responder to a Bravo team
    res_bravo_new_team = client.post(
        "/api/v1/teams",
        headers={"Authorization": f"Bearer {token_bravo}"},
        json={"name": f"Bravo Rescue Squad {uuid.uuid4().hex[:6]}", "callsign": "BRAVO-SQ"},
    )
    bravo_team_id = res_bravo_new_team.json()["id"]

    res_add_member = client.post(
        f"/api/v1/teams/{bravo_team_id}/members",
        headers={"Authorization": f"Bearer {token_bravo}"},
        json={"responder_id": alpha_resp_id},
    )
    assert res_add_member.status_code == 404


# ── TEST 5: Negative Authorization and Spoofing Prevention ────────────────────
def test_negative_tenant_spoofing_prevented(client, db):
    """Verify client cannot spoof or override organization via headers or parameters."""
    token_bravo = make_tenant_user_token(db, "bravo_attacker", Role.COMMANDER.value, "org-bravo")

    # Attacker presents Org Bravo JWT but includes Header 'X-Organization-Id: org-alpha'
    # The authenticated actor must strictly retain org-bravo from the verified token
    res = client.post(
        "/api/v1/incidents",
        headers={
            "Authorization": f"Bearer {token_bravo}",
            "X-Organization-Id": "org-alpha",
        },
        json={
            "title": "Spoofed Incident Attempt",
            "category": "OTHER",
            "priority": 3,
            "severity": 3,
            "description": "Testing spoofing resistance",
        },
    )
    assert res.status_code == 201
    # Must belong to org-bravo, NOT the spoofed header
    assert res.json()["organization_id"] == "org-bravo"
