SELECT *
FROM cl_ai_schema.ai_run
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset
