INSERT INTO cl_schema.hint_embedding (
    hint_id, dimensions, vector, model, content_revision_hash
)
VALUES (
    :hintId, :dimensions, :vector, :model, :contentRevisionHash
)
ON CONFLICT (hint_id) DO UPDATE
SET dimensions = EXCLUDED.dimensions,
    vector = EXCLUDED.vector,
    model = EXCLUDED.model,
    content_revision_hash = EXCLUDED.content_revision_hash,
    created_at = NOW()
