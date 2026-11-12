UPDATE cl_schema.hints
SET review_status = 'FAILED',
    last_reviewed_at = NULL,
    review_retry_after_at = NULL,
    last_review_run_id = :runId,
    review_claim_run_id = NULL,
    updated_at = :now
WHERE id = :hintId
  AND is_active = TRUE
