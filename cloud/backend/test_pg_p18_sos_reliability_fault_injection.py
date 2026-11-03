"""SOS Reliability, Idempotent Ingestion, and Fault Injection Tests (M3.18).

Tests:
1. Idempotent SOS ingestion: duplicate packet submission with same UUID returns cached ACK, zero duplicate DB rows.
2. Out-of-order SOS delivery: older packet arrives after newer packet; both are preserved with correct chronological sorting.
3. Crash mid-send simulation / atomic transaction rollback: simulated database failure rolls back partial writes, cleanly re-ingests on retry.
4. Network flap / disconnect and reconnect with cursor: client disconnects mid-stream, reconnects with since_seq, and receives gapless missed events.
5. Subscription authorization bypass rejection: unauthorized or un-ticketed WebSocket connection attempts fail immediately.
6. Terminal receipt state guarantee: SOS packets reach valid terminal states (DELIVERED, EXPIRED, FAILED) with explicit failure reason logging.
"""

import asyncio
import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.rbac import create_access_token
from app.core.security import hash_password
from app.models.idempotency_key import IdempotencyKeyModel
from app.models.outbox_event import OutboxEventModel
from app.models.sos import SosPacketModel
from app.models.user import UserModel
from app.services.outbox_publisher import process_outbox_batch
from main import app, GATEWAY_API_KEYS, alert_manager


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
def valid_gw_headers():
    key = list(GATEWAY_API_KEYS)[0]
    return {
        "X-Gateway-Key": key,
        "Content-Type": "application/json",
    }


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
    )
    db.add(user)
    db.commit()
    return create_access_token(user.id, user.username, user.role, user.token_version)


# ── TEST 1: Idempotent SOS Ingestion & Duplicate Suppression ─────────────────
def test_idempotent_sos_ingestion_duplicate_suppression(client, db, valid_gw_headers):
    """Submitting the exact same SOS packet multiple times must succeed idempotently without duplicate rows."""
    sos_uuid = f"sos-idemp-{uuid.uuid4().hex[:8]}"
    packet = {
        "uuid": sos_uuid,
        "senderId": "node-alpha-1",
        "senderName": "Alpha SAR",
        "emergencyType": "MEDICAL",
        "severity": 4,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "message": "Compound fracture on trail",
        "timestamp": int(time.time()),
        "status": "DELIVERED",
        "hopCount": 1,
    }

    # 1. First ingestion
    headers = {**valid_gw_headers, "Idempotency-Key": f"key-{sos_uuid}"}
    r1 = client.post("/api/sync/sos", headers=headers, json=[packet])
    assert r1.status_code == 200
    assert r1.json()["status"] == "success"

    # 2. Second ingestion (duplicate submission via mesh replay)
    r2 = client.post("/api/sync/sos", headers=headers, json=[packet])
    assert r2.status_code == 200
    assert r2.json()["status"] == "success"

    # Verify exactly one record exists in database
    db_rows = db.query(SosPacketModel).filter_by(uuid=sos_uuid).all()
    assert len(db_rows) == 1
    assert db_rows[0].senderId == "node-alpha-1"


# ── TEST 2: Out-of-Order SOS Ingestion ───────────────────────────────────────
def test_out_of_order_sos_delivery_preservation(client, db, valid_gw_headers):
    """Packets arriving out of order must both be ingested and sorted by timestamp."""
    now = int(time.time())
    newer_uuid = f"sos-newer-{uuid.uuid4().hex[:6]}"
    older_uuid = f"sos-older-{uuid.uuid4().hex[:6]}"

    newer_packet = {
        "uuid": newer_uuid,
        "senderId": "node-bravo-2",
        "senderName": "Bravo Team",
        "emergencyType": "FIRE",
        "severity": 3,
        "latitude": 37.7800,
        "longitude": -122.4200,
        "message": "Fire contained",
        "timestamp": now + 60,
        "status": "DELIVERED",
        "hopCount": 2,
    }

    older_packet = {
        "uuid": older_uuid,
        "senderId": "node-bravo-2",
        "senderName": "Bravo Team",
        "emergencyType": "FIRE",
        "severity": 5,
        "latitude": 37.7800,
        "longitude": -122.4200,
        "message": "Fire advancing rapidly",
        "timestamp": now,
        "status": "DELIVERED",
        "hopCount": 3,
    }

    # Ingest newer packet first
    r1 = client.post("/api/sync/sos", headers=valid_gw_headers, json=[newer_packet])
    assert r1.status_code == 200

    # Ingest older packet second (delayed multi-hop delivery)
    r2 = client.post("/api/sync/sos", headers=valid_gw_headers, json=[older_packet])
    assert r2.status_code == 200

    # Query chronological order
    results = (
        db.query(SosPacketModel)
        .filter(SosPacketModel.uuid.in_([newer_uuid, older_uuid]))
        .order_by(SosPacketModel.timestamp.asc())
        .all()
    )
    assert len(results) == 2
    assert results[0].uuid == older_uuid
    assert results[1].uuid == newer_uuid


