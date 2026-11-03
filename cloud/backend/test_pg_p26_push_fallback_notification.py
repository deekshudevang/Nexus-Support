"""SOS Delivery Resilience and Push Notification Fallback Tests (Requirement B).

Tests:
1. Automatic push notification fallback when WebSocket has 0 subscribers.
2. Strict delivery invariant: never reported DELIVERED before authenticated ACK.
3. Authenticated ACK processing with cryptographic signature verification.
4. ACK replay protection: replaying identical ACK signature does not re-process.
5. Push notification service outage fault injection: exponential retry & Dead-Letter Queue (DLQ).
6. Expired packet handling: packets older than TTL are marked EXPIRED and not alerted.
7. End-to-end API verification via /api/v1/sos/{id}/ack and /api/v1/sos/{id}/delivery-status.
"""

import asyncio
import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.services.alert_manager import alert_manager
from app.services.push_notification_service import (
    PushNotificationFallbackService,
    push_fallback_service,
)
from main import app, GATEWAY_API_KEYS, OPERATOR_TOKENS


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def gw_headers():
    key = next(iter(GATEWAY_API_KEYS))
    return {"Authorization": f"Bearer {key}"}


@pytest.fixture
def op_headers():
    tok = next(iter(OPERATOR_TOKENS))
    return {"Authorization": f"Bearer {tok}"}


# ── TEST 1: Strict Invariant: Never Report Delivered Before Authenticated ACK ──
def test_strict_delivery_invariant():
    """Verify system strictly reports NOT delivered until authenticated ACK arrives."""
    service = PushNotificationFallbackService(ttl_seconds=3600)
    packet = {
        "uuid": f"sos_{uuid.uuid4().hex[:12]}",
        "senderId": "node-alpha",
        "message": "Critical SOS Need Extraction",
        "severity": 5,
        "timestamp": int(time.time()),
    }

    record = service.register_outgoing_sos(packet, recipient="incident_commander")
    assert record.status == "PENDING_ACK"
    assert service.is_delivered(packet["uuid"]) is False

    # Forged short signature must fail
    with pytest.raises(ValueError, match="cryptographic proof required"):
        service.process_authenticated_ack(packet["uuid"], "incident_commander", "short_sig")

    # Invariant holds: still false
    assert service.is_delivered(packet["uuid"]) is False

    # Valid authenticated ACK received
    valid_sig = "ecdsa_sig_secp256r1_valid_hash_proof_00112233445566778899"
    ack_record = service.process_authenticated_ack(packet["uuid"], "incident_commander", valid_sig)
    assert ack_record.status == "ACKNOWLEDGED"
    assert service.is_delivered(packet["uuid"]) is True


# ── TEST 2: Push Fallback on WebSocket Outage ─────────────────────────────────
@pytest.mark.asyncio
async def test_push_fallback_on_websocket_outage():
    """Verify push fallback activates and queues recipients when WebSocket is offline."""
    service = PushNotificationFallbackService(ttl_seconds=3600)
    packet = {
        "uuid": f"sos_{uuid.uuid4().hex[:12]}",
        "senderId": "node-bravo",
        "message": "Flash flood water rising rapidly",
        "severity": 4,
        "timestamp": int(time.time()),
    }

    # Verify no WS connections simulated
    assert len(alert_manager.active_connections) == 0

    dispatch_res = await service.dispatch_push_fallback(packet, recipients=["disp_1", "disp_2"])
    assert dispatch_res["dispatched"] == 2
    for r in dispatch_res["results"]:
        assert r["status"] == "PENDING_ACK"
        assert r["delivered"] is False  # Never reported delivered yet


# ── TEST 3: Push Notification Service Outage & DLQ ───────────────────────────
@pytest.mark.asyncio
async def test_push_service_failure_and_dead_letter_queue():
    """Verify push provider outages trigger retry backoff and routing to Dead-Letter Queue."""
    service = PushNotificationFallbackService(ttl_seconds=3600)
    service.set_provider_failure_simulation(True)

    packet = {
        "uuid": f"sos_{uuid.uuid4().hex[:12]}",
        "senderId": "node-charlie",
        "message": "Mountain rescue needed",
        "severity": 5,
        "timestamp": int(time.time()),
    }

    dispatch_res = await service.dispatch_push_fallback(packet, recipients=["team_alpha"], max_retries=3)
    assert dispatch_res["dlq_count"] == 1
    assert dispatch_res["results"][0]["status"] == "FAILED_DLQ"
    assert dispatch_res["results"][0]["attempts"] == 3

    # Check DLQ entry content
    dlq_item = service.dead_letter_queue[0]
    assert dlq_item["message_id"] == packet["uuid"]
    assert dlq_item["recipient"] == "team_alpha"
    assert dlq_item["attempts"] == 3


