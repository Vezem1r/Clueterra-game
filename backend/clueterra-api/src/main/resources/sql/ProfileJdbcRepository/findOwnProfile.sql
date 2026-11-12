WITH ensure AS (
    INSERT INTO user_profiles (user_id, total_xp, current_level, challenges_played,
                               challenges_won, current_streak, best_streak, created_at, updated_at)
    VALUES (:userId, 0, 1, 0, 0, 0, 0, NOW(), NOW())
    ON CONFLICT (user_id) DO NOTHING
),
today_participation AS (
    SELECT EXISTS(
        SELECT 1 FROM user_daily_activity
        WHERE user_id = :userId
          AND activity_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
    ) AS val
)
SELECT u.username,
       u.email,
       up.display_name,
       up.avatar_url,
       up.total_xp                    AS total_xp,
       up.current_level               AS current_level,
       up.challenges_played           AS completed_runs,
       up.countries_solved                                    AS countries_solved,
       up.perfect_runs                                        AS perfect_runs,
       COALESCE(
               CASE
                   WHEN up.countries_played > 0
                       THEN ROUND(100.0 * up.countries_solved / NULLIF(up.countries_played, 0), 1)
                   ELSE win_rate(up.challenges_played, up.challenges_won)
                   END,
               0
       )                                                      AS solve_rate,
       up.current_streak                                      AS current_streak,
       up.best_streak                                         AS best_streak,
       CASE WHEN u.is_guest THEN NULL ELSE leaderboard.leaderboard_rank END AS leaderboard_rank,
       up.favorite_difficulty                                 AS favorite_difficulty,
       u.profile_locked,
       u.social_discoverable                                  AS social_discoverable,
       up.streak_freezes                                      AS streak_freezes,
       up.streak_freezes_used_since_completion                AS streak_freezes_used_since_completion,
       up.streak_freezes_acknowledged_since_completion        AS streak_freezes_acknowledged_since_completion,
       COALESCE(budget_stats.completed_challenges, 0)          AS budget_completed_challenges,
       COALESCE(budget_stats.unique_projects_explored, 0)      AS budget_projects_explored,
       COALESCE(budget_stats.mastered_project_value_usd, 0)    AS budget_projects_mastered_value_usd,
       COALESCE((SELECT val FROM today_participation), FALSE) AS today_completed,
       CASE
           WHEN up.streak_freeze_used_at IS NOT NULL
                AND DATE(up.streak_freeze_used_at AT TIME ZONE 'UTC') = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
           THEN TRUE
           ELSE FALSE
       END                                                    AS freeze_used_today
FROM users u
JOIN user_profiles up ON up.user_id = u.id
LEFT JOIN user_budget_stats budget_stats ON budget_stats.user_id = u.id
LEFT JOIN LATERAL (
    SELECT COUNT(*)::INTEGER + 1 AS leaderboard_rank
    FROM user_profiles ranked
    JOIN users ranked_user ON ranked_user.id = ranked.user_id
    WHERE u.is_guest = FALSE
      AND ranked_user.is_guest = FALSE
      AND (
          ranked.total_xp > up.total_xp
          OR (ranked.total_xp = up.total_xp AND ranked.user_id < up.user_id)
      )
) leaderboard ON TRUE
WHERE u.id = :userId
