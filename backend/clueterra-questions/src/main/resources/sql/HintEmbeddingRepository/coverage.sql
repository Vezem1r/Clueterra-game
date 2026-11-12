SELECT count(*) AS active_hints,
       count(embedding.hint_id) FILTER (
           WHERE embedding.model = :model
             AND embedding.dimensions = :dimensions
             AND embedding.content_revision_hash = hint.content_revision_hash
       ) AS embedded_hints
FROM cl_schema.hints hint
LEFT JOIN cl_schema.hint_embedding embedding ON embedding.hint_id = hint.id
WHERE hint.is_active = TRUE
