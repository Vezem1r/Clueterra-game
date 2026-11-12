WITH dates AS (
    SELECT GENERATE_SERIES(
        (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE - (:days - 1),
        (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE,
        INTERVAL '1 day'
    )::DATE AS session_date
), aggregate AS (
    SELECT
        (started_at AT TIME ZONE 'UTC')::DATE AS session_date,
        COUNT(*)::INTEGER AS total_sessions,
        COUNT(*) FILTER (WHERE status = :completedStatus)::INTEGER AS completed_sessions,
        COUNT(DISTINCT user_id)::INTEGER AS unique_users
    FROM budget_game_session
    WHERE started_at >= NOW() - (:days * INTERVAL '1 day')
    GROUP BY (started_at AT TIME ZONE 'UTC')::DATE
)
SELECT
    dates.session_date,
    COALESCE(aggregate.total_sessions, 0) AS total_sessions,
    COALESCE(aggregate.completed_sessions, 0) AS completed_sessions,
    COALESCE(aggregate.unique_users, 0) AS unique_users
FROM dates
LEFT JOIN aggregate USING (session_date)
ORDER BY dates.session_date
