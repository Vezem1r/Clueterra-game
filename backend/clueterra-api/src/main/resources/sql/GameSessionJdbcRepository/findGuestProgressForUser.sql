WITH budget_progress AS (
    SELECT COUNT(*) FILTER (WHERE session.status = 'COMPLETED')::INT AS play_count,
           COALESCE(SUM(session.total_xp_earned) FILTER (
               WHERE session.status = 'COMPLETED'
                 AND session.xp_applied_at IS NULL
           ), 0)::BIGINT AS pending_xp
    FROM cl_api_schema.budget_game_session session
    WHERE session.user_id = :userId
),
level_settings AS (
    SELECT COALESCE((
        SELECT setting.value::INTEGER
        FROM cl_api_schema.app_settings setting
        WHERE setting.key = 'level_divisor'
    ), 100)::INT AS level_divisor
)
SELECT COALESCE(up.challenges_played, 0)::INT                         AS play_count,
       budget_progress.play_count                                    AS budget_play_count,
       (COALESCE(up.total_xp, 0) + budget_progress.pending_xp)::BIGINT AS total_xp,
       GREATEST(
           COALESCE(up.current_level, 1),
           FLOOR(SQRT(
               (COALESCE(up.total_xp, 0) + budget_progress.pending_xp)::DOUBLE PRECISION
               / GREATEST(level_settings.level_divisor, 1)::DOUBLE PRECISION
           ))::INT + 1
       )::INT                                                        AS current_level,
       level_settings.level_divisor                                  AS level_divisor,
       COALESCE(up.current_streak, 0)::INT                           AS current_streak,
       COALESCE(up.best_streak, 0)::INT                              AS best_streak,
       COALESCE((
           SELECT COUNT(*)::INT
           FROM cl_api_schema.user_badges ub
           WHERE ub.user_id = u.id
       ), 0)::INT                                                    AS badges_earned,
       u.guest_token_expires_at                                      AS expires_at
FROM cl_api_schema.users u
LEFT JOIN cl_api_schema.user_profiles up ON up.user_id = u.id
CROSS JOIN budget_progress
CROSS JOIN level_settings
WHERE u.id = :userId
  AND u.is_guest = TRUE
  AND u.guest_token_expires_at > NOW()
