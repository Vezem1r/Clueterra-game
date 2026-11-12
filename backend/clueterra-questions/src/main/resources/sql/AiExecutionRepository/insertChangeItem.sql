INSERT INTO cl_ai_schema.ai_hint_change_item (
    id, change_set_id, run_id, proposal_id, item_type, status, hint_id, country_id,
    reason_summary, diff_summary, original_snapshot, proposed_snapshot, created_at, decided_at
)
VALUES (
    :id, :changeSetId, :runId, :proposalId, :itemType, :status, :hintId, :countryId,
    :reasonSummary, :diffSummary, :originalSnapshot::jsonb, :proposedSnapshot::jsonb, :createdAt, :decidedAt
)
ON CONFLICT (id) DO UPDATE
SET change_set_id = EXCLUDED.change_set_id,
    run_id = EXCLUDED.run_id,
    proposal_id = EXCLUDED.proposal_id,
    item_type = EXCLUDED.item_type,
    status = EXCLUDED.status,
    hint_id = EXCLUDED.hint_id,
    country_id = EXCLUDED.country_id,
    reason_summary = EXCLUDED.reason_summary,
    diff_summary = EXCLUDED.diff_summary,
    original_snapshot = EXCLUDED.original_snapshot,
    proposed_snapshot = EXCLUDED.proposed_snapshot,
    created_at = EXCLUDED.created_at,
    decided_at = EXCLUDED.decided_at
