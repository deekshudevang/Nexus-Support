"""Unit and integration tests for Phase PG-P2: Security, RBAC, and Device Keystore."""

import base64
import os
import sys
import time
import uuid
import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

# Ensure cloud/backend is on python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.device_auth import compute_canonical_message, sign_device_request
from app.core.rbac import (
    Permission,
    Role,
    create_access_token,
    decode_access_token,
)
from app.core.security import hash_password
from app.models.device import DeviceModel
from app.models.user import UserModel
from main import app


@pytest.fixture
def test_client():
    client = TestClient(app)
    return client


@pytest.fixture
def db_session():
    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


@pytest.fixture
def ec_keypair():
    """Generate a test P-256 EC key pair."""
    private_key = ec.generate_private_key(ec.SECP256R1())
    public_key = private_key.public_key()
    pem = public_key.public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode("utf-8")
    return private_key, pem


def test_token_lifecycle_and_tamper_detection():
    """Verify access token generation, claims verification, and signature tampering."""
    token = create_access_token(
        user_id=101,
        username="lead_medic",
        role="responder",
        token_version=1,
        expires_in_sec=3600,
    )
    claims = decode_access_token(token)
    assert claims["sub"] == "101"
    assert claims["username"] == "lead_medic"
    assert claims["role"] == "responder"
    assert claims["ver"] == 1

    # Tampered payload detection
    parts = token.split(".")
    tampered_token = f"{parts[0]}.eyJhZG1pbiI6dHJ1ZX0.{parts[2]}"
    with pytest.raises(Exception):
        decode_access_token(tampered_token)

    # Expired token detection
    expired_token = create_access_token(
        user_id=102,
        username="expired_user",
        role="viewer",
        token_version=1,
        expires_in_sec=-10,  # in the past
    )
    with pytest.raises(Exception) as excinfo:
        decode_access_token(expired_token)
    assert "expired" in str(excinfo.value).lower()


