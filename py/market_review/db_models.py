from datetime import date, datetime
from decimal import Decimal

from db.base import AuditMixin, Base
from sqlalchemy import Date, DateTime, Index, Integer, Numeric, String, Text, UniqueConstraint, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column


class MarketReviewRun(AuditMixin, Base):
    __tablename__ = "market_review_runs"
    __table_args__ = (
        UniqueConstraint("trade_date", "source", name="uq_market_review_runs_date_source"),
        Index("idx_market_review_runs_status_date", "status", "trade_date"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    source: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'akshare'"))
    status: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'final'"))
    error_message: Mapped[str | None] = mapped_column(Text)
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False,
                                                   server_default=text("NOW()"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketLimitUpPool(AuditMixin, Base):
    __tablename__ = "market_limit_up_pool"
    __table_args__ = (
        UniqueConstraint("trade_date", "code", name="uq_market_limit_up_pool_date_code"),
        Index("idx_market_limit_up_pool_date_boards", "trade_date", "consecutive_boards"),
        Index("idx_market_limit_up_pool_date_industry", "trade_date", "industry"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    code: Mapped[str] = mapped_column(String(16), nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    industry: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    latest_price: Mapped[Decimal | None] = mapped_column(Numeric(18, 4))
    change_percent: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    turnover_rate: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    amount: Mapped[Decimal | None] = mapped_column(Numeric(20, 2))
    circulating_market_value: Mapped[Decimal | None] = mapped_column(Numeric(20, 2))
    seal_amount: Mapped[Decimal | None] = mapped_column(Numeric(20, 2))
    first_limit_time: Mapped[str] = mapped_column(String(16), nullable=False, server_default=text("''"))
    last_limit_time: Mapped[str] = mapped_column(String(16), nullable=False, server_default=text("''"))
    open_count: Mapped[int | None] = mapped_column(Integer)
    consecutive_boards: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("1"))
    limit_up_stat: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("''"))
    board_quality_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    tags: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    raw_payload: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketSectorStrength(AuditMixin, Base):
    __tablename__ = "market_sector_strength"
    __table_args__ = (
        UniqueConstraint("trade_date", "industry", name="uq_market_sector_strength_date_industry"),
        Index("idx_market_sector_strength_date_score", "trade_date", "strength_score"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    industry: Mapped[str] = mapped_column(String(64), nullable=False)
    limit_up_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    advanced_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    max_consecutive_boards: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("1"))
    total_seal_amount: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    total_amount: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    open_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    core_stocks: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    strength_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    score_breakdown: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    risk_tags: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketCandidatePool(AuditMixin, Base):
    __tablename__ = "market_candidate_pool"
    __table_args__ = (
        UniqueConstraint("trade_date", "pool_type", "code", name="uq_market_candidate_pool_date_type_code"),
        Index("idx_market_candidate_pool_date_type_score", "trade_date", "pool_type", "candidate_score"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    pool_type: Mapped[str] = mapped_column(String(32), nullable=False)
    code: Mapped[str] = mapped_column(String(16), nullable=False)
    industry: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    target_boards: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("2"))
    candidate_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    level: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'观察'"))
    score_breakdown: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    reasons: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    risks: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    rule_version: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'v1'"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketReviewSignal(AuditMixin, Base):
    __tablename__ = "market_review_signal"
    __table_args__ = (
        UniqueConstraint("trade_date", "signal_type", "code", name="uq_market_review_signal_date_type_code"),
        Index("idx_market_review_signal_date_type_score", "trade_date", "signal_type", "signal_score"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    signal_type: Mapped[str] = mapped_column(String(64), nullable=False)
    code: Mapped[str] = mapped_column(String(16), nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    industry: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    phase: Mapped[str] = mapped_column(String(32), nullable=False)
    signal_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    score_breakdown: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    reasons: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    risks: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    rule_version: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'v1'"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketRadarSectorSnapshot(AuditMixin, Base):
    __tablename__ = "market_radar_sector_snapshot"
    __table_args__ = (
        UniqueConstraint("trade_date", "sector_type", "sector_name", name="uq_market_radar_sector_date_type_name"),
        Index("idx_market_radar_sector_date_score", "trade_date", "heat_score"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    sector_name: Mapped[str] = mapped_column(String(64), nullable=False)
    sector_type: Mapped[str] = mapped_column(String(32), nullable=False, server_default=text("'industry'"))
    heat_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    momentum_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    liquidity_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    breadth_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    limit_up_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    strong_stock_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    stock_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    rise_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    change_percent: Mapped[Decimal] = mapped_column(Numeric(10, 4), nullable=False, server_default=text("0"))
    total_amount: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    core_stocks: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    reasons: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    risks: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketRadarCandidateSnapshot(AuditMixin, Base):
    __tablename__ = "market_radar_candidate_snapshot"
    __table_args__ = (
        UniqueConstraint("trade_date", "code", "signal_type", name="uq_market_radar_candidate_date_code_signal"),
        Index("idx_market_radar_candidate_date_score", "trade_date", "candidate_score"),
        Index("idx_market_radar_candidate_date_industry", "trade_date", "industry"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    code: Mapped[str] = mapped_column(String(16), nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False)
    industry: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    latest_price: Mapped[Decimal | None] = mapped_column(Numeric(18, 4))
    change_percent: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    turnover_rate: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    amount: Mapped[Decimal | None] = mapped_column(Numeric(20, 2))
    candidate_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    sector_heat_score: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False, server_default=text("0"))
    signal_type: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("'sector_strength'"))
    reasons: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    risks: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    tags: Mapped[list] = mapped_column(JSONB, nullable=False, server_default=text("'[]'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketStockKlineDaily(AuditMixin, Base):
    __tablename__ = "market_stock_kline_daily"
    __table_args__ = (
        UniqueConstraint("trade_date", "code", name="uq_market_stock_kline_daily_date_code"),
        Index("idx_market_stock_kline_daily_code_date", "code", "trade_date"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    code: Mapped[str] = mapped_column(String(16), nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    open_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    close_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    high_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    low_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    volume: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    amount: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    amplitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    change_amount: Mapped[Decimal | None] = mapped_column(Numeric(18, 4))
    change_percent: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    turnover_rate: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    raw_payload: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketStockUniverse(AuditMixin, Base):
    __tablename__ = "market_stock_universe"
    __table_args__ = (
        Index("idx_market_stock_universe_status_code", "status", "code"),
    )

    code: Mapped[str] = mapped_column(String(16), primary_key=True)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    market: Mapped[str] = mapped_column(String(16), nullable=False, server_default=text("''"))
    status: Mapped[str] = mapped_column(String(16), nullable=False, server_default=text("'active'"))
    raw_payload: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))


class MarketStockKlineIntraday(AuditMixin, Base):
    __tablename__ = "market_stock_kline_intraday"
    __table_args__ = (
        UniqueConstraint("bar_time", "code", "period", name="uq_market_stock_kline_intraday_time_code_period"),
        Index("idx_market_stock_kline_intraday_code_period_time", "code", "period", "bar_time"),
        Index("idx_market_stock_kline_intraday_trade_date", "trade_date", "period"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    trade_date: Mapped[date] = mapped_column(Date, nullable=False)
    bar_time: Mapped[datetime] = mapped_column(DateTime(timezone=False), nullable=False)
    code: Mapped[str] = mapped_column(String(16), nullable=False)
    period: Mapped[str] = mapped_column(String(8), nullable=False)
    name: Mapped[str] = mapped_column(String(64), nullable=False, server_default=text("''"))
    open_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    close_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    high_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    low_price: Mapped[Decimal] = mapped_column(Numeric(18, 4), nullable=False)
    volume: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    amount: Mapped[Decimal] = mapped_column(Numeric(20, 2), nullable=False, server_default=text("0"))
    amplitude: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    change_amount: Mapped[Decimal | None] = mapped_column(Numeric(18, 4))
    change_percent: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    turnover_rate: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    raw_payload: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=text("NOW()"))
