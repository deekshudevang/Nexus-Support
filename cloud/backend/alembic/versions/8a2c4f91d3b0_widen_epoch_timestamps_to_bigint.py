"""widen epoch timestamps to bigint for millisecond ingestion

Revision ID: 8a2c4f91d3b0
Revises: 6f1a8b92c431
Create Date: 2026-11-28 11:05:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '8a2c4f91d3b0'
down_revision: Union[str, Sequence[str], None] = '6f1a8b92c431'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def _alter_int_col(table: str, col: str, nullable: bool, to_bigint: bool) -> None:
    target_type = sa.BigInteger() if to_bigint else sa.Integer()
    existing_type = sa.Integer() if to_bigint else sa.BigInteger()
    with op.batch_alter_table(table) as batch_op:
        batch_op.alter_column(
            col,
            existing_type=existing_type,
            type_=target_type,
            existing_nullable=nullable,
        )


def upgrade() -> None:
    _alter_int_col("sos_packets", "timestamp", nullable=False, to_bigint=True)
    _alter_int_col("node_status", "timestamp", nullable=False, to_bigint=True)
    _alter_int_col("incidents", "created_at", nullable=False, to_bigint=True)
    _alter_int_col("incidents", "updated_at", nullable=False, to_bigint=True)
    _alter_int_col("incidents", "resolved_at", nullable=True, to_bigint=True)
    _alter_int_col("responders", "last_location_at", nullable=True, to_bigint=True)
    _alter_int_col("responders", "created_at", nullable=False, to_bigint=True)


def downgrade() -> None:
    _alter_int_col("responders", "created_at", nullable=False, to_bigint=False)
    _alter_int_col("responders", "last_location_at", nullable=True, to_bigint=False)
    _alter_int_col("incidents", "resolved_at", nullable=True, to_bigint=False)
    _alter_int_col("incidents", "updated_at", nullable=False, to_bigint=False)
    _alter_int_col("incidents", "created_at", nullable=False, to_bigint=False)
    _alter_int_col("node_status", "timestamp", nullable=False, to_bigint=False)
    _alter_int_col("sos_packets", "timestamp", nullable=False, to_bigint=False)
