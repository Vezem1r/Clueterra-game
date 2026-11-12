SELECT COUNT(*)
FROM cl_budget_schema.daily_challenge
WHERE (:period = 'UPCOMING' AND challenge_date >= CURRENT_DATE)
    OR (:period = 'PAST' AND challenge_date < CURRENT_DATE)
