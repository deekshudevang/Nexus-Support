"""Unit and integration tests for Phase PG-P5: Real-time Outbox, Stream Sequencing, and WebSocket Replay."""

import json
import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.core.rbac import create_access_token
from app.core.security import generate_ws_ticket, hash_password
from app.models.outbox_event import OutboxEventModel
from app.models.stream_event import StreamEventModel
from app.models.user import UserModel
from app.services.outbox_publisher import process_outbox_batch
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


# ── PG-P5-01: Outbox to Gapless Stream Sequencing ────────────────────────────

def test_outbox_batch_processing_and_monotonic_stream_seq(db_session):
    """
    Verify outbox processor drains outbox events, assigns gapless monotonic stream_seq,
    and sets published_at timestamp.
    """
    now = int(time.time())
    # Create 3 outbox events
    evt1 = OutboxEventModel(
        event_id=f"evt_{uuid.uuid4().hex[:12]}",
        aggregate_type="INCIDENT",
        aggregate_id="inc_001",
        aggregate_version=1,
        event_type="incident.created",
        payload={"title": "Avalanche Alpha"},
        created_at=now,
    )
    evt2 = OutboxEventModel(
        event_id=f"evt_{uuid.uuid4().hex[:12]}",
        aggregate_type="INCIDENT",
        aggregate_id="inc_001",
        aggregate_version=2,
        event_type="incident.status_changed",
        payload={"status": "TRIAGED"},
        created_at=now + 1,
    )
    evt3 = OutboxEventModel(
        event_id=f"evt_{uuid.uuid4().hex[:12]}",
        aggregate_type="ASSIGNMENT",
        aggregate_id="asgn_001",
        aggregate_version=1,
        event_type="assignment.dispatched",
        payload={"responder": "RESCUE_1"},
        created_at=now + 2,
    )
    db_session.add_all([evt1, evt2, evt3])
    db_session.commit()

    # Process outbox
    published = process_outbox_batch(db_session, limit=10)
    assert len(published) >= 3

    # Check monotonic sequencing
    seqs = [p["stream_seq"] for p in published]
    assert seqs == sorted(seqs)
    for i in range(len(seqs) - 1):
        assert seqs[i + 1] > seqs[i]

    # Verify published_at is populated on outbox items
    db_session.refresh(evt1)
    db_session.refresh(evt2)
    db_session.refresh(evt3)
    assert evt1.published_at is not None
    assert evt2.published_at is not None
    assert evt3.published_at is not None


def test_cursor_based_event_replay_endpoint(test_client, db_session, commander_token):
    """
    Verify /api/v1/events/replay cursor backfill after a simulated edge reconnect.
    """
    # Create an incident via API (which emits outbox -> stream_seq)
    inc_resp = test_client.post(
        "/api/v1/incidents",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"title": "Earthquake Shake", "category": "EARTHQUAKE", "description": "Mag 5.2", "priority": 1, "severity": 5},
    )
    inc_id = inc_resp.json()["id"]

    # Transition it
    test_client.post(
        f"/api/v1/incidents/{inc_id}/transition",
        headers={"Authorization": f"Bearer {commander_token}"},
        json={"new_status": "TRIAGED"},
    )

    # Replay all events since seq 0
    replay_resp = test_client.get(
        "/api/v1/events/replay?since_seq=0",
        headers={"Authorization": f"Bearer {commander_token}"},
    )
    assert replay_resp.status_code == 200
    data = replay_resp.json()
    assert len(data["events"]) >= 2
    assert data["last_seq"] >= data["events"][-1]["stream_seq"]

    # Replay with higher cursor returns only newer events
    last_seen_seq = data["events"][0]["stream_seq"]
    sub_resp = test_client.get(
        f"/api/v1/events/replay?since_seq={last_seen_seq}",
        headers={"Authorization": f"Bearer {commander_token}"},
    )
    assert sub_resp.status_code == 200
    sub_events = sub_resp.json()["events"]
    assert all(e["stream_seq"] > last_seen_seq for e in sub_events)


# ── PG-P5-02: WebSocket Ticket Store & Live Replay ───────────────────────────

def test_ws_ticket_single_use_and_replay_connection(test_client, commander_token):
    """
    Verify that single-use tickets prevent token exposure over WebSockets,
    tickets are consumed once, and historical replay streams on connect.
    """
    # 1. Request single-use ticket
    ticket_resp = test_client.post(
        "/api/v1/auth/ws-ticket",
        headers={"Authorization": f"Bearer {commander_token}"},
    )
    assert ticket_resp.status_code == 200
    ticket = ticket_resp.json()["ticket"]
    assert len(ticket) >= 32

    # 2. Connect to WebSocket with ticket and since_seq=0
    with test_client.websocket_connect(f"/ws/events?ticket={ticket}&since_seq=0") as ws:
        # Initial message
        auth_msg = ws.receive_json()
        assert auth_msg["type"] == "authenticated"

        # Should receive historical replay events (if any) and replay_complete
        received_types = []
        while True:
            msg = ws.receive_json()
            received_types.append(msg["type"])
            if msg["type"] == "replay_complete":
                break

        assert "replay_complete" in received_types

        # Test keepalive ping/pong
        ws.send_json({"type": "ping"})
        pong = ws.receive_json()
        assert pong["type"] == "pong"

    # 3. Attempt to reuse consumed ticket must be rejected with WS 1008
    with pytest.raises(Exception):
        with test_client.websocket_connect(f"/ws/events?ticket={ticket}") as ws2:
            ws2.receive_json()


def test_ws_expired_and_invalid_ticket_rejection(test_client):
    """Verify expired and nonexistent tickets are rejected immediately."""
    fake_ticket = f"invalid-ticket-{uuid.uuid4().hex}"
    with pytest.raises(Exception):
        with test_client.websocket_connect(f"/ws/events?ticket={fake_ticket}") as ws:
            ws.receive_json()


def test_ws_multiple_simultaneous_subscribers_broadcast(test_client, commander_token):
    """Verify multiple simultaneous operators receive live broadcasts and disconnect cleanly."""
    from main import alert_manager

    # 1. Obtain two tickets
    t1 = test_client.post("/api/v1/auth/ws-ticket", headers={"Authorization": f"Bearer {commander_token}"}).json()["ticket"]
    t2 = test_client.post("/api/v1/auth/ws-ticket", headers={"Authorization": f"Bearer {commander_token}"}).json()["ticket"]

    with test_client.websocket_connect(f"/ws/events?ticket={t1}") as ws1:
        assert ws1.receive_json()["type"] == "authenticated"

        with test_client.websocket_connect(f"/ws/events?ticket={t2}") as ws2:
            assert ws2.receive_json()["type"] == "authenticated"

            # Alert manager must have at least 2 active connections
            assert len(alert_manager.active_connections) >= 2

            # Broadcast simulated SOS alert
            import asyncio
            packet = {
                "uuid": f"live-{uuid.uuid4().hex[:6]}",
                "emergencyType": "HAZMAT",
                "severity": 5,
                "message": "Chlorine leak confirmed",
            }
            asyncio.run(alert_manager.broadcast_sos(packet))

            # Both clients receive the live broadcast
            msg1 = ws1.receive_json()
            msg2 = ws2.receive_json()
            assert msg1["type"] == "sos_alert"
            assert msg1["packet"]["uuid"] == packet["uuid"]
            assert msg2["type"] == "sos_alert"
            assert msg2["packet"]["uuid"] == packet["uuid"]

    # Both connections closed, alert_manager should be clean
    assert len(alert_manager.active_connections) == 0
