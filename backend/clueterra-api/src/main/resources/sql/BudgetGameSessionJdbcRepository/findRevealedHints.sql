SELECT
    hint.question_variant_id,
    hint.position,
    hint.hint_index,
    hint.multiplier_percent,
    hint.hint_payload::TEXT AS hint_payload,
    hint.revealed_at
FROM cl_api_schema.budget_game_hint hint
WHERE hint.session_id = :sessionId
ORDER BY hint.position, hint.hint_index
