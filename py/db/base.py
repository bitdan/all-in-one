from sqlalchemy import String, text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class AuditMixin:
    """Shared creator/updater columns for persistent business records."""

    created_by: Mapped[str] = mapped_column(String(128), nullable=False, server_default=text("'system'"))
    updated_by: Mapped[str] = mapped_column(String(128), nullable=False, server_default=text("'system'"))
