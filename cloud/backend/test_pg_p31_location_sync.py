"""Verification of Location Sync Endpoints and Telemetry Convergence (Task 4 / IMP-CRIT-03).

Proves that:
1. Unauthenticated requests to POST/GET /api/v1/sync/location are rejected with HTTP 401.
2. POST /api/v1/sync/location ingests LocationEventEntity batches and returns SyncResponse ({"success": True, "uploadedCount": N}).
3. Responders registered with matching peerId/deviceId have their real-time location telemetry updated (last_latitude, last_longitude, location_accuracy_m, last_location_at).
4. GET /api/v1/sync/location returns location records matching the Android entity contract, with proper sinceTimestamp filtering.
5. Location event ingestion is idempotent (re-uploading the same eventId updates rather than duplicates).
"""

import os
import sys
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import Base, SessionLocal, engine
from app.models.responder import ResponderModel
from app.models.location_event import LocationEventModel
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

def test_location_sync_rejects_unauthenticated(client):
    # Missing credentials
    res_post = client.post("/api/v1/sync/location", json=[])
    assert res_post.status_code == 401

    res_get = client.get("/api/v1/sync/location")
    assert res_get.status_code == 401

    # Invalid credentials
    res_invalid = client.post(
        "/api/v1/sync/location",
        headers={"X-Gateway-Key": "wrong-secret"},
        json=[]
    )
    assert res_invalid.status_code == 401

def test_location_sync_upload_and_responder_telemetry_convergence(client, db, gw_headers):
    unique_suffix = uuid.uuid4().hex[:8]
    peer_id = f"node-telemetry-{unique_suffix}"
    resp_id = f"resp-telemetry-{unique_suffix}"
    event_id = f"evt-loc-{unique_suffix}"
    now_ts = int(time.time() * 1000)

    # 1. Seed a responder linked to this device
    responder = ResponderModel(
        id=resp_id,
        callsign="SAR-BRAVO-1",
        device_id=peer_id,
        availability="ACTIVE",
        organization_id="org-default",
        responder_type="SAR",
        created_at=int(time.time()),
    )
    db.add(responder)
    db.commit()

    # 2. Upload location event via POST /api/v1/sync/location
    payload = [
        {
            "eventId": event_id,
            "peerId": peer_id,
            "latitude": 37.7749,
            "longitude": -122.4194,
            "accuracy": 4.5,
            "timestamp": now_ts,
            "sequenceNumber": 1,
            "signature": "sig-hex-test",
            "publicKey": "pk-hex-test",
            "syncStatus": 0
        }
    ]

    res = client.post("/api/v1/sync/location", headers=gw_headers, json=payload)
    assert res.status_code == 200, res.text
    body = res.json()
    # Contract matches Android SyncResponse(success: Boolean, uploadedCount: Int)
    assert body["success"] is True
    assert body["uploadedCount"] == 1

    # 3. Verify record was stored in location_events
    db.expire_all()
    loc_record = db.query(LocationEventModel).filter(LocationEventModel.event_id == event_id).first()
    assert loc_record is not None
    assert loc_record.peer_id == peer_id
    assert abs(loc_record.latitude - 37.7749) < 1e-4
    assert abs(loc_record.longitude - (-122.4194)) < 1e-4
    assert abs(loc_record.accuracy - 4.5) < 1e-4

    # 4. Verify responder coordinates were updated in real time
    updated_resp = db.query(ResponderModel).filter(ResponderModel.id == resp_id).first()
    assert updated_resp is not None
    assert abs(updated_resp.last_latitude - 37.7749) < 1e-4
    assert abs(updated_resp.last_longitude - (-122.4194)) < 1e-4
    assert abs(updated_resp.location_accuracy_m - 4.5) < 1e-4
    assert updated_resp.last_location_at == now_ts

