UPDATE cl_budget_schema.sync_run
SET status = 'FAILED',
    completed_at = NOW(),
    categories_attempted = :categoriesAttempted,
    categories_succeeded = :categoriesSucceeded,
    records_discovered = :recordsDiscovered,
    records_retained = :recordsRetained,
    error_message = LEFT(:errorMessage, 2000)
WHERE id = :runId
  AND status = 'RUNNING'
RETURNING id,
    source_code,
    status,
    started_at,
    completed_at,
    categories_attempted,
    categories_succeeded,
    records_discovered,
    records_retained,
    error_message
