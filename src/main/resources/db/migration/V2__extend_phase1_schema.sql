CREATE TABLE IF NOT EXISTS ingredient (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) UNIQUE NOT NULL,
    alias_names TEXT,
    category VARCHAR(64),
    is_allergen BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipe_ingredient (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    amount VARCHAR(64),
    unit VARCHAR(32),
    is_optional BOOLEAN DEFAULT FALSE,
    sort_order INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_ingredient_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id),
    CONSTRAINT fk_recipe_ingredient_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id)
);

CREATE TABLE IF NOT EXISTS recipe_section (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    section_type VARCHAR(32) NOT NULL,
    title VARCHAR(128),
    content TEXT NOT NULL,
    sort_order INT,
    token_estimate INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_section_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
);

CREATE TABLE IF NOT EXISTS recipe_tag (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    tag_type VARCHAR(32),
    tag_value VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_tag_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
);

CREATE TABLE IF NOT EXISTS retrieval_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    query_text TEXT NOT NULL,
    rewritten_query TEXT,
    intent VARCHAR(32),
    vector_topk INT,
    keyword_topk INT,
    rerank_topn INT,
    result_chunk_ids JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_run_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    agent_name VARCHAR(64) NOT NULL,
    state_before VARCHAR(32),
    state_after VARCHAR(32),
    input_payload JSONB,
    output_payload JSONB,
    status VARCHAR(16),
    error_message TEXT,
    latency_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recipe_section_recipe_id ON recipe_section(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_tag_recipe_id ON recipe_tag(recipe_id);
CREATE INDEX IF NOT EXISTS idx_retrieval_log_trace_id ON retrieval_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_agent_run_log_trace_id ON agent_run_log(trace_id);
