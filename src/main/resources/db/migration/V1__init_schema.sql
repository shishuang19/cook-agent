-- Enable pgvector extension in PostgreSQL environment.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS recipe (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(128) UNIQUE,
    category VARCHAR(64) NOT NULL,
    difficulty VARCHAR(32),
    cook_time_minutes INT,
    summary TEXT,
    source_path TEXT,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(64),
    session_status VARCHAR(16) DEFAULT 'ACTIVE',
    rolling_summary TEXT,
    active_task VARCHAR(64),
    last_user_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message_log (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    token_estimate INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS memory_slot (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    slot_key VARCHAR(64) NOT NULL,
    slot_value JSONB NOT NULL,
    confidence NUMERIC(5, 4),
    scope VARCHAR(16) NOT NULL,
    source_trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipe_chunk_vector (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    section_id BIGINT,
    chunk_id VARCHAR(128) UNIQUE NOT NULL,
    chunk_text TEXT NOT NULL,
    chunk_summary TEXT,
    section_type VARCHAR(32),
    metadata JSONB NOT NULL,
    embedding VECTOR(1536),
    tsv TSVECTOR,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recipe_chunk_vector_recipe_id ON recipe_chunk_vector(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_chunk_vector_section_type ON recipe_chunk_vector(section_type);
CREATE INDEX IF NOT EXISTS idx_recipe_chunk_vector_metadata_gin ON recipe_chunk_vector USING GIN(metadata);
CREATE INDEX IF NOT EXISTS idx_recipe_chunk_vector_tsv ON recipe_chunk_vector USING GIN(tsv);
