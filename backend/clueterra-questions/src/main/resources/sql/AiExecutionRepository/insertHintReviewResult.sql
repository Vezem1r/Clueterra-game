INSERT INTO cl_ai_schema.ai_hint_review_result (
    id, run_id, stage_id, hint_id, source_revision_hash, decision, confidence, summary_reason,
    evidence_summary, proposed_text, proposed_difficulty, proposed_image_url, disable_reason, change_set_id, created_at,
    retry_after_days
)
VALUES (
    :id, :runId, :stageId, :hintId, :sourceRevisionHash, :decision, :confidence, :summaryReason,
    :evidenceSummary, :proposedText, :proposedDifficulty, :proposedImageUrl, :disableReason, :changeSetId, :createdAt,
    :retryAfterDays
)
ON CONFLICT (hint_id, source_revision_hash)
    WHERE source_revision_hash IS NOT NULL AND superseded_at IS NULL
DO UPDATE SET run_id = EXCLUDED.run_id,
    stage_id = EXCLUDED.stage_id,
    hint_id = EXCLUDED.hint_id,
    source_revision_hash = EXCLUDED.source_revision_hash,
    decision = EXCLUDED.decision,
    confidence = EXCLUDED.confidence,
    summary_reason = EXCLUDED.summary_reason,
    evidence_summary = EXCLUDED.evidence_summary,
    proposed_text = EXCLUDED.proposed_text,
    proposed_difficulty = EXCLUDED.proposed_difficulty,
    proposed_image_url = EXCLUDED.proposed_image_url,
    disable_reason = EXCLUDED.disable_reason,
    retry_after_days = EXCLUDED.retry_after_days,
    change_set_id = EXCLUDED.change_set_id,
    created_at = EXCLUDED.created_at,
    superseded_at = NULL
