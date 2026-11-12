UPDATE cl_schema.hints hint
SET review_status = 'FAILED',
    last_reviewed_at = NULL,
    review_retry_after_at = NULL,
    last_review_run_id = rejected.run_id,
    review_claim_run_id = NULL,
    updated_at = transaction_timestamp()
FROM (
    SELECT item.hint_id, MAX(item.run_id) AS run_id
    FROM cl_ai_schema.ai_hint_change_item item
    WHERE item.change_set_id = :changeSetId
      AND item.item_type = ANY(CAST(:itemTypes AS TEXT[]))
      AND item.status = 'REJECTED'
      AND item.decided_at = transaction_timestamp()
      AND item.proposal_id IS NULL
      AND item.hint_id IS NOT NULL
    GROUP BY item.hint_id
) rejected
WHERE hint.id = rejected.hint_id
  AND hint.is_active = TRUE
