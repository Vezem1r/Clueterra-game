UPDATE hints
SET review_status = 'REVIEWED',
    last_reviewed_at = :reviewedAt,
    last_review_run_id = :reviewRunId,
    review_retry_after_at = :reviewRetryAfterAt,
    review_claim_run_id = NULL,
    updated_at = :reviewedAt
WHERE id = :hintId
  AND (review_claim_run_id = :reviewRunId OR review_claim_run_id IS NULL)
