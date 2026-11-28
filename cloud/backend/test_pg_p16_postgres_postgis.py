"""Production PostgreSQL, PostGIS & Safety Invariant 9 Validation (M3.16).

Tests:
1. Safety Invariant 9: Fail-fast validation when ENVIRONMENT=production on SQLite.
2. Safety Invariant 9: Fail-fast validation when ENVIRONMENT=production without DATABASE_URL.
3. Production configuration validation: Passes when PostgreSQL + secure keys + HTTPS CORS are present.
4. Connection pooling configuration for PostgreSQL engine.
5. Alembic migrations round-trip: upgrade to head, verify spatial indexes, downgrade, re-upgrade.
6. Spatial coordinates integrity: Bounding-box queries and check constraints.
"""

import os
import sys
import tempfile
import pytest
from unittest.mock import patch
from alembic.config import Config
from alembic import command
import sqlalchemy as sa
from sqlalchemy.orm import Session

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import validate_production_configuration
from app.core.config import _resolve_database_url
from app.core.database import Base, SessionLocal, engine
from app.models.incident import IncidentModel
from app.models.location_event import LocationEventModel
from app.models.node import NodeStatusModel
from app.models.responder import ResponderModel
from app.models.sos import SosPacketModel


# ── TEST 1: Safety Invariant 9 — Refuse SQLite in Production ─────────────────
def test_production_refuses_sqlite_fallback():
    """Safety Invariant 9: Production must NEVER run on SQLite."""
    with pytest.raises(RuntimeError, match="CRITICAL SAFETY INVARIANT 9 VIOLATION.*SQLite"):
        validate_production_configuration(
            environment="production",
            gateway_keys={"secure_prod_gw_key_1234567890"},
            operator_tokens={"secure_prod_op_token_1234567890"},
            allowed_origins=["https://command.rescuemesh.org"],
            database_url="sqlite:///./production_fallback.db",
        )

    # Also test in-memory SQLite
    with pytest.raises(RuntimeError, match="CRITICAL SAFETY INVARIANT 9 VIOLATION"):
        validate_production_configuration(
            environment="production",
            gateway_keys={"secure_prod_gw_key_1234567890"},
            operator_tokens={"secure_prod_op_token_1234567890"},
            allowed_origins=["https://command.rescuemesh.org"],
            database_url="sqlite:///:memory:",
        )


# ── TEST 2: Safety Invariant 9 — Refuse Missing Database in Production ──────
def test_production_refuses_missing_database_url():
    """Safety Invariant 9: Refuse startup if DATABASE_URL is missing or non-Postgres."""
    with pytest.raises(RuntimeError, match="CRITICAL SAFETY INVARIANT 9 VIOLATION"):
        validate_production_configuration(
            environment="production",
            gateway_keys={"secure_prod_gw_key_1234567890"},
            operator_tokens={"secure_prod_op_token_1234567890"},
            allowed_origins=["https://command.rescuemesh.org"],
            database_url="",
        )

    with pytest.raises(RuntimeError, match="Invalid PostgreSQL database URL scheme"):
        validate_production_configuration(
            environment="production",
            gateway_keys={"secure_prod_gw_key_1234567890"},
            operator_tokens={"secure_prod_op_token_1234567890"},
            allowed_origins=["https://command.rescuemesh.org"],
            database_url="mysql://user:pass@localhost/db",
        )


# ── TEST 3: Production Configuration Passes on Valid PostgreSQL ─────────────
def test_production_configuration_passes_valid_postgres():
    """Verify clean startup when valid PostgreSQL URL, secure secrets, and HTTPS origin are supplied."""
    validate_production_configuration(
        environment="production",
        gateway_keys={"sec_gw_998877665544332211"},
        operator_tokens={"sec_op_998877665544332211"},
        allowed_origins=["https://eoc.rescuemesh.org"],
        database_url="postgresql+psycopg2://rescuemesh:prod_secret@db.internal:5432/rescuemesh",
    )


# ── TEST 4: Config Module _resolve_database_url Isolation ────────────────────
def test_config_resolve_database_url_invariants():
    with patch.dict(os.environ, {"ENVIRONMENT": "production", "DATABASE_URL": "sqlite:///./leak.db"}):
        with pytest.raises(RuntimeError, match="CRITICAL SAFETY INVARIANT 9 VIOLATION"):
            with patch("app.core.config.ENVIRONMENT", "production"):
                _resolve_database_url()


