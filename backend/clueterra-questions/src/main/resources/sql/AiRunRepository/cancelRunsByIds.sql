UPDATE cl_ai_schema.ai_run
SET status = 'CANCELLED',
    error_message = :reason,
    completed_at = :completedAt
WHERE id IN (:runIds)
  AND status IN ('PREPARED', 'RUNNING')
