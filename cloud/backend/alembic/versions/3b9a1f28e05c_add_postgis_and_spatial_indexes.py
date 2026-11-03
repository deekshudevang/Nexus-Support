"""add postgis and spatial indexes

Revision ID: 3b9a1f28e05c
Revises: 1366b03464eb
Create Date: 2026-11-03 18:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '3b9a1f28e05c'
down_revision: Union[str, Sequence[str], None] = '1366b03464eb'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    bind = op.get_bind()
    dialect_name = bind.dialect.name

    if dialect_name == "postgresql":
        # Enable PostGIS extension if permissions allow
        try:
            op.execute("CREATE EXTENSION IF NOT EXISTS postgis;")
        except Exception:
            pass

        # 1. Incidents Spatial Column & GIST Index
        op.execute("""
            DO $$
            BEGIN
                IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
                    ALTER TABLE incidents ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326);
                    UPDATE incidents SET geom = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
                        WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
                    CREATE INDEX IF NOT EXISTS ix_incidents_spatial_geom ON incidents USING GIST (geom);
                ELSE
                    CREATE INDEX IF NOT EXISTS ix_incidents_spatial_lat_lon ON incidents (latitude, longitude);
                END IF;
            END $$;
        """)

        # 2. SOS Packets Spatial Column & GIST Index
        op.execute("""
            DO $$
            BEGIN
                IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
                    ALTER TABLE sos_packets ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326);
                    UPDATE sos_packets SET geom = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
                        WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
                    CREATE INDEX IF NOT EXISTS ix_sos_packets_spatial_geom ON sos_packets USING GIST (geom);
                ELSE
                    CREATE INDEX IF NOT EXISTS ix_sos_packets_spatial_lat_lon ON sos_packets (latitude, longitude);
                END IF;
            END $$;
        """)

        # 3. Node Status Spatial Column & GIST Index
        op.execute("""
            DO $$
            BEGIN
                IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
                    ALTER TABLE node_status ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326);
                    UPDATE node_status SET geom = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
                        WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
                    CREATE INDEX IF NOT EXISTS ix_node_status_spatial_geom ON node_status USING GIST (geom);
                ELSE
                    CREATE INDEX IF NOT EXISTS ix_node_status_spatial_lat_lon ON node_status (latitude, longitude);
                END IF;
            END $$;
        """)

        # 4. Responders Spatial Column & GIST Index
        op.execute("""
            DO $$
            BEGIN
                IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
                    ALTER TABLE responders ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326);
                    UPDATE responders SET geom = ST_SetSRID(ST_MakePoint(last_longitude, last_latitude), 4326)
                        WHERE last_latitude IS NOT NULL AND last_longitude IS NOT NULL;
                    CREATE INDEX IF NOT EXISTS ix_responders_spatial_geom ON responders USING GIST (geom);
                ELSE
                    CREATE INDEX IF NOT EXISTS ix_responders_spatial_lat_lon ON responders (last_latitude, last_longitude);
                END IF;
            END $$;
        """)
    else:
        # SQLite / Generic dialect fallback: standard composite spatial index for bounding-box search
        op.execute("CREATE INDEX IF NOT EXISTS ix_incidents_spatial_lat_lon ON incidents (latitude, longitude);")
        op.execute("CREATE INDEX IF NOT EXISTS ix_sos_packets_spatial_lat_lon ON sos_packets (latitude, longitude);")
        op.execute("CREATE INDEX IF NOT EXISTS ix_node_status_spatial_lat_lon ON node_status (latitude, longitude);")
        op.execute("CREATE INDEX IF NOT EXISTS ix_responders_spatial_lat_lon ON responders (last_latitude, last_longitude);")


def downgrade() -> None:
    bind = op.get_bind()
    dialect_name = bind.dialect.name

    if dialect_name == "postgresql":
        op.execute("DROP INDEX IF EXISTS ix_incidents_spatial_geom;")
        op.execute("DROP INDEX IF EXISTS ix_incidents_spatial_lat_lon;")
        op.execute("ALTER TABLE incidents DROP COLUMN IF EXISTS geom;")

        op.execute("DROP INDEX IF EXISTS ix_sos_packets_spatial_geom;")
        op.execute("DROP INDEX IF EXISTS ix_sos_packets_spatial_lat_lon;")
        op.execute("ALTER TABLE sos_packets DROP COLUMN IF EXISTS geom;")

        op.execute("DROP INDEX IF EXISTS ix_node_status_spatial_geom;")
        op.execute("DROP INDEX IF EXISTS ix_node_status_spatial_lat_lon;")
        op.execute("ALTER TABLE node_status DROP COLUMN IF EXISTS geom;")

        op.execute("DROP INDEX IF EXISTS ix_responders_spatial_geom;")
        op.execute("DROP INDEX IF EXISTS ix_responders_spatial_lat_lon;")
        op.execute("ALTER TABLE responders DROP COLUMN IF EXISTS geom;")
    else:
        op.execute("DROP INDEX IF EXISTS ix_responders_spatial_lat_lon;")
        op.execute("DROP INDEX IF EXISTS ix_node_status_spatial_lat_lon;")
        op.execute("DROP INDEX IF EXISTS ix_sos_packets_spatial_lat_lon;")
        op.execute("DROP INDEX IF EXISTS ix_incidents_spatial_lat_lon;")
