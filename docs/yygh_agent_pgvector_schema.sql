CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS medical_knowledge_chunk (
  id BIGSERIAL PRIMARY KEY,
  source_type VARCHAR(64) NOT NULL,
  source_url TEXT NULL,
  hospital_code VARCHAR(64) NULL,
  hospital_name VARCHAR(128) NULL,
  department VARCHAR(128) NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  embedding vector(1536) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_filter
  ON medical_knowledge_chunk (source_type, hospital_code, department, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_medical_knowledge_embedding_hnsw
  ON medical_knowledge_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS agent_memory_item (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NULL,
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

CREATE TABLE IF NOT EXISTS agent_knowledge_ingest_log (
  id BIGSERIAL PRIMARY KEY,
  source_url TEXT NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  message TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
