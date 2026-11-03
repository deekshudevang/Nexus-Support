"""
Contract Freeze Test Suite for Legacy Mobile and Operator Endpoints.
Ensures that any new PostgreSQL/Alembic migration or refactor keeps 100% backward
compatibility with the existing field clients and web operations console.
"""
import os
import tempfile
import pytest

os.environ["ENVIRONMENT"] = "development"
os.environ["GATEWAY_API_KEYS"] = "gw_key_valid_1,meshlink_gateway_dev_secret"
os.environ["OPERATOR_TOKENS"] = "op_token_valid_1,demo"

from fastapi.testclient import TestClient
import main

@pytest.fixture(autouse=True)
def setup_isolated_db():
    temp_dir = tempfile.mkdtemp()
    test_db = os.path.join(temp_dir, "test_legacy_contract.db")
    orig_db = main.DATABASE_PATH
    main.DATABASE_PATH = test_db
    main.init_db(test_db)
    try:
        yield
    finally:
        main.DATABASE_PATH = orig_db
        if os.path.exists(test_db):
            try:
                os.remove(test_db)
            except Exception:
                pass

@pytest.fixture
def client():
    return TestClient(main.app)

def test_legacy_health_contract(client):
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert "status" in data
    assert data["status"] == "ok"
    assert "environment" in data
    assert "timestamp" in data

def test_legacy_sos_sync_and_get_contract(client):
    gw_headers = {"X-Gateway-Key": "gw_key_valid_1"}
    op_headers = {"Authorization": "Bearer op_token_valid_1"}

    payload = [{
        "uuid": "contract-test-uuid-001",
        "senderId": "node-contract-1",
        "senderName": "Alpha Lead",
        "emergencyType": "MEDICAL",
        "severity": 7,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "message": "Contract compliance verified",
        "timestamp": 1700000000,
        "status": "PENDING",
        "hopCount": 1
    }]

    # Ingestion contract
    post_res = client.post("/api/sync/sos", json=payload, headers=gw_headers)
    assert post_res.status_code == 200
    post_data = post_res.json()
    assert post_data["status"] == "success"
    assert post_data["synced_count"] == 1
    assert "receipts" in post_data
    assert post_data["receipts"][0]["uuid"] == "contract-test-uuid-001"

    # Query contract
    get_res = client.get("/api/sos", headers=op_headers)
    assert get_res.status_code == 200
    items = get_res.json()
    assert isinstance(items, list)
    item = next((i for i in items if i["uuid"] == "contract-test-uuid-001"), None)
    assert item is not None
    assert item["senderId"] == "node-contract-1"
    assert item["severity"] == 7

def test_legacy_node_sync_and_get_contract(client):
    gw_headers = {"X-Gateway-Key": "gw_key_valid_1"}
    op_headers = {"Authorization": "Bearer op_token_valid_1"}

    nodes_payload = [{
        "deviceId": "contract-node-001",
        "displayName": "Relay 1",
        "role": "REPEATER",
        "batteryLevel": 92,
        "needsHelp": False,
        "latitude": 37.77,
        "longitude": -122.41,
        "timestamp": 1700000000
    }]

    post_res = client.post("/api/sync/nodes", json=nodes_payload, headers=gw_headers)
    assert post_res.status_code == 200
    assert post_res.json()["status"] == "success"

    get_res = client.get("/api/nodes", headers=op_headers)
    assert get_res.status_code == 200
    items = get_res.json()
    assert isinstance(items, list)
    item = next((i for i in items if i["deviceId"] == "contract-node-001"), None)
    assert item is not None
    assert item["batteryLevel"] == 92

def test_legacy_auth_and_ws_ticket_contract(client):
    op_headers = {"Authorization": "Bearer op_token_valid_1"}

    ticket_res = client.post("/api/auth/ws-ticket", headers=op_headers)
    assert ticket_res.status_code == 200
    ticket_data = ticket_res.json()
    assert "ticket" in ticket_data
    assert ticket_data["expires_in"] == 30

    login_res = client.post("/api/auth/token", json={"username": "admin", "password": "op_token_valid_1"})
    assert login_res.status_code == 200
    login_data = login_res.json()
    assert "access_token" in login_data
    assert login_data["role"] in ["operator", "admin"]
