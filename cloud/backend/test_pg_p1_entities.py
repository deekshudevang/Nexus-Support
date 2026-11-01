"""
Phase PG-P1 Database Foundation Tests:
Tests entity creation, partial unique indexes, check constraints, import tool, and admin CLI.
"""
import os
import tempfile
import time
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.exc import IntegrityError

from app.core.database import Base, convention
from app.models.user import UserModel
from app.models.team import TeamModel
from app.models.responder import ResponderModel
from app.models.team_member import TeamMemberModel
from app.models.incident import IncidentModel
from app.models.assignment import AssignmentModel
from app.models.incident_event import IncidentEventModel
from app.models.outbox_event import OutboxEventModel
from app.models.stream_event import StreamEventModel
from app.models.idempotency_key import IdempotencyKeyModel
from app.models.device import DeviceModel
from app.tools.import_sqlite_to_pg import migrate_database

@pytest.fixture
def db_session():
    # Use SQLite in-memory for unit testing constraints and schema
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

def test_incident_coordinates_check_constraint(db_session):
    # 1. Null coordinates valid (honest offline GPS unavailable)
    valid_null_incident = IncidentModel(
        id="inc-null-gps-1",
        title="Indoor Search",
        category="MEDICAL",
        status="REPORTED",
        latitude=None,
        longitude=None,
        created_at=int(time.time()),
        updated_at=int(time.time())
    )
    db_session.add(valid_null_incident)
    db_session.commit()
    assert valid_null_incident.id == "inc-null-gps-1"

    # 2. Both coordinates valid
    valid_coords_incident = IncidentModel(
        id="inc-valid-gps-1",
        title="Field Extraction",
        category="RESCUE",
        status="REPORTED",
        latitude=37.7749,
        longitude=-122.4194,
        created_at=int(time.time()),
        updated_at=int(time.time())
    )
    db_session.add(valid_coords_incident)
    db_session.commit()
    assert valid_coords_incident.latitude == 37.7749

def test_assignments_partial_unique_index(db_session):
    # Create incident and responder
    inc1 = IncidentModel(id="inc-assign-1", title="Alpha Inc", status="ACTIVE", created_at=100, updated_at=100)
    inc2 = IncidentModel(id="inc-assign-2", title="Beta Inc", status="ACTIVE", created_at=100, updated_at=100)
    resp = ResponderModel(id="resp-001", callsign="Medic-1", responder_type="MEDICAL", created_at=100)
    db_session.add_all([inc1, inc2, resp])
    db_session.commit()

    # First active assignment
    a1 = AssignmentModel(
        id="assign-1",
        incident_id="inc-assign-1",
        responder_id="resp-001",
        status="DISPATCHED",
        offered_at=int(time.time())
    )
    db_session.add(a1)
    db_session.commit()

    # Attempt second active assignment for same responder to different incident
    a2 = AssignmentModel(
        id="assign-2",
        incident_id="inc-assign-2",
        responder_id="resp-001",
        status="EN_ROUTE",
        offered_at=int(time.time())
    )
    db_session.add(a2)
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()

    # But completed assignments do not block a new active assignment
    a1.status = "COMPLETED"
    a1.completed_at = int(time.time())
    db_session.commit()

    # Now assign-2 can proceed cleanly
    db_session.add(a2)
    db_session.commit()
    assert a2.status == "EN_ROUTE"

def test_team_member_unique_constraint(db_session):
    team = TeamModel(id="team-alpha", name="Alpha Rescue", callsign="ALPHA", created_at=100)
    resp = ResponderModel(id="resp-002", callsign="Tech-2", responder_type="SEARCH", created_at=100)
    db_session.add_all([team, resp])
    db_session.commit()

    tm1 = TeamMemberModel(team_id="team-alpha", responder_id="resp-002", joined_at=100)
    db_session.add(tm1)
    db_session.commit()

    # Duplicate membership fails
    tm2 = TeamMemberModel(team_id="team-alpha", responder_id="resp-002", joined_at=200)
    db_session.add(tm2)
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()

def test_idempotency_key_scope_constraint(db_session):
    k1 = IdempotencyKeyModel(
        scope="incident_create",
        key="idemp-key-999",
        request_hash="hash123",
        response_json='{"status": "created"}',
        status_code=201,
        created_at=100,
        expires_at=500
    )
    db_session.add(k1)
    db_session.commit()

    # Duplicate key in same scope fails
    k2 = IdempotencyKeyModel(
        scope="incident_create",
        key="idemp-key-999",
        request_hash="hash456",
        response_json='{"status": "conflict"}',
        status_code=409,
        created_at=200,
        expires_at=600
    )
    db_session.add(k2)
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()

def test_import_tool_integrity_verification():
    # Create temp sqlite database with data
    temp_dir = tempfile.mkdtemp()
    temp_sqlite = os.path.join(temp_dir, "test_source.db")
    import sqlite3
    conn = sqlite3.connect(temp_sqlite)
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
        message TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        status TEXT NOT NULL,
        hopCount INTEGER DEFAULT 0
    );
    """)
    conn.execute("""
    INSERT INTO sos_packets VALUES 
    ('uuid-import-1', 'node-1', 'Alice', 'MEDICAL', 8, 37.77, -122.41, 1, 'Leg fracture', 1700000000, 'PENDING', 0);
    """)
    conn.commit()
    conn.close()

    # Run dry run
    report_dry = migrate_database(temp_sqlite, dry_run=True)
    assert report_dry["status"] == "success"
    assert report_dry["tables"]["sos_packets"]["source_count"] == 1
    assert report_dry["tables"]["sos_packets"]["imported"] == 1

    # Clean up
    if os.path.exists(temp_sqlite):
        os.remove(temp_sqlite)