def test_user_login_and_me_endpoint(test_client, db_session):
    """Verify user login against DB and capability reporting via /api/v1/auth/me."""
    u_id = uuid.uuid4().hex[:8]
    uname = f"dispatcher_{u_id}"
    pwd_hash = hash_password("SecurePass123!")
    user = UserModel(
        username=uname,
        email=f"{uname}@rescuemesh.org",
        password_hash=pwd_hash,
        role="dispatcher",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(user)
    db_session.commit()

    # Login with valid credentials
    resp = test_client.post(
        "/api/v1/auth/login",
        json={"username": uname, "password": "SecurePass123!"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert "token" in data
    assert data["role"] == "dispatcher"
    token = data["token"]

    # Call /api/v1/auth/me
    me_resp = test_client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert me_resp.status_code == 200
    me_data = me_resp.json()
    assert me_data["username"] == uname
    assert me_data["role"] == "dispatcher"
    assert Permission.INCIDENT_TRIAGE.value in me_data["permissions"]
    assert Permission.ASSIGNMENT_DISPATCH.value in me_data["permissions"]
    assert Permission.USER_MANAGE.value not in me_data["permissions"]


def test_logout_and_session_invalidation(test_client, db_session):
    """Verify that logout bumps token_version and immediately invalidates active tokens."""
    u_id = uuid.uuid4().hex[:8]
    uname = f"medic_{u_id}"
    pwd_hash = hash_password("LogoutTestPass123!")
    user = UserModel(
        username=uname,
        email=f"{uname}@rescuemesh.org",
        password_hash=pwd_hash,
        role="responder",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(user)
    db_session.commit()

    login_resp = test_client.post(
        "/api/v1/auth/login",
        json={"username": uname, "password": "LogoutTestPass123!"},
    )
    assert login_resp.status_code == 200
    token = login_resp.json()["token"]

    # Token works initially
    assert test_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).status_code == 200

    # Logout
    logout_resp = test_client.post("/api/v1/auth/logout", headers={"Authorization": f"Bearer {token}"})
    assert logout_resp.status_code == 200

    # Subsequent request using the old token must fail with 401
    expired_resp = test_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert expired_resp.status_code == 401
    assert "invalidated" in expired_resp.json()["detail"].lower()


def test_negative_authorization_and_rbac(test_client, db_session):
    """Verify viewer cannot execute privileged management or triage actions."""
    u_id = uuid.uuid4().hex[:8]
    uname = f"viewer_{u_id}"
    pwd_hash = hash_password("ViewerPass123!")
    user = UserModel(
        username=uname,
        email=f"{uname}@observer.org",
        password_hash=pwd_hash,
        role="viewer",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(user)
    db_session.commit()

    login_resp = test_client.post(
        "/api/v1/auth/login",
        json={"username": uname, "password": "ViewerPass123!"},
    )
    token = login_resp.json()["token"]

    # Viewer CAN access team list (TEAM_READ)
    resp = test_client.get("/api/v1/devices", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200

    # Viewer CANNOT revoke a device (requires RESPONDER_MANAGE)
    revoke_resp = test_client.delete(
        "/api/v1/devices/dev_some_node",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert revoke_resp.status_code == 403
    assert "Insufficient permissions" in revoke_resp.json()["detail"]


def test_device_enrollment_and_ecdsa_signature_auth(test_client, db_session, ec_keypair):
    """Verify device registration, cryptographic signature validation, replay protection, and tamper detection."""
    priv_key, pub_pem = ec_keypair
    device_id = "node_tactical_99"

    # 1. Enroll device
    enroll_resp = test_client.post(
        "/api/v1/devices/enroll",
        json={
            "device_id": device_id,
            "public_key_pem": pub_pem,
            "role": "field_node",
        },
    )
    assert enroll_resp.status_code == 200
    assert enroll_resp.json()["device_id"] == device_id
    assert enroll_resp.json()["status"] == "ACTIVE"

    # 2. Authenticate heartbeat with valid ECDSA signature
    path = f"/api/v1/devices/{device_id}/heartbeat"
    method = "POST"
    now_ts = str(int(time.time()))
    body = b'{"battery_level":88,"rssi":-65,"active_mesh_peers":4}'
    sig = sign_device_request(priv_key, method, path, now_ts, body)

    heartbeat_resp = test_client.post(
        path,
        content=body,
        headers={
            "Content-Type": "application/json",
            "X-Device-Id": device_id,
            "X-Device-Timestamp": now_ts,
            "X-Device-Signature": sig,
        },
    )
    assert heartbeat_resp.status_code == 200
    assert heartbeat_resp.json()["status"] == "SUCCESS"

    # 3. Replay attack rejection (timestamp older than 300 seconds)
    stale_ts = str(int(time.time()) - 400)
    stale_sig = sign_device_request(priv_key, method, path, stale_ts, body)
    replay_resp = test_client.post(
        path,
        content=body,
        headers={
            "Content-Type": "application/json",
            "X-Device-Id": device_id,
            "X-Device-Timestamp": stale_ts,
            "X-Device-Signature": stale_sig,
        },
    )
    assert replay_resp.status_code == 401
    assert "replay window" in replay_resp.json()["detail"].lower()

    # 4. Tampered body rejection
    tampered_body = b'{"battery_level":12,"rssi":-99,"active_mesh_peers":0}'
    tamper_resp = test_client.post(
        path,
        content=tampered_body,
        headers={
            "Content-Type": "application/json",
            "X-Device-Id": device_id,
            "X-Device-Timestamp": now_ts,
            "X-Device-Signature": sig,  # Signature was for original body!
        },
    )
    assert tamper_resp.status_code == 401
    assert "signature verification failed" in tamper_resp.json()["detail"].lower()


def test_device_idor_protection(test_client, db_session, ec_keypair):
    """Verify that Device A cannot impersonate or submit telemetry for Device B."""
    priv_key_a, pub_pem_a = ec_keypair
    priv_key_b, pub_pem_b = ec.generate_private_key(ec.SECP256R1()), ec.generate_private_key(ec.SECP256R1()).public_key().public_bytes(
        serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
    ).decode("utf-8")

    test_client.post("/api/v1/devices/enroll", json={"device_id": "node_alpha", "public_key_pem": pub_pem_a})
    test_client.post("/api/v1/devices/enroll", json={"device_id": "node_bravo", "public_key_pem": pub_pem_b})

    # Device Alpha signs a request targeting Node Bravo's heartbeat endpoint
    target_path = "/api/v1/devices/node_bravo/heartbeat"
    now_ts = str(int(time.time()))
    body = b'{"battery_level":50}'
    sig_alpha = sign_device_request(priv_key_a, "POST", target_path, now_ts, body)

    idor_resp = test_client.post(
        target_path,
        content=body,
        headers={
            "Content-Type": "application/json",
            "X-Device-Id": "node_alpha",
            "X-Device-Timestamp": now_ts,
            "X-Device-Signature": sig_alpha,
        },
    )
    assert idor_resp.status_code == 403
    assert "Device IDOR violation" in idor_resp.json()["detail"]


def test_revoked_device_cannot_authenticate(test_client, db_session, ec_keypair):
    """Verify that revoking a device immediately terminates its access."""
    priv_key, pub_pem = ec_keypair
    device_id = f"node_rev_{uuid.uuid4().hex[:6]}"

    test_client.post("/api/v1/devices/enroll", json={"device_id": device_id, "public_key_pem": pub_pem})

    # Create admin user for authorized revoke
    admin_uname = f"admin_{uuid.uuid4().hex[:6]}"
    admin = UserModel(
        username=admin_uname,
        email=f"{admin_uname}@rescuemesh.org",
        password_hash=hash_password("AdminPass123!"),
        role="admin",
        status="active",
        is_active=True,
        token_version=1,
        created_at=int(time.time()),
    )
    db_session.add(admin)
    db_session.commit()

    admin_token = test_client.post(
        "/api/v1/auth/login",
        json={"username": admin_uname, "password": "AdminPass123!"},
    ).json()["token"]

    # Revoke device using admin token
    del_resp = test_client.delete(
        f"/api/v1/devices/{device_id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert del_resp.status_code == 200

    # Try heartbeat with revoked device
    path = f"/api/v1/devices/{device_id}/heartbeat"
    now_ts = str(int(time.time()))
    body = b'{}'
    sig = sign_device_request(priv_key, "POST", path, now_ts, body)

    blocked_resp = test_client.post(
        path,
        content=body,
        headers={
            "Content-Type": "application/json",
            "X-Device-Id": device_id,
            "X-Device-Timestamp": now_ts,
            "X-Device-Signature": sig,
        },
    )
    assert blocked_resp.status_code == 403
    assert "revoked" in blocked_resp.json()["detail"].lower()
