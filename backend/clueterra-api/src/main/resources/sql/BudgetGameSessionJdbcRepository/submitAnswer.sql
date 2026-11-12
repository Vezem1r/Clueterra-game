SELECT
    outcome,
    session_id,
    question_variant_id,
    answer_position::INTEGER
FROM cl_api_schema.submit_budget_game_answer(
    :answerId,
    :sessionId,
    :userId,
    :variantId,
    :guessedValue,
    :guessedChoice
)
