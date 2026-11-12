UPDATE cl_schema.hints
SET review_status = 'ACTION_PENDING',
    review_claim_run_id = NULL,
    last_review_run_id = :reviewRunId,
    updated_at = :reviewedAt
WHERE id = :hintId
  AND review_claim_run_id = :reviewRunId
