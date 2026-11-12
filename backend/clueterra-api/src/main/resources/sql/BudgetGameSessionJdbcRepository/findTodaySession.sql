SELECT
    session.id,
    session.user_id,
    app_user.is_guest,
    session.challenge_id,
    challenge.challenge_date,
    session.status,
    session.current_position,
    session.total_xp_earned,
    session.streak_bonus_xp,
    session.base_xp_per_question,
    session.full_xp_threshold_percent,
    session.xp_before,
    session.level_before,
    session.started_at,
    session.completed_at
FROM cl_api_schema.budget_game_session session
JOIN cl_api_schema.users app_user ON app_user.id = session.user_id
JOIN cl_budget_schema.daily_challenge challenge ON challenge.id = session.challenge_id
WHERE session.user_id = :userId
    AND challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
