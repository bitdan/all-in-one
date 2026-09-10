CREATE TABLE IF NOT EXISTS market_review_runs
(
    id BIGSERIAL PRIMARY KEY,
    trade_date    DATE        NOT NULL,
    source        VARCHAR(32) NOT NULL DEFAULT 'akshare',
    status VARCHAR(32) NOT NULL DEFAULT 'final',
    error_message TEXT,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_review_runs_date_source UNIQUE (trade_date, source)
);

CREATE INDEX IF NOT EXISTS idx_market_review_runs_status_date
    ON market_review_runs (status, trade_date);

CREATE TABLE IF NOT EXISTS market_limit_up_pool
(
    id BIGSERIAL PRIMARY KEY,
    trade_date               DATE           NOT NULL,
    code                     VARCHAR(16)    NOT NULL,
    name                     VARCHAR(64)    NOT NULL,
    industry                 VARCHAR(64)    NOT NULL DEFAULT '',
    latest_price             NUMERIC(18, 4),
    change_percent           NUMERIC(10, 4),
    turnover_rate            NUMERIC(10, 4),
    amount                   NUMERIC(20, 2),
    circulating_market_value NUMERIC(20, 2),
    seal_amount              NUMERIC(20, 2),
    first_limit_time         VARCHAR(16)    NOT NULL DEFAULT '',
    last_limit_time          VARCHAR(16)    NOT NULL DEFAULT '',
    open_count               INTEGER,
    consecutive_boards       INTEGER        NOT NULL DEFAULT 1,
    limit_up_stat            VARCHAR(32)    NOT NULL DEFAULT '',
    board_quality_score      NUMERIC(10, 2) NOT NULL DEFAULT 0,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_limit_up_pool_date_code UNIQUE (trade_date, code)
);

CREATE INDEX IF NOT EXISTS idx_market_limit_up_pool_date_boards
    ON market_limit_up_pool (trade_date, consecutive_boards);
CREATE INDEX IF NOT EXISTS idx_market_limit_up_pool_date_industry
    ON market_limit_up_pool (trade_date, industry);

CREATE TABLE IF NOT EXISTS market_sector_strength
(
    id BIGSERIAL PRIMARY KEY,
    trade_date             DATE           NOT NULL,
    industry               VARCHAR(64)    NOT NULL,
    limit_up_count         INTEGER        NOT NULL DEFAULT 0,
    advanced_count         INTEGER        NOT NULL DEFAULT 0,
    max_consecutive_boards INTEGER        NOT NULL DEFAULT 1,
    total_seal_amount      NUMERIC(20, 2) NOT NULL DEFAULT 0,
    total_amount           NUMERIC(20, 2) NOT NULL DEFAULT 0,
    open_count             INTEGER        NOT NULL DEFAULT 0,
    core_stocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    strength_score         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    risk_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_sector_strength_date_industry UNIQUE (trade_date, industry)
);

CREATE INDEX IF NOT EXISTS idx_market_sector_strength_date_score
    ON market_sector_strength (trade_date, strength_score);

CREATE TABLE IF NOT EXISTS market_candidate_pool
(
    id BIGSERIAL PRIMARY KEY,
    trade_date      DATE           NOT NULL,
    pool_type       VARCHAR(32)    NOT NULL,
    code            VARCHAR(16)    NOT NULL,
    industry        VARCHAR(64)    NOT NULL DEFAULT '',
    target_boards   INTEGER        NOT NULL DEFAULT 2,
    candidate_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    level           VARCHAR(32)    NOT NULL DEFAULT '观察',
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    risks JSONB NOT NULL DEFAULT '[]'::jsonb,
    rule_version    VARCHAR(32)    NOT NULL DEFAULT 'v1',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_candidate_pool_date_type_code UNIQUE (trade_date, pool_type, code)
);

CREATE INDEX IF NOT EXISTS idx_market_candidate_pool_date_type_score
    ON market_candidate_pool (trade_date, pool_type, candidate_score);

CREATE TABLE IF NOT EXISTS market_review_signal
(
    id BIGSERIAL PRIMARY KEY,
    trade_date   DATE           NOT NULL,
    signal_type  VARCHAR(64)    NOT NULL,
    code         VARCHAR(16)    NOT NULL,
    name         VARCHAR(64)    NOT NULL,
    industry     VARCHAR(64)    NOT NULL DEFAULT '',
    phase        VARCHAR(32)    NOT NULL,
    signal_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    risks JSONB NOT NULL DEFAULT '[]'::jsonb,
    rule_version VARCHAR(32)    NOT NULL DEFAULT 'v1',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_review_signal_date_type_code UNIQUE (trade_date, signal_type, code)
);

CREATE INDEX IF NOT EXISTS idx_market_review_signal_date_type_score
    ON market_review_signal (trade_date, signal_type, signal_score);

