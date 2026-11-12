UPDATE cl_ai_schema.ai_run_stage
SET status = 'SKIPPED',
    error_message = :reason,
    completed_at = :completedAt
WHERE run_id IN (:runIds)
  AND status IN ('PREPARED', 'RUNNING')
