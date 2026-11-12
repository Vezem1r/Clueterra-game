SELECT
    challenge.id,
    challenge.challenge_date,
    challenge.generation_trigger,
    challenge.status,
    challenge.published_at,
    challenge.challenge_date > CURRENT_DATE AS deletable,
    COALESCE(
        JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'position', entry.position,
                'questionId', variant.id,
                'questionType', variant.question_type,
                'difficulty', variant.difficulty,
                'projectName', source_question.name_snapshot,
                'comparisonProjectName', comparison_question.name_snapshot,
                'active', variant.is_active,
                'answerValue', variant.answer_value,
                'answerUnit', variant.answer_unit,
                'answerChoice', variant.answer_choice,
                'prompt', variant.prompt_payload,
                'reveal', variant.reveal_payload
            ) ORDER BY entry.position
        ) FILTER (WHERE entry.position IS NOT NULL),
        '[]'::JSONB
    ) AS questions
FROM cl_budget_schema.daily_challenge challenge
LEFT JOIN cl_budget_schema.daily_challenge_question entry ON entry.challenge_id = challenge.id
LEFT JOIN cl_budget_schema.question_variant variant ON variant.id = entry.question_variant_id
LEFT JOIN cl_budget_schema.question source_question ON source_question.id = variant.source_question_id
LEFT JOIN cl_budget_schema.question comparison_question
    ON comparison_question.id = variant.comparison_question_id
WHERE (:period = 'UPCOMING' AND challenge.challenge_date >= CURRENT_DATE)
    OR (:period = 'PAST' AND challenge.challenge_date < CURRENT_DATE)
GROUP BY challenge.id
ORDER BY
    CASE WHEN :period = 'UPCOMING' THEN challenge.challenge_date END,
    challenge.challenge_date DESC
LIMIT :limit OFFSET :offset