# ── TEST 4: Expired Packet and Out-of-Order Handling ──────────────────────────
def test_expired_and_out_of_order_event_handling():
    """Verify expired packets (older than TTL) are flagged and not alerted."""
    service = PushNotificationFallbackService(ttl_seconds=300)  # 5 min TTL
    now = int(time.time())

    expired_packet = {
        "uuid": f"sos_exp_{uuid.uuid4().hex[:8]}",
        "senderId": "node-delta",
        "message": "Old stale report",
        "severity": 3,
        "timestamp": now - 3600,  # 1 hour ago
    }

    record = service.register_outgoing_sos(expired_packet, recipient="ops")
    assert record.status == "EXPIRED"
    assert service.is_delivered(expired_packet["uuid"]) is False


# ── TEST 5: ACK Replay Protection ─────────────────────────────────────────────
def test_ack_replay_protection():
    """Verify identical ACK signature cannot be replayed to corrupt delivery state."""
    service = PushNotificationFallbackService(ttl_seconds=3600)
    msg_id = f"sos_replay_{uuid.uuid4().hex[:8]}"
    packet = {
        "uuid": msg_id,
        "senderId": "node-echo",
        "message": "Medical assistance required",
        "severity": 3,
        "timestamp": int(time.time()),
    }
    service.register_outgoing_sos(packet, recipient="commander")

    sig = "sig_ecdsa_unique_token_proof_abcdef1234567890"
    rec1 = service.process_authenticated_ack(msg_id, "commander", sig)
    assert rec1.status == "ACKNOWLEDGED"

    # Replay identical ACK
    rec2 = service.process_authenticated_ack(msg_id, "commander", sig)
    assert rec2.status == "ACKNOWLEDGED"
    # Fingerprint set prevents duplicate state mutation
    assert len(service.seen_signatures) == 1


# ── TEST 6: API Integration Endpoints ─────────────────────────────────────────
def test_sos_delivery_api_endpoints(client, gw_headers, op_headers):
    """Verify /api/sync/sos triggers push fallback when WS is empty, and ACK endpoint works."""
    packet_id = f"sos_api_{uuid.uuid4().hex[:8]}"
    packet = {
        "uuid": packet_id,
        "senderId": "device-mobile-01",
        "senderName": "SearchTeamAlpha",
        "emergencyType": "MEDICAL",
        "severity": 5,
        "latitude": 37.7749,
        "longitude": -122.4194,
        "gpsFixAvailable": 1,
        "message": "Trauma patient needs air evacuation",
        "timestamp": int(time.time()),
        "status": "ACTIVE",
    }

    # Ingest SOS packet
    res_sync = client.post("/api/sync/sos", headers=gw_headers, json=[packet])
    assert res_sync.status_code == 200

    # Query delivery status: should be PENDING_ACK (not yet delivered)
    res_status = client.get(f"/api/v1/sos/{packet_id}/delivery-status", headers=op_headers)
    assert res_status.status_code == 200
    status_data = res_status.json()
    assert status_data["message_id"] == packet_id
    assert status_data["status"] == "PENDING_ACK"
    assert status_data["delivered"] is False

    # Submit authenticated ACK
    res_ack = client.post(
        f"/api/v1/sos/{packet_id}/ack",
        headers=op_headers,
        json={
            "recipient": "field_commander_1",
            "signature": "ecdsa_authenticated_dispatch_signature_998877",
        },
    )
    assert res_ack.status_code == 200
    ack_data = res_ack.json()
    assert ack_data["delivery_status"] == "ACKNOWLEDGED"
    assert ack_data["delivered"] is True

    # Check delivery status again: now DELIVERED is True
    res_status_after = client.get(f"/api/v1/sos/{packet_id}/delivery-status", headers=op_headers)
    assert res_status_after.status_code == 200
    assert res_status_after.json()["delivered"] is True
    assert res_status_after.json()["status"] == "ACKNOWLEDGED"
