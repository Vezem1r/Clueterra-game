UPDATE cl_schema.hints
SET review_status = 'FAILED',
    review_claim_run_id = NULL,
    updated_at = NOW()
WHERE review_claim_run_id = :runId
  AND review_status = 'IN_REVIEW'
