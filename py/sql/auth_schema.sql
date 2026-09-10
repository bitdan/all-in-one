-- PostgreSQL schema for Tool Hub auth users.
-- Run this in the tool_hub database before enabling PostgreSQL user persistence.

CREATE TABLE IF NOT EXISTS sys_users
(
    id BIGSERIAL PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL UNIQUE,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    avatar        TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'active',
    roles JSONB NOT NULL DEFAULT '["user"]'::jsonb,
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    last_login_at TIMESTAMPTZ,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sys_users_status_created
    ON sys_users (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sys_users_email
    ON sys_users (email)
    WHERE email IS NOT NULL;

INSERT INTO sys_users (user_id,
                       username,
                       password_hash,
                       email,
                       avatar,
                       status,
                       roles,
                       permissions,
                       created_by,
                       updated_by,
                       created_at,
                       updated_at)
VALUES ('admin',
        'admin',
        '$2b$12$EU5Lp5TvjeohpxP6WbLJoOOGuLMF0IcIzABzEq9wAbb2DW64Bcq9G',
        'admin@example.com',
        NULL,
        'active',
        '["admin"]'::jsonb,
        '["*"]'::jsonb,
        'admin',
        'admin',
        NOW(),
        NOW())
ON CONFLICT
    (username)
    DO UPDATE
SET user_id = EXCLUDED.user_id,
    password_hash = EXCLUDED.password_hash,
    email = EXCLUDED.email,
    status = 'active',
    roles = EXCLUDED.roles,
    permissions = EXCLUDED.permissions,
    updated_by = 'admin',
    updated_at = NOW();