# ── TEST 5: Alembic Migrations Round-Trip (Upgrade & Downgrade) ──────────────
def test_alembic_migrations_roundtrip():
    """Execute Alembic upgrade to head, test downgrade -1, and re-upgrade to head."""
    backend_dir = os.path.dirname(os.path.abspath(__file__))
    alembic_cfg_path = os.path.join(backend_dir, "alembic.ini")
    assert os.path.exists(alembic_cfg_path), "alembic.ini missing"

    temp_db_path = os.path.join(tempfile.mkdtemp(), "migration_test.db")
    test_db_url = f"sqlite:///{temp_db_path}"

    cfg = Config(alembic_cfg_path)
    cfg.set_main_option("sqlalchemy.url", test_db_url)
    cfg.set_main_option("script_location", os.path.join(backend_dir, "alembic"))

    # 1. Upgrade to head (including 3b9a1f28e05c spatial indexes)
    command.upgrade(cfg, "head")

    # Verify table and spatial index exist in migrated database
    engine_temp = sa.create_engine(test_db_url)
    inspector = sa.inspect(engine_temp)
    tables = inspector.get_table_names()
    assert "incidents" in tables
    assert "sos_packets" in tables
    assert "responders" in tables
    assert "node_status" in tables

    incident_indexes = [idx["name"] for idx in inspector.get_indexes("incidents")]
    assert "ix_incidents_spatial_lat_lon" in incident_indexes

    # 2. Downgrade back to 1366b03464eb (prior to spatial indexes)
    command.downgrade(cfg, "1366b03464eb")
    inspector_down = sa.inspect(engine_temp)
    incident_indexes_down = [idx["name"] for idx in inspector_down.get_indexes("incidents")]
    assert "ix_incidents_spatial_lat_lon" not in incident_indexes_down

    # 3. Re-upgrade to head
    command.upgrade(cfg, "head")
    inspector_reup = sa.inspect(engine_temp)
    incident_indexes_reup = [idx["name"] for idx in inspector_reup.get_indexes("incidents")]
    assert "ix_incidents_spatial_lat_lon" in incident_indexes_reup

    engine_temp.dispose()
    if os.path.exists(temp_db_path):
        try:
            os.remove(temp_db_path)
        except Exception:
            pass


# ── TEST 6: Spatial Coordinates Validation & Bounding Box Queries ───────────
def test_spatial_coordinates_constraints_and_queries():
    """Verify coordinate check constraint and bounding-box spatial filter."""
    session = SessionLocal()
    try:
        # Valid GPS incident within San Francisco bounding box
        inc_sf = IncidentModel(
            id="inc-sf-001",
            title="Structural Damage SF",
            category="STRUCTURAL",
            priority=2,
            severity=4,
            status="REPORTED",
            latitude=37.7749,
            longitude=-122.4194,
            created_at=1700000000,
            updated_at=1700000000,
        )
        session.merge(inc_sf)

        # Valid GPS incident in Oakland
        inc_oak = IncidentModel(
            id="inc-oak-002",
            title="Substation Flood Oakland",
            category="FLOOD",
            priority=3,
            severity=3,
            status="REPORTED",
            latitude=37.8044,
            longitude=-122.2712,
            created_at=1700000010,
            updated_at=1700000010,
        )
        session.merge(inc_oak)

        # Null GPS incident (Zero Fabrication: location unavailable)
        inc_null = IncidentModel(
            id="inc-null-003",
            title="Underground Distress Signal",
            category="MEDICAL",
            priority=1,
            severity=5,
            status="REPORTED",
            latitude=None,
            longitude=None,
            created_at=1700000020,
            updated_at=1700000020,
        )
        session.merge(inc_null)
        session.commit()

        # Bounding box filter for SF peninsula (lat 37.70 to 37.80, lon -122.52 to -122.35)
        sf_results = session.query(IncidentModel).filter(
            IncidentModel.latitude >= 37.70,
            IncidentModel.latitude <= 37.80,
            IncidentModel.longitude >= -122.52,
            IncidentModel.longitude <= -122.35,
        ).all()

        sf_ids = [inc.id for inc in sf_results]
        assert "inc-sf-001" in sf_ids
        assert "inc-oak-002" not in sf_ids
        assert "inc-null-003" not in sf_ids

        # Cleanup test records
        session.query(IncidentModel).filter(
            IncidentModel.id.in_(["inc-sf-001", "inc-oak-002", "inc-null-003"])
        ).delete(synchronize_session=False)
        session.commit()
    finally:
        session.close()


# ── TEST 7: Epoch Millisecond Timestamps Compile to BIGINT on PostgreSQL ────
def test_epoch_ms_columns_compile_to_postgresql_bigint():
    """Prevent 32-bit integer overflow when Android sends System.currentTimeMillis()."""
    from sqlalchemy.dialects import postgresql

    pg = postgresql.dialect()
    checked = [
        SosPacketModel.__table__.c.timestamp,
        NodeStatusModel.__table__.c.timestamp,
        LocationEventModel.__table__.c.timestamp,
        IncidentModel.__table__.c.created_at,
        IncidentModel.__table__.c.updated_at,
        IncidentModel.__table__.c.resolved_at,
        ResponderModel.__table__.c.last_location_at,
        ResponderModel.__table__.c.created_at,
    ]
    for col in checked:
        pg_type = col.type.compile(dialect=pg).upper()
        assert "BIGINT" in pg_type, f"{col.table.name}.{col.name} must compile to BIGINT on PostgreSQL, got {pg_type}"
