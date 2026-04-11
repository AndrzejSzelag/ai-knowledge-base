-- 1. Enable pgvector extension to support vector data types
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Table for 1024-dim embeddings (e.g. mxbai-embed-large)
-- Ensure dimension (1024) matches your specific embedding model output!
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid NOT NULL PRIMARY KEY,
    content text,
    metadata jsonb,
    embedding vector(1024) 
);

-- 3. HNSW index for similarity search (The core of RAG performance)
-- m=16: Balanced RAM usage for 1024-dim vectors
-- ef_construction=128: Optimized for mid-range CPUs (like i5) to speed up indexing
-- vector_cosine_ops: Used for cosine similarity (standard for LLM embeddings)
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
ON vector_store USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- 4. GIN index for fast metadata filtering (e.g. searching by category or tags)
CREATE INDEX IF NOT EXISTS vector_store_metadata_idx
ON vector_store USING gin (metadata);

-- 5. Functional index for sorting by ingestion time
-- Casting to bigint matches Spring Data / Java Long requirements for fast sorting
CREATE INDEX IF NOT EXISTS vector_store_ingested_at_idx 
ON vector_store (((metadata->>'ingested_at')::bigint) DESC);

-- ==========================================================
-- DIAGNOSTIC COMMANDS (Run via Docker/psql)
-- ==========================================================
-- Connection: docker exec -it postgres-pgvector psql -U user -d ai-db

-- Check table structure and indexes: \d vector_store

-- Verify RAG search and index usage: 
-- EXPLAIN ANALYZE SELECT content FROM vector_store ORDER BY embedding <=> array_fill(0, ARRAY[1024])::vector LIMIT 1;

-- Quick data preview:
-- SELECT content, metadata FROM vector_store LIMIT 1;

-- Clean database: 
-- TRUNCATE TABLE vector_store;
-- Shell version: docker exec -it postgres-pgvector psql -U user -d ai-db -c "TRUNCATE TABLE vector_store;"