CREATE TABLE IF NOT EXISTS market_radar_sector_snapshot
(
    id BIGSERIAL PRIMARY KEY,
    trade_date DATE NOT NULL,
    sector_name VARCHAR(64) NOT NULL,
    sector_type VARCHAR(32) NOT NULL DEFAULT 'industry',
    heat_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    momentum_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    liquidity_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    breadth_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    limit_up_count INTEGER NOT NULL DEFAULT 0,
    strong_stock_count INTEGER NOT NULL DEFAULT 0,
    stock_count INTEGER NOT NULL DEFAULT 0,
    rise_count INTEGER NOT NULL DEFAULT 0,
    change_percent NUMERIC(10, 4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(20, 2) NOT NULL DEFAULT 0,
    core_stocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    risks JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_radar_sector_date_type_name UNIQUE (trade_date, sector_type, sector_name)
);

CREATE INDEX IF NOT EXISTS idx_market_radar_sector_date_score
    ON market_radar_sector_snapshot (trade_date, heat_score);

CREATE TABLE IF NOT EXISTS market_radar_candidate_snapshot
(
    id BIGSERIAL PRIMARY KEY,
    trade_date DATE NOT NULL,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(64) NOT NULL,
    industry VARCHAR(64) NOT NULL DEFAULT '',
    latest_price NUMERIC(18, 4),
    change_percent NUMERIC(10, 4),
    turnover_rate NUMERIC(10, 4),
    amount NUMERIC(20, 2),
    candidate_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    sector_heat_score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    signal_type VARCHAR(64) NOT NULL DEFAULT 'sector_strength',
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    risks JSONB NOT NULL DEFAULT '[]'::jsonb,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_radar_candidate_date_code_signal UNIQUE (trade_date, code, signal_type)
);

CREATE INDEX IF NOT EXISTS idx_market_radar_candidate_date_score
    ON market_radar_candidate_snapshot (trade_date, candidate_score);
CREATE INDEX IF NOT EXISTS idx_market_radar_candidate_date_industry
    ON market_radar_candidate_snapshot (trade_date, industry);

CREATE TABLE IF NOT EXISTS market_stock_kline_daily
(
    id BIGSERIAL PRIMARY KEY,
    trade_date     DATE           NOT NULL,
    code           VARCHAR(16)    NOT NULL,
    name           VARCHAR(64)    NOT NULL DEFAULT '',
    open_price     NUMERIC(18, 4) NOT NULL,
    close_price    NUMERIC(18, 4) NOT NULL,
    high_price     NUMERIC(18, 4) NOT NULL,
    low_price      NUMERIC(18, 4) NOT NULL,
    volume         NUMERIC(20, 2) NOT NULL DEFAULT 0,
    amount         NUMERIC(20, 2) NOT NULL DEFAULT 0,
    amplitude      NUMERIC(10, 4),
    change_amount  NUMERIC(18, 4),
    change_percent NUMERIC(10, 4),
    turnover_rate  NUMERIC(10, 4),
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_stock_kline_daily_date_code UNIQUE (trade_date, code)
);

CREATE INDEX IF NOT EXISTS idx_market_stock_kline_daily_code_date
    ON market_stock_kline_daily (code, trade_date);

CREATE TABLE IF NOT EXISTS market_stock_kline_intraday
(
    id BIGSERIAL PRIMARY KEY,
    trade_date     DATE           NOT NULL,
    bar_time       TIMESTAMP      NOT NULL,
    code           VARCHAR(16)    NOT NULL,
    period         VARCHAR(8)     NOT NULL,
    name           VARCHAR(64)    NOT NULL DEFAULT '',
    open_price     NUMERIC(18, 4) NOT NULL,
    close_price    NUMERIC(18, 4) NOT NULL,
    high_price     NUMERIC(18, 4) NOT NULL,
    low_price      NUMERIC(18, 4) NOT NULL,
    volume         NUMERIC(20, 2) NOT NULL DEFAULT 0,
    amount         NUMERIC(20, 2) NOT NULL DEFAULT 0,
    amplitude      NUMERIC(10, 4),
    change_amount  NUMERIC(18, 4),
    change_percent NUMERIC(10, 4),
    turnover_rate  NUMERIC(10, 4),
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_stock_kline_intraday_time_code_period UNIQUE (bar_time, code, period)
);

CREATE INDEX IF NOT EXISTS idx_market_stock_kline_intraday_code_period_time
    ON market_stock_kline_intraday (code, period, bar_time);
CREATE INDEX IF NOT EXISTS idx_market_stock_kline_intraday_trade_date
    ON market_stock_kline_intraday (trade_date, period);

CREATE TABLE IF NOT EXISTS market_stock_universe
(
    code VARCHAR(16) PRIMARY KEY,
    name VARCHAR(64) NOT NULL DEFAULT '',
    market VARCHAR(16) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_market_stock_universe_status_code
    ON market_stock_universe (status, code);
