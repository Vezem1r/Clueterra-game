SELECT
    challenge.challenge_date,
    COUNT(question.position)::INTEGER AS question_count
FROM cl_budget_schema.daily_challenge challenge
JOIN cl_budget_schema.daily_challenge_question question
    ON question.challenge_id = challenge.id
WHERE challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
    AND challenge.status = 'PUBLISHED'
GROUP BY challenge.id, challenge.challenge_date
