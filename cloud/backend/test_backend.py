import json
import os
import tempfile
import time
import pytest
from fastapi.testclient import TestClient

# Ensure test configuration is set before importing app
os.environ["ENVIRONMENT"] = "development"
os.environ["CORS_ORIGINS"] = "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173"
os.environ["GATEWAY_API_KEYS"] = "gw_key_valid_1,gw_key_valid_2"
os.environ["OPERATOR_TOKENS"] = "op_token_valid_1,op_token_valid_2"
os.environ["REVOKED_CREDENTIALS"] = "gw_revoked_key,op_revoked_token"

from main import app, init_db, get_db, generate_ws_ticket, _ws_tickets, validate_production_configuration

@pytest.fixture(autouse=True)
def setup_isolated_db():
    temp_dir = tempfile.mkdtemp()
    test_db = os.path.join(temp_dir, "test_sync.db")
    import main
    main._ip_request_history.clear()
    main.DATABASE_PATH = test_db
    init_db(test_db)
    yield
    main._ip_request_history.clear()
    if os.path.exists(test_db):
        try:
            os.remove(test_db)
        except Exception:
            pass

@pytest.fixture
def client():
    return TestClient(app)

# ── Production Configuration Guardrails ──────────────────────────────────────
def test_production_configuration_accepts_explicit_secure_values():
    validate_production_configuration(
        "production",
        {"fresh-gateway-key"},
        {"fresh-operator-token"},
        ["https://dispatch.example.org"],
    )


@pytest.mark.parametrize(
    ("gateway_keys", "operator_tokens", "origins"),
    [
        ({"meshlink_gw_dev_secret"}, {"fresh-operator-token"}, ["https://dispatch.example.org"]),
        ({"fresh-gateway-key"}, {"meshlink_op_dev_secret"}, ["https://dispatch.example.org"]),
        ({"fresh-gateway-key"}, {"fresh-operator-token"}, ["http://dispatch.example.org"]),
        ({"fresh-gateway-key"}, {"fresh-operator-token"}, ["https://localhost:8443"]),
        ({"fresh-gateway-key"}, {"fresh-operator-token"}, ["*"]),
        ({"fresh-gateway-key"}, {"fresh-operator-token"}, []),
    ],
)
def test_production_configuration_rejects_unsafe_values(gateway_keys, operator_tokens, origins):
    with pytest.raises(RuntimeError, match="Unsafe production configuration"):
        validate_production_configuration("production", gateway_keys, operator_tokens, origins)


def test_development_configuration_keeps_local_defaults_available():
    validate_production_configuration(
        "development",
        {"meshlink_gw_dev_secret"},
        {"meshlink_op_dev_secret"},
        ["http://localhost:5173"],
    )


