SELECT source_question.project_id
FROM cl_budget_schema.daily_challenge challenge
JOIN cl_budget_schema.daily_challenge_question challenge_question
    ON challenge_question.challenge_id = challenge.id
JOIN cl_budget_schema.question_variant variant
    ON variant.id = challenge_question.question_variant_id
JOIN cl_budget_schema.question source_question
    ON source_question.id = variant.source_question_id
WHERE challenge.challenge_date BETWEEN :fromDate AND :toDate
UNION
SELECT comparison_question.project_id
FROM cl_budget_schema.daily_challenge challenge
JOIN cl_budget_schema.daily_challenge_question challenge_question
    ON challenge_question.challenge_id = challenge.id
JOIN cl_budget_schema.question_variant variant
    ON variant.id = challenge_question.question_variant_id
JOIN cl_budget_schema.question comparison_question
    ON comparison_question.id = variant.comparison_question_id
WHERE challenge.challenge_date BETWEEN :fromDate AND :toDate