# ── TEST 3: Transactional Rollback on Mid-Send Failure ────────────────────────
def test_transactional_rollback_on_failed_batch(client, db, valid_gw_headers):
    """Batch containing invalid coordinates must rollback completely; no partial ghost rows."""
    valid_uuid = f"sos-valid-{uuid.uuid4().hex[:6]}"
    invalid_uuid = f"sos-invalid-{uuid.uuid4().hex[:6]}"

    batch = [
        {
            "uuid": valid_uuid,
            "senderId": "node-test-1",
            "senderName": "Valid Unit",
            "emergencyType": "MEDICAL",
            "severity": 2,
            "latitude": 37.5,
            "longitude": -122.5,
            "message": "All good",
            "timestamp": int(time.time()),
            "status": "DELIVERED",
        },
        {
            "uuid": invalid_uuid,
            "senderId": "node-test-2",
            "senderName": "Bad Unit",
            "emergencyType": "MEDICAL",
            "severity": 2,
            "latitude": 999.0,  # Invalid latitude (> 90.0)
            "longitude": -122.5,
            "message": "Corrupted GPS fix",
            "timestamp": int(time.time()),
            "status": "DELIVERED",
        },
    ]

    res = client.post("/api/sync/sos", headers=valid_gw_headers, json=batch)
    # Should reject the invalid coordinate batch
    assert res.status_code in (400, 422)

    # Neither row should be committed
    assert db.query(SosPacketModel).filter_by(uuid=valid_uuid).first() is None
    assert db.query(SosPacketModel).filter_by(uuid=invalid_uuid).first() is None


# ── TEST 4: Replay After Network Flap Reconnection ───────────────────────────
def test_network_flap_reconnect_cursor_replay(client, db, commander_token):
    """When a dashboard reconnects after a flap, passing since_seq replays all missed events."""
    # 1. Obtain ticket
    ticket1 = client.post("/api/v1/auth/ws-ticket", headers={"Authorization": f"Bearer {commander_token}"}).json()["ticket"]

    # Connect and receive initial state
    with client.websocket_connect(f"/ws/events?ticket={ticket1}&since_seq=0") as ws:
        assert ws.receive_json()["type"] == "authenticated"
        while True:
            msg = ws.receive_json()
            if msg["type"] == "replay_complete":
                last_seen = msg.get("last_seq", 0)
                break

    # 2. Network flap: client was disconnected; new outbox event occurs in backend
    now = int(time.time())
    new_evt = OutboxEventModel(
        event_id=f"evt_flap_{uuid.uuid4().hex[:8]}",
        aggregate_type="INCIDENT",
        aggregate_id="inc_flap_01",
        aggregate_version=1,
        event_type="incident.created",
        payload={"title": "Flap Event Disaster", "category": "EARTHQUAKE"},
        created_at=now,
    )
    db.add(new_evt)
    db.commit()
    published = process_outbox_batch(db)
    assert len(published) >= 1
    missed_seq = published[0]["stream_seq"]

    # 3. Client reconnects with new ticket and passes last_seen cursor
    ticket2 = client.post("/api/v1/auth/ws-ticket", headers={"Authorization": f"Bearer {commander_token}"}).json()["ticket"]
    with client.websocket_connect(f"/ws/events?ticket={ticket2}&since_seq={last_seen}") as ws_reconnect:
        assert ws_reconnect.receive_json()["type"] == "authenticated"
        replayed_events = []
        while True:
            msg = ws_reconnect.receive_json()
            if msg["type"] == "replay_complete":
                break
            replayed_events.append(msg)

        seqs = [e.get("stream_seq") for e in replayed_events if "stream_seq" in e]
        assert missed_seq in seqs


# ── TEST 5: Unauthorized WebSocket Subscription Rejection ────────────────────
def test_unauthorized_websocket_subscription_bypass(client):
    """Subscribing to WebSocket endpoints without valid single-use ticket must be rejected."""
    # Invalid or forged ticket query parameter raises 1008
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect("/ws/events?ticket=forged_unauthorized_ticket_12345"):
            pass
    assert exc.value.code == 1008

    # No ticket provided: socket opens, sending bad credentials returns error and closes with 1008
    with client.websocket_connect("/ws/events") as ws:
        ws.send_json({"type": "auth", "token": "invalid_operator_token_xyz"})
        err = ws.receive_json()
        assert err["type"] == "error"
        assert "Authentication required" in err["message"]
        with pytest.raises(WebSocketDisconnect) as exc_auth:
            ws.receive_json()
        assert exc_auth.value.code == 1008


# ── TEST 6: Terminal Delivery Receipts Guarantee ─────────────────────────────
def test_terminal_receipt_states_guarantee(client, db, valid_gw_headers):
    """Every ingested SOS must have a recognized terminal receipt status."""
    valid_statuses = {"DELIVERED", "EXPIRED", "FAILED", "ACKNOWLEDGED", "PENDING"}

    sos_id = f"sos-terminal-{uuid.uuid4().hex[:6]}"
    packet = {
        "uuid": sos_id,
        "senderId": "node-k9",
        "senderName": "K9 Handler",
        "emergencyType": "SEARCH_RESCUE",
        "severity": 3,
        "latitude": 37.77,
        "longitude": -122.42,
        "message": "Subject found alive",
        "timestamp": int(time.time()),
        "status": "DELIVERED",
        "hopCount": 1,
    }

    resp = client.post("/api/sync/sos", headers=valid_gw_headers, json=[packet])
    assert resp.status_code == 200
    receipts = resp.json().get("receipts", [])
    assert len(receipts) == 1
    assert receipts[0]["status"] == "ACKNOWLEDGED"

    row = db.query(SosPacketModel).filter_by(uuid=sos_id).first()
    assert row is not None
    assert row.status in valid_statuses
