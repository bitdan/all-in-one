-- PostgreSQL schema for Tool Hub posts.
-- Run this once in the target database before enabling the post API.


CREATE DATABASE tool_hub
    WITH
    OWNER = root
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;



CREATE TABLE IF NOT EXISTS post_posts
(
    id            VARCHAR(64) PRIMARY KEY,
    title         VARCHAR(120) NOT NULL,
    category      VARCHAR(64)  NOT NULL DEFAULT '经验分享',
    content       TEXT         NOT NULL,
    author_id     VARCHAR(128) NOT NULL,
    author_name   VARCHAR(128) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'published',
    view_count    INTEGER      NOT NULL DEFAULT 0,
    like_count    INTEGER      NOT NULL DEFAULT 0,
    comment_count INTEGER      NOT NULL DEFAULT 0,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_post_posts_status_created
    ON post_posts (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_post_posts_author
    ON post_posts (author_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_post_posts_category_created
    ON post_posts (category, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_post_posts_search
    ON post_posts
    USING GIN (to_tsvector('simple', coalesce (title, '') || ' ' || coalesce (content, '')));

CREATE TABLE IF NOT EXISTS post_comments
(
    id          VARCHAR(64) PRIMARY KEY,
    post_id     VARCHAR(64)  NOT NULL REFERENCES post_posts (id) ON DELETE CASCADE,
    author_id   VARCHAR(128) NOT NULL,
    author_name VARCHAR(128) NOT NULL,
    content     TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'published',
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_post_comments_post
    ON post_comments (post_id, created_at ASC);

CREATE TABLE IF NOT EXISTS post_likes
(
    id BIGSERIAL PRIMARY KEY,
    post_id VARCHAR(64)  NOT NULL REFERENCES post_posts (id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_post_likes_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_likes_user
    ON post_likes (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS post_post_tags
(
    id BIGSERIAL PRIMARY KEY,
    post_id  VARCHAR(64) NOT NULL REFERENCES post_posts (id) ON DELETE CASCADE,
    tag_name VARCHAR(64) NOT NULL,
    created_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_post_post_tags_post_tag UNIQUE (post_id, tag_name)
);

CREATE INDEX IF NOT EXISTS idx_post_post_tags_post
    ON post_post_tags (post_id);

CREATE INDEX IF NOT EXISTS idx_post_post_tags_tag
    ON post_post_tags (tag_name);
