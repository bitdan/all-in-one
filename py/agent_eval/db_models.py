from datetime import datetime

from db.base import AuditMixin, Base
from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Index, Integer, JSON, String, Text, text
from sqlalchemy.orm import Mapped, mapped_column


class AgentRun(AuditMixin, Base):
    __tablename__ = "agent_runs"
    __table_args__ = (
        Index("idx_agent_runs_route_created", "route", "created_at"),
        Index("idx_agent_runs_status_created", "status", "created_at"),
        Index("idx_agent_runs_prompt_version", "prompt_version", "created_at"),
        Index("idx_agent_runs_user", "user_id", "created_at"),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    trace_id: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    user_id: Mapped[str | None] = mapped_column(String(128))
    route: Mapped[str] = mapped_column(String(64), nullable=False)
    input_text: Mapped[str] = mapped_column(Text, nullable=False)
    output_text: Mapped[str] = mapped_column(Text, nullable=False, server_default=text("''"))
    structured_content: Mapped[dict | None] = mapped_column(JSON)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    latency_ms: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    steps_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    prompt_tokens: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    completion_tokens: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    total_tokens: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    estimated_cost: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    prompt_version: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("'default'"))
    model_name: Mapped[str] = mapped_column(String(128), nullable=False, server_default=text("'unknown'"))
    error_message: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class AgentToolCall(AuditMixin, Base):
    __tablename__ = "agent_tool_calls"
    __table_args__ = (
        Index("idx_agent_tool_calls_run", "run_id"),
        Index("idx_agent_tool_calls_tool_created", "tool_name", "created_at"),
        Index("idx_agent_tool_calls_status_created", "status", "created_at"),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    run_id: Mapped[str] = mapped_column(String(64), ForeignKey("agent_runs.id", ondelete="CASCADE"), nullable=False)
    tool_name: Mapped[str] = mapped_column(String(128), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    latency_ms: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    input_payload: Mapped[dict | None] = mapped_column(JSON)
    output_payload: Mapped[dict | None] = mapped_column(JSON)
    error_message: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class AgentFeedback(AuditMixin, Base):
    __tablename__ = "agent_feedback"
    __table_args__ = (
        Index("idx_agent_feedback_run", "run_id"),
        Index("idx_agent_feedback_created", "created_at"),
        Index("idx_agent_feedback_user", "user_id", "created_at"),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    run_id: Mapped[str] = mapped_column(String(64), ForeignKey("agent_runs.id", ondelete="CASCADE"), nullable=False)
    user_id: Mapped[str | None] = mapped_column(String(128))
    rating: Mapped[int | None] = mapped_column(Integer)
    is_helpful: Mapped[bool | None] = mapped_column(Boolean)
    is_resolved: Mapped[bool | None] = mapped_column(Boolean)
    needs_human_takeover: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))
    hallucination_reported: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))
    feedback_text: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class AgentEvalCase(AuditMixin, Base):
    __tablename__ = "agent_eval_cases"
    __table_args__ = (
        Index("idx_agent_eval_cases_route_status", "route", "status"),
        Index("idx_agent_eval_cases_created", "created_at"),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    route: Mapped[str] = mapped_column(String(64), nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    input_payload: Mapped[dict] = mapped_column(JSON, nullable=False)
    expected_payload: Mapped[dict | None] = mapped_column(JSON)
    source_run_id: Mapped[str | None] = mapped_column(String(64), ForeignKey("agent_runs.id", ondelete="SET NULL"))
    status: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'active'"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class AgentEvalResult(AuditMixin, Base):
    __tablename__ = "agent_eval_results"
    __table_args__ = (
        Index("idx_agent_eval_results_case", "case_id", "created_at"),
        Index("idx_agent_eval_results_prompt_version", "prompt_version", "created_at"),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    case_id: Mapped[str] = mapped_column(String(64), ForeignKey("agent_eval_cases.id", ondelete="CASCADE"),
                                         nullable=False)
    run_id: Mapped[str | None] = mapped_column(String(64), ForeignKey("agent_runs.id", ondelete="SET NULL"))
    prompt_version: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("'default'"))
    route_score: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    answer_score: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    safety_score: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    hallucination_score: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    passed: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))
    judge_reason: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
