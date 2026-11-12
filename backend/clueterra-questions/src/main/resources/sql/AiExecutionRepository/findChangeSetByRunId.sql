SELECT *
FROM cl_ai_schema.ai_hint_change_set
WHERE run_id = :runId
ORDER BY created_at DESC
LIMIT 1
