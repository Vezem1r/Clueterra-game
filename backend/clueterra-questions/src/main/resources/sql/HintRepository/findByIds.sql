SELECT *
FROM cl_media_schema.hint_projection
WHERE id IN (:hintIds)
ORDER BY updated_at DESC, id DESC
