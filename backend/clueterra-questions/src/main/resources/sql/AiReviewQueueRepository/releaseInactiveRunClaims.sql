UPDATE cl_schema.hints hint
SET review_status = 'FAILED',
    review_claim_run_id = NULL,
    updated_at = NOW()
WHERE hint.review_status = 'IN_REVIEW'
  AND (
      hint.review_claim_run_id IS NULL
      OR NOT EXISTS (
          SELECT 1
          FROM cl_ai_schema.ai_run run
          WHERE run.id = hint.review_claim_run_id
            AND run.status IN ('PREPARED', 'RUNNING')
      )
  )
