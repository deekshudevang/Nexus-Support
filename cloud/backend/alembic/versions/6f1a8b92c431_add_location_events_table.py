"""add location events table

Revision ID: 6f1a8b92c431
Revises: 5d8e2a10b42f
Create Date: 2026-11-20 20:55:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '6f1a8b92c431'
down_revision: Union[str, Sequence[str], None] = '5d8e2a10b42f'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    try:
        op.create_table(
            "location_events",
            sa.Column("event_id", sa.String(length=128), primary_key=True),
            sa.Column("peer_id", sa.String(length=128), nullable=False),
            sa.Column("latitude", sa.Float(), nullable=False),
            sa.Column("longitude", sa.Float(), nullable=False),
            sa.Column("accuracy", sa.Float(), nullable=False, server_default="0.0"),
            sa.Column("timestamp", sa.BigInteger(), nullable=False),
            sa.Column("sequence_number", sa.Integer(), nullable=False, server_default="0"),
            sa.Column("signature", sa.String(length=512), nullable=True, server_default=""),
            sa.Column("public_key", sa.String(length=512), nullable=True, server_default=""),
            sa.Column("sync_status", sa.Integer(), nullable=False, server_default="1"),
            sa.Column("organization_id", sa.String(length=64), nullable=False, server_default="org-default"),
        )
        op.create_index("ix_location_events_event_id", "location_events", ["event_id"])
        op.create_index("ix_location_events_peer_id", "location_events", ["peer_id"])
        op.create_index("ix_location_events_timestamp", "location_events", ["timestamp"])
        op.create_index("ix_location_events_organization_id", "location_events", ["organization_id"])
        op.create_index("ix_location_events_peer_timestamp", "location_events", ["peer_id", "timestamp"])
    except Exception:
        pass


def downgrade() -> None:
    try:
        op.drop_table("location_events")
    except Exception:
        pass
