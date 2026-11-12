SELECT hint.country_id
FROM cl_schema.hints hint
LEFT JOIN cl_schema.hint_embedding embedding ON embedding.hint_id = hint.id
WHERE hint.is_active = TRUE
  AND hint.hint_text IS NOT NULL
  AND (
      embedding.hint_id IS NULL
      OR embedding.model <> :model
      OR embedding.dimensions <> :dimensions
      OR embedding.content_revision_hash <> hint.content_revision_hash
  )
GROUP BY hint.country_id
ORDER BY count(*) DESC
LIMIT :limit
