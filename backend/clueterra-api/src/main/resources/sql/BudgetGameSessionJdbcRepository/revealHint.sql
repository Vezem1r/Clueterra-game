SELECT
    outcome,
    hint_index,
    hint_payload::TEXT AS hint_payload,
    effective_multiplier_percent,
    revealed_at
FROM cl_api_schema.reveal_budget_game_hint(
    :sessionId,
    :userId,
    :variantId,
    CAST(:hintIndex AS SMALLINT)
)
