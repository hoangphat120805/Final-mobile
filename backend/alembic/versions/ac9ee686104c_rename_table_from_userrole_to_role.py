"""rename table from userrole to role

Revision ID: ac9ee686104c
Revises: e757a0e8dc72
Create Date: 2025-07-16 23:44:48.179354

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
import sqlmodel.sql.sqltypes


# revision identifiers, used by Alembic.
revision: str = 'ac9ee686104c'
down_revision: Union[str, Sequence[str], None] = 'e757a0e8dc72'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.rename_table('userrole', 'role')
    pass


def downgrade() -> None:
    """Downgrade schema."""
    op.rename_table('role', 'userrole')
    pass