# ── Health Endpoint ──────────────────────────────────────────────────────────
def test_health_endpoint(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert "environment" in data
    assert "timestamp" in data
    # Ensure no secrets or sensitive info in health payload
    assert "key" not in data
    assert "token" not in data

# ── Gateway Ingestion & Role Separation ──────────────────────────────────────
def test_sync_sos_unauthenticated(client):
    payload = [{
        "uuid": "sos-101",
        "senderId": "node-1",
        "senderName": "Alpha",
        "emergencyType": "MEDICAL",
        "severity": 9,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "message": "Need oxygen",
        "timestamp": int(time.time()),
        "status": "ACTIVE"
    }]
    response = client.post("/api/sync/sos", json=payload)
    assert response.status_code == 401
    assert "Gateway credentials required" in response.json()["detail"]

def test_sync_sos_role_separation_operator_cannot_write(client):
    payload = [{
        "uuid": "sos-102",
        "senderId": "node-2",
        "senderName": "Beta",
        "emergencyType": "EVACUATION",
        "severity": 8,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "message": "Water rising",
        "timestamp": int(time.time()),
        "status": "ACTIVE"
    }]
    # Attempting to write with operator token must be rejected
    headers = {"Authorization": "Bearer op_token_valid_1"}
    response = client.post("/api/sync/sos", json=payload, headers=headers)
    assert response.status_code == 401
    assert "Invalid gateway credentials" in response.json()["detail"]

def test_sync_sos_revoked_credentials_rejected(client):
    payload = [{
        "uuid": "sos-103",
        "senderId": "node-3",
        "senderName": "Gamma",
        "emergencyType": "FIRE",
        "severity": 10,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "message": "Structure fire",
        "timestamp": int(time.time()),
        "status": "ACTIVE"
    }]
    headers = {"X-Gateway-Key": "gw_revoked_key"}
    response = client.post("/api/sync/sos", json=payload, headers=headers)
    assert response.status_code == 403
    assert "revoked" in response.json()["detail"].lower()

def test_sync_sos_validation_constraints(client):
    headers = {"X-Gateway-Key": "gw_key_valid_1"}
    # Severity out of bounds (15 > 10)
    invalid_severity = [{
        "uuid": "sos-invalid",
        "senderId": "node-1",
        "senderName": "Alpha",
        "emergencyType": "SEARCH",
        "severity": 15,
        "latitude": 0.0,
        "longitude": 0.0,
        "message": "Test",
        "timestamp": int(time.time()),
        "status": "ACTIVE"
    }]
    response = client.post("/api/sync/sos", json=invalid_severity, headers=headers)
    assert response.status_code == 422

    # Latitude out of bounds (95 > 90)
    invalid_coords = [{
        "uuid": "sos-invalid",
        "senderId": "node-1",
        "senderName": "Alpha",
        "emergencyType": "SEARCH",
        "severity": 5,
        "latitude": 95.0,
        "longitude": 0.0,
        "message": "Test",
        "timestamp": int(time.time()),
        "status": "ACTIVE"
    }]
    response = client.post("/api/sync/sos", json=invalid_coords, headers=headers)
    assert response.status_code == 422

def test_sync_sos_success(client):
    headers = {"X-Gateway-Key": "gw_key_valid_1"}
    payload = [{
        "uuid": "sos-success-1",
        "senderId": "node-succ",
        "senderName": "Responder 1",
        "emergencyType": "MEDICAL",
        "severity": 7,
        "latitude": 34.0522,
        "longitude": -118.2437,
        "message": "All clear",
        "timestamp": int(time.time()),
        "status": "ACTIVE",
        "hopCount": 2
    }]
    response = client.post("/api/sync/sos", json=payload, headers=headers)
    assert response.status_code == 200
    assert response.json()["status"] == "success"
    assert response.json()["synced_count"] == 1

# ── Operator Query Endpoints ─────────────────────────────────────────────────
def test_get_sos_unauthenticated(client):
    response = client.get("/api/sos")
    assert response.status_code == 401

def test_get_sos_role_separation_gateway_cannot_read(client):
    # Gateway credential cannot read operator endpoints
    headers = {"Authorization": "Bearer gw_key_valid_1"}
    response = client.get("/api/sos", headers=headers)
    assert response.status_code == 401
    assert "Invalid operator token" in response.json()["detail"]

def test_get_sos_revoked_operator_rejected(client):
    headers = {"Authorization": "Bearer op_revoked_token"}
    response = client.get("/api/sos", headers=headers)
    assert response.status_code == 403

def test_get_sos_success_and_filtering(client):
    # Ingest test SOS
    gw_headers = {"X-Gateway-Key": "gw_key_valid_1"}
    now = int(time.time())
    packets = [
        {"uuid": "s1", "senderId": "n1", "senderName": "A", "emergencyType": "MED", "severity": 4, "latitude": 0.0, "longitude": 0.0, "message": "Low", "timestamp": now - 10, "status": "ACTIVE"},
        {"uuid": "s2", "senderId": "n2", "senderName": "B", "emergencyType": "MED", "severity": 9, "latitude": 0.0, "longitude": 0.0, "message": "High", "timestamp": now, "status": "ACTIVE"}
    ]
    client.post("/api/sync/sos", json=packets, headers=gw_headers)

    op_headers = {"Authorization": "Bearer op_token_valid_1"}
    response = client.get("/api/sos", headers=op_headers)
    assert response.status_code == 200
    rows = response.json()
    assert len(rows) == 2
    # Verify order timestamp DESC
    assert rows[0]["uuid"] == "s2"

    # Verify severity filter
    filtered = client.get("/api/sos?severity_min=8", headers=op_headers)
    assert filtered.status_code == 200
    f_rows = filtered.json()
    assert len(f_rows) == 1
    assert f_rows[0]["uuid"] == "s2"

# ── Node Status Ingestion & Query ────────────────────────────────────────────
def test_sync_and_get_nodes(client):
    gw_headers = {"X-Gateway-Key": "gw_key_valid_2"}
    op_headers = {"Authorization": "Bearer op_token_valid_2"}

    nodes_payload = [
        {
            "deviceId": "dev-001",
            "displayName": "Relay Alpha",
            "role": "REPEATER",
            "batteryLevel": 88,
            "needsHelp": False,
            "latitude": 37.77,
            "longitude": -122.42,
            "timestamp": int(time.time())
        }
    ]
    # Ingest
    res_post = client.post("/api/sync/nodes", json=nodes_payload, headers=gw_headers)
    assert res_post.status_code == 200
    assert res_post.json()["synced_count"] == 1

    # Read
    res_get = client.get("/api/nodes", headers=op_headers)
    assert res_get.status_code == 200
    node_list = res_get.json()
    assert len(node_list) == 1
    assert node_list[0]["deviceId"] == "dev-001"
    assert node_list[0]["displayName"] == "Relay Alpha"
    assert node_list[0]["batteryLevel"] == 88

# ── WebSocket Authentication & Alerts Handshake ──────────────────────────────
def test_ws_ticket_issuance_and_consumption(client):
    op_headers = {"Authorization": "Bearer op_token_valid_1"}
    ticket_res = client.post("/api/auth/ws-ticket", headers=op_headers)
    assert ticket_res.status_code == 200
    ticket = ticket_res.json()["ticket"]
    assert len(ticket) >= 32

    # Connect to WebSocket with ticket in query param
    with client.websocket_connect(f"/ws/alerts?ticket={ticket}") as ws:
        msg = ws.receive_json()
        assert msg["type"] == "authenticated"

    # Ticket must be consumed (single-use)
    # Attempting to reconnect with the same ticket must fail immediately
    with pytest.raises(Exception):
        with client.websocket_connect(f"/ws/alerts?ticket={ticket}"):
            pass

def test_ws_bad_origin_rejected(client):
    op_headers = {"Authorization": "Bearer op_token_valid_1"}
    ticket = client.post("/api/auth/ws-ticket", headers=op_headers).json()["ticket"]

    # Connecting with unauthorized Origin header
    with pytest.raises(Exception):
        with client.websocket_connect(
            f"/ws/alerts?ticket={ticket}",
            headers={"Origin": "http://malicious-site.example.com"}
        ):
            pass

def test_ws_expired_ticket_rejected(client):
    # Artificially insert an expired ticket
    ticket = "expired_test_ticket_12345"
    _ws_tickets[ticket] = time.time() - 10.0  # expired 10 seconds ago

    with pytest.raises(Exception):
        with client.websocket_connect(f"/ws/alerts?ticket={ticket}"):
            pass

def test_ws_initial_auth_message_success(client):
    # Connect without ticket, then send timed auth message with valid operator token
    with client.websocket_connect("/ws/alerts") as ws:
        ws.send_text(json.dumps({"type": "auth", "token": "op_token_valid_1"}))
        ack = ws.receive_json()
        assert ack["type"] == "authenticated"

        # Test heartbeat
        ws.send_text(json.dumps({"type": "ping"}))
        pong = ws.receive_json()
        assert pong["type"] == "pong"

def test_ws_realtime_broadcast_delivery(client):
    op_headers = {"Authorization": "Bearer op_token_valid_1"}
    gw_headers = {"X-Gateway-Key": "gw_key_valid_1"}
    ticket = client.post("/api/auth/ws-ticket", headers=op_headers).json()["ticket"]

    with client.websocket_connect(f"/ws/alerts?ticket={ticket}") as ws:
        ack = ws.receive_json()
        assert ack["type"] == "authenticated"

        # Ingest new SOS packet via REST gateway endpoint
        packet = [{
            "uuid": "live-alert-broadcast-001",
            "senderId": "sensor-9",
            "senderName": "Perimeter 9",
            "emergencyType": "FLASH_FLOOD",
            "severity": 10,
            "latitude": 38.0,
            "longitude": -120.0,
            "message": "Water cresting levee",
            "timestamp": int(time.time()),
            "status": "ACTIVE",
            "hopCount": 1
        }]
        client.post("/api/sync/sos", json=packet, headers=gw_headers)

        # WebSocket must receive the alert payload in real time
        live_alert = ws.receive_json()
        assert live_alert["type"] == "sos_alert"
        assert live_alert["packet"]["uuid"] == "live-alert-broadcast-001"
        assert live_alert["packet"]["severity"] == 10
        assert live_alert["packet"]["emergencyType"] == "FLASH_FLOOD"

def test_ws_initial_auth_message_invalid_token_rejected(client):
    # Connect without ticket, send invalid token
    with pytest.raises(Exception):
        with client.websocket_connect("/ws/alerts") as ws:
            ws.send_text(json.dumps({"type": "auth", "token": "invalid_wrong_token"}))
            # Server closes with WS_1008_POLICY_VIOLATION
            ws.receive_json()
            ws.receive_json()

def test_ws_no_alerts_before_authentication(client):
    gw_headers = {"X-Gateway-Key": "gw_key_valid_1"}
    # Connect client without ticket (unauthenticated state)
    with client.websocket_connect("/ws/alerts") as ws:
        # Ingest SOS while client is unauthenticated
        packet = [{
            "uuid": "pre-auth-alert-001",
            "senderId": "sensor-1",
            "senderName": "Sensor 1",
            "emergencyType": "MEDICAL",
            "severity": 6,
            "latitude": 38.0,
            "longitude": -120.0,
            "message": "Pre-auth test",
            "timestamp": int(time.time()),
            "status": "ACTIVE"
        }]
        client.post("/api/sync/sos", json=packet, headers=gw_headers)

        # Now authenticate
        ws.send_text(json.dumps({"type": "auth", "token": "op_token_valid_1"}))
        ack = ws.receive_json()
        assert ack["type"] == "authenticated"
        # The pre-auth alert must NOT have been delivered to this client

def test_production_non_tls_ws_rejected(client):
    import main
    orig_env = main.ENVIRONMENT
    try:
        main.ENVIRONMENT = "production"
        with pytest.raises(Exception):
            with client.websocket_connect("/ws/alerts"):
                pass
    finally:
        main.ENVIRONMENT = orig_env

def test_operator_token_login(client):
    # Valid login
    res = client.post("/api/auth/token", json={"username": "admin", "password": "op_token_valid_1"})
    assert res.status_code == 200
    data = res.json()
    assert "access_token" in data
    assert data["role"] == "operator"

    # Invalid login
    res_bad = client.post("/api/auth/token", json={"username": "admin", "password": "wrong_password"})
    assert res_bad.status_code == 401

def test_sync_sos_nullable_gps_and_receipts(client):
    gw_headers = {"X-Gateway-Key": "gw_key_valid_1"}
    op_headers = {"Authorization": "Bearer op_token_valid_1"}

    # SOS packet without GPS fix (e.g. underground or indoor victim)
    no_gps_packet = [{
        "uuid": "sos-no-gps-001",
        "senderId": "node-basement-1",
        "senderName": "Basement Resident",
        "emergencyType": "MEDICAL",
        "severity": 8,
        "latitude": None,
        "longitude": None,
        "gpsFixAvailable": False,
        "message": "Trapped underground, no GPS lock",
        "timestamp": int(time.time()),
        "status": "PENDING",
        "hopCount": 2
    }]

    response = client.post("/api/v1/sync/sos", json=no_gps_packet, headers=gw_headers)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["synced_count"] == 1
    assert "receipts" in data
    assert len(data["receipts"]) == 1
    assert data["receipts"][0]["uuid"] == "sos-no-gps-001"
    assert data["receipts"][0]["status"] == "ACKNOWLEDGED"

    # Query as operator to verify record was stored truthfully
    get_res = client.get("/api/sos", headers=op_headers)
    assert get_res.status_code == 200
    items = get_res.json()
    found = next((i for i in items if i["uuid"] == "sos-no-gps-001"), None)
    assert found is not None
    assert found["latitude"] is None
    assert found["longitude"] is None
    assert found["gpsFixAvailable"] == 0

def test_user_registration_and_persistent_login(client):
    op_headers = {"Authorization": "Bearer op_token_valid_1"}

    # 1. Register a new operator user
    reg_payload = {
        "username": "sar_lead_john",
        "password": "SecurePassword123!",
        "role": "responder"
    }
    reg_res = client.post("/api/auth/register", json=reg_payload, headers=op_headers)
    assert reg_res.status_code == 200
    reg_data = reg_res.json()
    assert reg_data["status"] == "success"
    assert reg_data["username"] == "sar_lead_john"

    # 2. Login with registered user credentials
    login_res = client.post("/api/auth/token", json={
        "username": "sar_lead_john",
        "password": "SecurePassword123!"
    })
    assert login_res.status_code == 200
    login_data = login_res.json()
    assert "access_token" in login_data
    assert login_data["role"] == "responder"

    # 3. Wrong password fails
    bad_login = client.post("/api/auth/token", json={
        "username": "sar_lead_john",
        "password": "WrongPassword!"
    })
    assert bad_login.status_code == 401

    # 4. Duplicate username rejected
    dup_res = client.post("/api/auth/register", json=reg_payload, headers=op_headers)
    assert dup_res.status_code == 400

def test_password_hashing_security():
    from app.core.security import hash_password, verify_password
    pw = "SuperSecretTacticalKey456!"
    h1 = hash_password(pw)
    h2 = hash_password(pw)

    # Different salts must generate different hashes
    assert h1 != h2
    assert h1.startswith("pbkdf2_sha256$")
    assert verify_password(pw, h1) is True
    assert verify_password(pw, h2) is True
    assert verify_password("WrongKey", h1) is False