def test_location_sync_fetch_with_since_timestamp_filtering(client, db, gw_headers):
    unique_suffix = uuid.uuid4().hex[:8]
    peer_id = f"node-filter-{unique_suffix}"
    evt1 = f"evt-old-{unique_suffix}"
    evt2 = f"evt-new-{unique_suffix}"

    ts_old = 1000000
    ts_new = 2000000

    payload = [
        {
            "eventId": evt1,
            "peerId": peer_id,
            "latitude": 34.05,
            "longitude": -118.25,
            "accuracy": 10.0,
            "timestamp": ts_old,
            "sequenceNumber": 1,
            "signature": "",
            "publicKey": "",
            "syncStatus": 0
        },
        {
            "eventId": evt2,
            "peerId": peer_id,
            "latitude": 34.06,
            "longitude": -118.26,
            "accuracy": 5.0,
            "timestamp": ts_new,
            "sequenceNumber": 2,
            "signature": "",
            "publicKey": "",
            "syncStatus": 0
        }
    ]

    upload_res = client.post("/api/v1/sync/location", headers=gw_headers, json=payload)
    assert upload_res.status_code == 200
    assert upload_res.json()["uploadedCount"] == 2

    # Query all events since 0
    fetch_all = client.get("/api/v1/sync/location?sinceTimestamp=0", headers=gw_headers)
    assert fetch_all.status_code == 200
    all_events = fetch_all.json()
    event_ids = [e["eventId"] for e in all_events]
    assert evt1 in event_ids
    assert evt2 in event_ids

    # Query filtered events since ts_old (should only contain evt2)
    fetch_filtered = client.get(f"/api/v1/sync/location?sinceTimestamp={ts_old}", headers=gw_headers)
    assert fetch_filtered.status_code == 200
    filtered_events = fetch_filtered.json()
    filtered_ids = [e["eventId"] for e in filtered_events]
    assert evt1 not in filtered_ids
    assert evt2 in filtered_ids

    # Verify serialization matches Android LocationEventEntity fields
    sample = next(e for e in filtered_events if e["eventId"] == evt2)
    assert "eventId" in sample
    assert "peerId" in sample
    assert "latitude" in sample
    assert "longitude" in sample
    assert "accuracy" in sample
    assert "timestamp" in sample
    assert "sequenceNumber" in sample
    assert "signature" in sample
    assert "publicKey" in sample
    assert "syncStatus" in sample

def test_location_sync_idempotent_reupload(client, db, gw_headers):
    unique_suffix = uuid.uuid4().hex[:8]
    event_id = f"evt-idempotent-{unique_suffix}"
    peer_id = f"peer-idempotent-{unique_suffix}"

    initial = [
        {
            "eventId": event_id,
            "peerId": peer_id,
            "latitude": 12.0,
            "longitude": 77.0,
            "accuracy": 15.0,
            "timestamp": 1234567,
            "sequenceNumber": 1,
            "signature": "",
            "publicKey": "",
            "syncStatus": 0
        }
    ]
    res1 = client.post("/api/v1/sync/location", headers=gw_headers, json=initial)
    assert res1.status_code == 200
    assert res1.json()["uploadedCount"] == 1

    # Re-upload with updated latitude
    updated = [
        {
            "eventId": event_id,
            "peerId": peer_id,
            "latitude": 12.9716,
            "longitude": 77.5946,
            "accuracy": 2.0,
            "timestamp": 1234599,
            "sequenceNumber": 2,
            "signature": "",
            "publicKey": "",
            "syncStatus": 0
        }
    ]
    res2 = client.post("/api/v1/sync/location", headers=gw_headers, json=updated)
    assert res2.status_code == 200
    assert res2.json()["uploadedCount"] == 1

    # Ensure single record exists with updated values
    db.expire_all()
    records = db.query(LocationEventModel).filter(LocationEventModel.event_id == event_id).all()
    assert len(records) == 1
    assert abs(records[0].latitude - 12.9716) < 1e-4
    assert abs(records[0].longitude - 77.5946) < 1e-4
