SELECT *
FROM cl_ai_schema.ai_hint_change_set
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset
