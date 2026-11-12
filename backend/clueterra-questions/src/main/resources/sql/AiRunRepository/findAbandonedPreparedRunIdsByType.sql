SELECT id
FROM cl_ai_schema.ai_run
WHERE run_type = :runType
  AND status = 'PREPARED'
  AND started_at IS NULL
  AND created_at < :cutoff
ORDER BY created_at ASC
