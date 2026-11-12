SELECT
    (SELECT COUNT(*) FROM game_sessions
     WHERE started_at >= NOW() - INTERVAL '30 days') AS geography_total_sessions,
    (SELECT COUNT(*) FROM game_sessions
     WHERE started_at >= NOW() - INTERVAL '30 days' AND status = :completedStatus) AS geography_completed_sessions,
    (SELECT COUNT(*) FROM game_sessions
     WHERE started_at >= NOW() - INTERVAL '30 days' AND status = :abandonedStatus) AS geography_abandoned_sessions,
    (SELECT COUNT(DISTINCT user_id) FROM game_sessions
     WHERE started_at >= NOW() - INTERVAL '30 days') AS geography_unique_users,
    (SELECT COUNT(DISTINCT user_id) FROM user_daily_participation
     WHERE challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE) AS geography_active_today,
    (SELECT COUNT(*) FROM budget_game_session
     WHERE started_at >= NOW() - INTERVAL '30 days') AS budget_total_sessions,
    (SELECT COUNT(*) FROM budget_game_session
     WHERE started_at >= NOW() - INTERVAL '30 days' AND status = :completedStatus) AS budget_completed_sessions,
    (SELECT COUNT(*) FROM budget_game_session
     WHERE started_at >= NOW() - INTERVAL '30 days' AND status = :abandonedStatus) AS budget_abandoned_sessions,
    (SELECT COUNT(DISTINCT user_id) FROM budget_game_session
     WHERE started_at >= NOW() - INTERVAL '30 days') AS budget_unique_users,
    (SELECT COUNT(DISTINCT session.user_id)
     FROM budget_game_session session
     JOIN cl_budget_schema.daily_challenge challenge ON challenge.id = session.challenge_id
     WHERE challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE) AS budget_active_today,
    (SELECT COUNT(*) FROM users WHERE is_guest = FALSE) AS total_registered_users
