SELECT embedding.hint_id, embedding.vector
FROM cl_schema.hint_embedding embedding
JOIN cl_schema.hints hint ON hint.id = embedding.hint_id
WHERE hint.country_id = :countryId
  AND hint.is_active = TRUE
  AND embedding.model = :model
  AND embedding.dimensions = :dimensions
  AND embedding.content_revision_hash = hint.content_revision_hash
