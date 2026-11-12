WITH target AS (
    SELECT
        challenge.id,
        challenge.challenge_date,
        EXISTS (
            SELECT 1
            FROM cl_api_schema.budget_game_session session
            WHERE session.challenge_id = challenge.id
        ) AS has_sessions
    FROM cl_budget_schema.daily_challenge challenge
    WHERE challenge.id = :challengeId
    FOR UPDATE
), deleted AS (
    DELETE FROM cl_budget_schema.daily_challenge challenge
    USING target
    WHERE challenge.id = target.id
      AND target.challenge_date > CURRENT_DATE
      AND NOT target.has_sessions
    RETURNING challenge.id
)
SELECT
    target.id AS challenge_id,
    target.challenge_date,
    target.challenge_date > CURRENT_DATE AS future,
    EXISTS (SELECT 1 FROM deleted) AS deleted,
    target.has_sessions
FROM target
