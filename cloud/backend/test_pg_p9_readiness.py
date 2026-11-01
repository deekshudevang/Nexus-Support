"""Production Readiness, Health Probes, and SQLite-to-Target Migration Drill (Phase PG-P9)."""

import hashlib
import json
import os
import sqlite3
import sys
import tempfile
import time
import uuid
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import app, init_db
from app.core.database import Base, SessionLocal, engine
from app.models.sos import SosPacketModel
from app.models.node import NodeStatusModel
from app.models.user import UserModel
from app.models.outbox_event import OutboxEventModel
from app.tools.import_sqlite_to_pg import migrate_database, compute_file_checksum

@pytest.fixture(autouse=True)
def setup_isolated_env():
    temp_dir = tempfile.mkdtemp()
    test_db = os.path.join(temp_dir, "test_p9_sync.db")
    import main
    main._ip_request_history.clear()
    original_db = main.DATABASE_PATH
    main.DATABASE_PATH = test_db
    init_db(test_db)
    Base.metadata.create_all(bind=engine)
    yield
    main.DATABASE_PATH = original_db
    main._ip_request_history.clear()
    if os.path.exists(test_db):
        try:
            os.remove(test_db)
        except Exception:
            pass

@pytest.fixture
def test_client():
    return TestClient(app)

@pytest.fixture
def sample_source_sqlite():
    """Create a temporary source SQLite database mirroring historical edge data."""
    temp_dir = tempfile.mkdtemp()
    source_path = os.path.join(temp_dir, "source_historical.db")
    conn = sqlite3.connect(source_path)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("""
    CREATE TABLE sos_packets (
        uuid TEXT PRIMARY KEY,
        senderId TEXT NOT NULL,
        senderName TEXT NOT NULL,
        emergencyType TEXT NOT NULL,
        severity INTEGER NOT NULL,
        latitude REAL,
        longitude REAL,
        gpsFixAvailable INTEGER DEFAULT 1,
        message TEXT,
        timestamp INTEGER NOT NULL,
        status TEXT NOT NULL,
        hopCount INTEGER DEFAULT 0
    );
    """)
    conn.execute("""
    CREATE TABLE node_status (
        deviceId TEXT PRIMARY KEY,
        displayName TEXT NOT NULL,
        role TEXT NOT NULL,
        batteryLevel INTEGER NOT NULL,
        needsHelp INTEGER NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        timestamp INTEGER NOT NULL
    );
    """)
    conn.execute("""
    CREATE TABLE users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        email TEXT,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL,
        status TEXT NOT NULL,
        is_active INTEGER DEFAULT 1,
        last_login INTEGER,
        token_version INTEGER DEFAULT 1,
        created_at INTEGER NOT NULL
    );
    """)

    # Seed 1 packet with valid GPS, 1 packet with NULL GPS (honest telemetry)
    conn.execute("""
    INSERT INTO sos_packets VALUES (
        'sos-hist-01', 'node-alpha', 'Alpha Unit', 'FIRE', 4, 37.7749, -122.4194, 1, 'Brush fire advancing', 1700000000, 'DELIVERED', 1
    );
    """)
    conn.execute("""
    INSERT INTO sos_packets VALUES (
        'sos-hist-02', 'node-beta', 'Beta Underground', 'TRAPPED', 5, NULL, NULL, 0, 'Collapsed tunnel', 1700000010, 'PENDING', 2
    );
    """)

    # Seed 1 node status
    conn.execute("""
    INSERT INTO node_status VALUES (
        'dev-relay-1', 'Relay Mast 1', 'ROUTER', 95, 0, 37.7800, -122.4100, 1700000020
    );
    """)

    # Seed 1 user
    conn.execute("""
    INSERT INTO users (username, email, password_hash, role, status, is_active, created_at) VALUES (
        'field_officer_1', 'officer1@rescuemesh.org', 'pbkdf2:sha256:fakehash', 'operator', 'active', 1, 1700000000
    );
    """)

    conn.commit()
    conn.close()

    yield source_path

    if os.path.exists(source_path):
        try:
            os.remove(source_path)
        except Exception:
            pass


