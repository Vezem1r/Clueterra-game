UPDATE cl_budget_schema.sync_run
SET heartbeat_at = NOW()
WHERE id = :runId
  AND status = 'RUNNING'
