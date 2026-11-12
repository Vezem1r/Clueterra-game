SELECT COUNT(*)::INT
FROM cl_ai_schema.ai_run
WHERE run_type = :runType
  AND status IN ('PREPARED', 'RUNNING')
