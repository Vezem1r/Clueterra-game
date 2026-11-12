SELECT
    answer.question_variant_id,
    answer.position,
    answer.guessed_value,
    answer.guessed_choice,
    answer.answer_value,
    answer.answer_unit,
    answer.answer_choice,
    answer.accuracy_percent,
    answer.raw_xp_percent,
    answer.raw_xp_earned,
    answer.hint_multiplier_percent,
    answer.hints_used,
    answer.xp_percent,
    answer.xp_earned,
    answer.reveal_payload::TEXT AS reveal_payload,
    answer.answered_at
FROM cl_api_schema.budget_game_answer answer
WHERE answer.session_id = :sessionId
ORDER BY answer.position