# ── TEST 1: Liveness Probe (/health) ─────────────────────────────────────────
def test_health_liveness_probe(test_client):
    res = test_client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert "environment" in data
    assert "timestamp" in data
    assert "service" in data


# ── TEST 2: Readiness Probe (/health/ready) Healthy ──────────────────────────
def test_health_readiness_probe_healthy(test_client):
    res = test_client.get("/health/ready")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "READY"
    assert data["database"] == "OK"
    assert "outbox_lag" in data
    assert isinstance(data["outbox_lag"], int)
    assert data["outbox_lag"] >= 0
    assert "database_engine" in data


# ── TEST 3: Readiness Probe (/health/ready) Degraded 503 Failure Mode ─────────
def test_health_readiness_probe_degraded_returns_503(test_client, monkeypatch):
    from unittest.mock import MagicMock
    mock_session = MagicMock()
    mock_session.execute.side_effect = Exception("Database connection pool exhausted")

    import app.core.database as db_mod
    monkeypatch.setattr(db_mod, "SessionLocal", lambda: mock_session)

    res = test_client.get("/health/ready")
    assert res.status_code == 503
    data = res.json()
    assert data["status"] == "NOT_READY"
    assert data["database"] == "ERROR"
    assert len(data["errors"]) > 0
    assert "Database connection pool exhausted" in data["errors"][0]


# ── TEST 4: SQLite-to-Target Migration Drill (Dry Run) ────────────────────────
def test_sqlite_to_target_migration_dry_run(sample_source_sqlite):
    pre_checksum = compute_file_checksum(sample_source_sqlite)

    # Execute dry-run
    report = migrate_database(sample_source_sqlite, dry_run=True)
    assert report["status"] == "success"
    assert report["dry_run"] is True
    assert report["tables"]["sos_packets"]["source_count"] == 2
    assert report["tables"]["node_status"]["source_count"] == 1
    assert report["tables"]["users"]["source_count"] == 1

    # Checksum MUST remain unaltered
    post_checksum = compute_file_checksum(sample_source_sqlite)
    assert pre_checksum == post_checksum


# ── TEST 5: SQLite-to-Target Migration Drill (Full Execution & Data Fidelity) ──
def test_sqlite_to_target_migration_actual_and_fidelity(sample_source_sqlite):
    pre_checksum = compute_file_checksum(sample_source_sqlite)

    # Execute actual migration
    report = migrate_database(sample_source_sqlite, dry_run=False)
    assert report["status"] == "success"
    assert report["dry_run"] is False

    # Checksum MUST remain identical (read-only verification)
    post_checksum = compute_file_checksum(sample_source_sqlite)
    assert pre_checksum == post_checksum

    # Verify rows in SQLAlchemy database
    session = SessionLocal()
    try:
        # Check SOS 1 (valid GPS)
        sos1 = session.query(SosPacketModel).filter_by(uuid="sos-hist-01").first()
        assert sos1 is not None
        assert sos1.emergencyType == "FIRE"
        assert sos1.gpsFixAvailable is True
        assert abs(sos1.latitude - 37.7749) < 0.0001
        assert abs(sos1.longitude - (-122.4194)) < 0.0001

        # Check SOS 2 (null GPS - zero fabrication)
        sos2 = session.query(SosPacketModel).filter_by(uuid="sos-hist-02").first()
        assert sos2 is not None
        assert sos2.emergencyType == "TRAPPED"
        assert sos2.latitude is None
        assert sos2.longitude is None
        assert sos2.gpsFixAvailable is False

        # Check Node
        node = session.query(NodeStatusModel).filter_by(deviceId="dev-relay-1").first()
        assert node is not None
        assert node.displayName == "Relay Mast 1"
        assert node.batteryLevel == 95

        # Check User
        user = session.query(UserModel).filter_by(username="field_officer_1").first()
        assert user is not None
        assert user.email == "officer1@rescuemesh.org"
    finally:
        session.close()

    # Rerun migration for idempotency verification
    rerun_report = migrate_database(sample_source_sqlite, dry_run=False)
    assert rerun_report["status"] == "success"
    assert rerun_report["tables"]["sos_packets"]["imported"] == 2
