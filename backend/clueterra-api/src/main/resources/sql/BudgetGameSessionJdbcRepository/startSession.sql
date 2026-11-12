SELECT
    outcome,
    session_id,
    NULL::BIGINT AS question_variant_id,
    NULL::INTEGER AS answer_position
FROM cl_api_schema.start_budget_game_session(
    :sessionId,
    :userId,
    :baseXp,
    CAST(:fullXpThresholdPercent AS NUMERIC),
    CAST(:curvePower AS NUMERIC),
    :levelDivisor
)
