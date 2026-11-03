"""add multi tenant organization isolation

Revision ID: 5d8e2a10b42f
Revises: 3b9a1f28e05c
Create Date: 2026-11-03 19:20:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '5d8e2a10b42f'
down_revision: Union[str, Sequence[str], None] = '3b9a1f28e05c'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


TABLES = ["users", "incidents", "responders", "teams", "assignments", "devices"]


def upgrade() -> None:
    for table in TABLES:
        try:
            op.add_column(
                table,
                sa.Column("organization_id", sa.String(length=64), nullable=False, server_default="org-default")
            )
            op.create_index(f"ix_{table}_organization_id", table, ["organization_id"])
        except Exception:
            # Handles idempotent reruns or SQLite schemas
            pass


def downgrade() -> None:
    for table in reversed(TABLES):
        try:
            op.drop_index(f"ix_{table}_organization_id", table_name=table)
            op.drop_column(table, "organization_id")
        except Exception:
            pass
