CREATE TABLE cl_schema.hint_embedding (
    hint_id               BIGINT      PRIMARY KEY,
    dimensions            INTEGER     NOT NULL,
    vector                REAL[]      NOT NULL,
    model                 VARCHAR(80) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_revision_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_hint_embedding_hint
        FOREIGN KEY (hint_id) REFERENCES cl_schema.hints (id) ON DELETE CASCADE,
    CONSTRAINT chk_hint_embedding_dimensions
        CHECK (dimensions BETWEEN 1 AND 3072)
);

COMMENT ON TABLE cl_schema.hint_embedding IS 'Semantic vector per hint, used for paraphrase-aware duplicate detection';

COMMENT ON COLUMN cl_schema.hint_embedding.content_revision_hash IS 'Hint revision used to create this vector; mismatches make the embedding stale';
