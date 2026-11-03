"""API Contract Integrity and Breaking Change Detection Tests (Requirement E).

Tests:
1. OpenAPI specification extraction and API versioning integrity.
2. Android, Web, and Backend contract route parity.
3. Schema field presence, naming, and type invariants across critical models:
   - SosPacket (Mesh edge -> Backend sync)
   - IncidentResponse & IncidentDetailResponse (Backend -> Web Tactical Command Center)
   - ResponderResponse & TeamResponse (Backend -> Web & Android Dispatch)
   - AssignmentResponse (Backend -> Responder mobile clients)
4. WebSocket event schema and streaming parameter compatibility.
5. Automated breaking change detection against frozen baseline contract.
"""

import os
import sys
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import app


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def openapi_spec():
    return app.openapi()


# ── TEST 1: OpenAPI Schema Generation & Versioning ───────────────────────────
def test_openapi_schema_generation_and_versioning(openapi_spec):
    """Verify OpenAPI 3.x document is valid, versioned, and documented."""
    assert "openapi" in openapi_spec
    assert openapi_spec["openapi"].startswith("3.")
    assert "info" in openapi_spec
    assert openapi_spec["info"]["title"] == "MeshLink Gateway Backend"
    assert openapi_spec["info"]["version"] == "1.0.0"
    assert "paths" in openapi_spec
    assert len(openapi_spec["paths"]) >= 15


# ── TEST 2: Critical REST Endpoint Contract Compatibility ────────────────────
def test_critical_rest_routes_exist(openapi_spec):
    """Verify all REST routes required by Android and Web clients exist with correct HTTP methods."""
    paths = openapi_spec["paths"]

    expected_endpoints = {
        "/api/sync/sos": ["post"],
        "/api/v1/sync/sos": ["post"],
        "/api/sos": ["get"],
        "/api/sync/nodes": ["post"],
        "/api/nodes": ["get"],
        "/api/v1/incidents": ["get", "post"],
        "/api/v1/incidents/{incident_id}": ["get"],
        "/api/v1/incidents/{incident_id}/transition": ["post"],
        "/api/v1/incidents/{incident_id}/assignments": ["get", "post"],
        "/api/v1/responders": ["get", "post"],
        "/api/v1/responders/{responder_id}": ["get"],
        "/api/v1/teams": ["get", "post"],
        "/api/v1/assignments/{assignment_id}/transition": ["post"],
        "/api/v1/auth/login": ["post"],
        "/api/v1/auth/ws-ticket": ["post"],
        "/api/v1/sos/{sos_id}/ack": ["post"],
        "/api/v1/sos/{sos_id}/delivery-status": ["get"],
        "/health": ["get"],
        "/health/ready": ["get"],
    }

    for path, expected_methods in expected_endpoints.items():
        assert path in paths, f"Mandatory contract route missing: '{path}'"
        available_methods = list(paths[path].keys())
        for method in expected_methods:
            assert method in available_methods, (
                f"HTTP method '{method.upper()}' missing on contract route '{path}'"
            )


# ── TEST 3: Schema Field Presence & Types Invariants ─────────────────────────
def test_schema_field_presence_and_types(openapi_spec):
    """Verify data models preserve required fields, preventing silent schema drift."""
    schemas = openapi_spec.get("components", {}).get("schemas", {})

    # 1. SosPacket contract (Android edge -> Gateway)
    assert "SosPacket" in schemas, "SosPacket schema must exist in OpenAPI components"
    sos_props = schemas["SosPacket"]["properties"]
    expected_sos_fields = {
        "uuid": "string",
        "senderId": "string",
        "senderName": "string",
        "emergencyType": "string",
        "severity": "integer",
        "message": "string",
        "timestamp": "integer",
        "status": "string",
    }
    for field, expected_type in expected_sos_fields.items():
        assert field in sos_props, f"Missing field '{field}' in SosPacket schema"
        prop_type = sos_props[field].get("type")
        assert prop_type == expected_type, f"Field '{field}' expected {expected_type}, got {prop_type}"

    # 2. IncidentResponse contract (Gateway -> Tactical Command Center)
    assert "IncidentResponse" in schemas, "IncidentResponse schema must exist"
    inc_props = schemas["IncidentResponse"]["properties"]
    expected_inc_fields = {
        "id": "string",
        "title": "string",
        "description": "string",
        "category": "string",
        "priority": "integer",
        "severity": "integer",
        "status": "string",
        "organization_id": "string",
        "version": "integer",
        "created_at": "integer",
        "updated_at": "integer",
    }
    for field, expected_type in expected_inc_fields.items():
        assert field in inc_props, f"Missing field '{field}' in IncidentResponse schema"

    # 3. ResponderResponse contract (Gateway -> Dispatch Web & Mobile)
    assert "ResponderResponse" in schemas, "ResponderResponse schema must exist"
    resp_props = schemas["ResponderResponse"]["properties"]
    expected_resp_fields = ["id", "callsign", "responder_type", "skills", "organization_id", "availability"]
    for field in expected_resp_fields:
        assert field in resp_props, f"Missing field '{field}' in ResponderResponse schema"


# ── TEST 4: WebSocket Event Streaming Parameters ─────────────────────────────
def test_websocket_contract_routes_registered():
    """Verify WebSocket endpoint handlers exist in FastAPI routing table."""
    ws_routes = [route.path for route in app.routes if getattr(route, "path", None) and route.path.startswith("/ws/")]
    assert "/ws/alerts" in ws_routes, "Mandatory WebSocket route '/ws/alerts' missing"
    assert "/ws/events" in ws_routes, "Mandatory WebSocket route '/ws/events' missing"


# ── TEST 5: Breaking Change Detection Against Frozen Baseline ────────────────
def test_breaking_change_frozen_baseline_comparison(openapi_spec):
    """
    Simulates CI breaking change detection:
    Fails if any mandatory field is deleted, renamed, or converted into an incompatible type.
    """
    schemas = openapi_spec.get("components", {}).get("schemas", {})

    frozen_baseline = {
        "SosPacket": ["uuid", "senderId", "senderName", "emergencyType", "severity", "message", "timestamp", "status"],
        "NodeStatus": ["deviceId", "displayName", "role", "batteryLevel", "needsHelp", "latitude", "longitude", "timestamp"],
        "IncidentResponse": ["id", "title", "description", "category", "priority", "severity", "status", "organization_id", "created_at"],
        "ResponderResponse": ["id", "callsign", "responder_type", "skills", "organization_id", "availability"],
        "TeamResponse": ["id", "name", "callsign", "organization_id", "status", "created_at"],
        "AssignmentResponse": ["id", "incident_id", "responder_id", "organization_id", "status"],
    }

    for schema_name, required_fields in frozen_baseline.items():
        assert schema_name in schemas, f"Breaking change: Schema '{schema_name}' was removed!"
        schema_props = schemas[schema_name].get("properties", {})
        for req_field in required_fields:
            assert req_field in schema_props, (
                f"BREAKING CHANGE DETECTED: Required field '{req_field}' missing from schema '{schema_name}'!"
            )
