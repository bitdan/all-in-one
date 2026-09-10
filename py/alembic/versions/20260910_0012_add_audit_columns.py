"""add common audit columns

Revision ID: 20260910_0012
Revises: 20260618_0011
Create Date: 2026-09-10 00:00:00.000000
"""

from alembic import op
import sqlalchemy as sa


revision = "20260910_0012"
down_revision = "20260618_0011"
branch_labels = None
depends_on = None


AUDITED_TABLES = (
    "sys_users",
    "post_posts",
    "post_comments",
    "post_likes",
    "post_post_tags",
    "agent_runs",
    "agent_tool_calls",
    "agent_feedback",
    "agent_eval_cases",
    "agent_eval_results",
    "market_review_runs",
    "market_limit_up_pool",
    "market_sector_strength",
    "market_candidate_pool",
    "market_review_signal",
    "market_stock_kline_daily",
    "market_stock_kline_intraday",
    "market_stock_universe",
    "market_radar_sector_snapshot",
    "market_radar_candidate_snapshot",
)

MISSING_UPDATED_AT_TABLES = (
    "post_likes",
    "agent_runs",
    "agent_tool_calls",
    "agent_feedback",
    "agent_eval_results",
)


def upgrade() -> None:
    for table_name in MISSING_UPDATED_AT_TABLES:
        op.add_column(
            table_name,
            sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False),
        )

    op.add_column(
        "post_post_tags",
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False),
    )
    op.add_column(
        "post_post_tags",
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("NOW()"), nullable=False),
    )

    for table_name in AUDITED_TABLES:
        op.add_column(
            table_name,
            sa.Column("created_by", sa.String(length=128), server_default=sa.text("'system'"), nullable=False),
        )
        op.add_column(
            table_name,
            sa.Column("updated_by", sa.String(length=128), server_default=sa.text("'system'"), nullable=False),
        )


def downgrade() -> None:
    for table_name in reversed(AUDITED_TABLES):
        op.drop_column(table_name, "updated_by")
        op.drop_column(table_name, "created_by")

    op.drop_column("post_post_tags", "updated_at")
    op.drop_column("post_post_tags", "created_at")

    for table_name in reversed(MISSING_UPDATED_AT_TABLES):
        op.drop_column(table_name, "updated_at")
