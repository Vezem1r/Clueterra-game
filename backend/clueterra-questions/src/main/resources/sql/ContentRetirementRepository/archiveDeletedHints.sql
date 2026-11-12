WITH retirement_candidates AS (
    SELECT hint.*
    FROM hints hint
    JOIN hint_suppression suppression
      ON suppression.action = 'DELETE'
     AND suppression.is_active = TRUE
     AND suppression.country_id = hint.country_id
     AND suppression.source = hint.source
     AND suppression.category = hint.category
     AND suppression.hint_type = hint.hint_type
     AND suppression.source_indicator IS NOT DISTINCT FROM hint.source_indicator
    WHERE hint.is_active = FALSE
      AND NOT EXISTS (
          SELECT 1
          FROM daily_challenges challenge
          CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
          WHERE challenge.is_active = TRUE
            AND challenge.challenge_date >= CURRENT_DATE
            AND (payload ->> 'hintId')::BIGINT = hint.id
      )
), archived AS (
    INSERT INTO hints_history (
        id, country_id, category, hint_type, hint_text, difficulty,
        quality_score, source, source_indicator, source_year, content_hash, ambiguity_count,
        times_used, times_correct, is_active, image_resource_id, original_created_at,
        archived_at, archive_reason
    )
    SELECT candidate.id, candidate.country_id, candidate.category, candidate.hint_type,
           candidate.hint_text, candidate.difficulty, candidate.quality_score,
           candidate.source, candidate.source_indicator, candidate.source_year,
           candidate.content_hash,
           candidate.ambiguity_count, candidate.times_used, candidate.times_correct,
           candidate.is_active, candidate.image_resource_id, candidate.created_at,
           NOW(), 'admin_delete'
    FROM retirement_candidates candidate
    ON CONFLICT (id) DO NOTHING
    RETURNING id
), deleted AS (
    DELETE FROM hints hint
    WHERE hint.id IN (SELECT candidate.id FROM retirement_candidates candidate)
      AND EXISTS (SELECT 1 FROM hints_history history WHERE history.id = hint.id)
    RETURNING hint.id
)
SELECT COUNT(*)::INTEGER FROM deleted
