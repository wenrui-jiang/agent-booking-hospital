CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS agent_session (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NULL,
  intent VARCHAR(64) NULL,
  stage VARCHAR(64) NULL,
  slots_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_message (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_message_session_time
  ON agent_message (session_id, created_at);

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  arguments_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(32) NOT NULL,
  result_summary TEXT NULL,
  cost_ms BIGINT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_call_session_time
  ON agent_tool_call (session_id, created_at);

CREATE TABLE IF NOT EXISTS agent_memory_item (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  user_id BIGINT NULL,
  memory_type VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  source_message_ids BIGINT[] NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  embedding vector(1536) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_memory_item_user_type
  ON agent_memory_item (user_id, memory_type, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_memory_item_session
  ON agent_memory_item (session_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_memory_item_embedding_hnsw
  ON agent_memory_item USING hnsw (embedding vector_cosine_ops);
