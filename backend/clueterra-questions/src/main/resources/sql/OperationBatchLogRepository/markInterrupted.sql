UPDATE operation_batch_log
SET status = 'FAILED',
    completed_at = :completedAt,
    duration_ms = GREATEST(
        0,
        (EXTRACT(EPOCH FROM (:completedAt - started_at)) * 1000)::BIGINT
    ),
    error_message = :errorMessage
WHERE status = 'STARTED'
  AND summary ->> 'operationId' = :operationId
