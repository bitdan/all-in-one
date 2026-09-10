CREATE TABLE IF NOT EXISTS agent_runs
(
    id                 VARCHAR(64) PRIMARY KEY,
    trace_id           VARCHAR(64)      NOT NULL UNIQUE,
    user_id            VARCHAR(128),
    route              VARCHAR(64)      NOT NULL,
    input_text         TEXT             NOT NULL,
    output_text        TEXT             NOT NULL DEFAULT '',
    structured_content JSON,
    status             VARCHAR(32)      NOT NULL,
    latency_ms         INTEGER          NOT NULL DEFAULT 0,
    steps_count        INTEGER          NOT NULL DEFAULT 0,
    retry_count        INTEGER          NOT NULL DEFAULT 0,
    prompt_tokens      INTEGER          NOT NULL DEFAULT 0,
    completion_tokens  INTEGER          NOT NULL DEFAULT 0,
    total_tokens       INTEGER          NOT NULL DEFAULT 0,
    estimated_cost     DOUBLE PRECISION NOT NULL DEFAULT 0,
    prompt_version     VARCHAR(64)      NOT NULL DEFAULT 'default',
    model_name         VARCHAR(128)     NOT NULL DEFAULT 'unknown',
    error_message      TEXT,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_runs_route_created
    ON agent_runs (route, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_runs_status_created
    ON agent_runs (status, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_runs_prompt_version
    ON agent_runs (prompt_version, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_runs_user
    ON agent_runs (user_id, created_at);

CREATE TABLE IF NOT EXISTS agent_tool_calls
(
    id             VARCHAR(64) PRIMARY KEY,
    run_id         VARCHAR(64)  NOT NULL REFERENCES agent_runs (id) ON DELETE CASCADE,
    tool_name      VARCHAR(128) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    latency_ms     INTEGER      NOT NULL DEFAULT 0,
    input_payload  JSON,
    output_payload JSON,
    error_message  TEXT,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_run
    ON agent_tool_calls (run_id);
CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_tool_created
    ON agent_tool_calls (tool_name, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_status_created
    ON agent_tool_calls (status, created_at);

CREATE TABLE IF NOT EXISTS agent_feedback
(
    id                     VARCHAR(64) PRIMARY KEY,
    run_id                 VARCHAR(64) NOT NULL REFERENCES agent_runs (id) ON DELETE CASCADE,
    user_id                VARCHAR(128),
    rating                 INTEGER,
    is_helpful             BOOLEAN,
    is_resolved            BOOLEAN,
    needs_human_takeover   BOOLEAN     NOT NULL DEFAULT false,
    hallucination_reported BOOLEAN     NOT NULL DEFAULT false,
    feedback_text          TEXT,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_feedback_run
    ON agent_feedback (run_id);
CREATE INDEX IF NOT EXISTS idx_agent_feedback_created
    ON agent_feedback (created_at);
CREATE INDEX IF NOT EXISTS idx_agent_feedback_user
    ON agent_feedback (user_id, created_at);

CREATE TABLE IF NOT EXISTS agent_eval_cases
(
    id               VARCHAR(64) PRIMARY KEY,
    route            VARCHAR(64)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    input_payload    JSON         NOT NULL,
    expected_payload JSON,
    source_run_id    VARCHAR(64)  REFERENCES agent_runs (id) ON DELETE SET NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_cases_route_status
    ON agent_eval_cases (route, status);
CREATE INDEX IF NOT EXISTS idx_agent_eval_cases_created
    ON agent_eval_cases (created_at);

CREATE TABLE IF NOT EXISTS agent_eval_results
(
    id                  VARCHAR(64) PRIMARY KEY,
    case_id             VARCHAR(64)      NOT NULL REFERENCES agent_eval_cases (id) ON DELETE CASCADE,
    run_id              VARCHAR(64)      REFERENCES agent_runs (id) ON DELETE SET NULL,
    prompt_version      VARCHAR(64)      NOT NULL DEFAULT 'default',
    route_score         DOUBLE PRECISION NOT NULL DEFAULT 0,
    answer_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    safety_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    hallucination_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    passed              BOOLEAN          NOT NULL DEFAULT false,
    judge_reason        TEXT,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_results_case
    ON agent_eval_results (case_id, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_eval_results_prompt_version
    ON agent_eval_results (prompt_version, created_at);
