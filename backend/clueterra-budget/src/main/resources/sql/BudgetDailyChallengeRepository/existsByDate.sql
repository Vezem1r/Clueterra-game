SELECT EXISTS (
    SELECT 1
    FROM cl_budget_schema.daily_challenge
    WHERE challenge_date = :date
)
