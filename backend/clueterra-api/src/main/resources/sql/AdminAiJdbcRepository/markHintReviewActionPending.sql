UPDATE cl_schema.hints
SET review_status = 'ACTION_PENDING',
    review_claim_run_id = NULL,
    updated_at = NOW()
WHERE id = :hintId
  AND is_active = TRUE
  AND review_status = 'FAILED'
