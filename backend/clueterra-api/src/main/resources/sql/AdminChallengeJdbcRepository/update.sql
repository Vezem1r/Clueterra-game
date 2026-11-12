WITH updated AS (
    UPDATE daily_challenges
    SET challenge_level = :level,
        difficulty      = :difficulty
    WHERE id = :id
    RETURNING *
)
SELECT id, challenge_date, challenge_level, difficulty,
       total_countries, total_hints, is_active, created_at,
       countries_json, hints_json
FROM updated
