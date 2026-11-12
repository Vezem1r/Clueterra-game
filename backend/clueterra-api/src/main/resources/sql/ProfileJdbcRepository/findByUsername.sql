SELECT u.username,
       up.display_name,
       up.avatar_url,
       COALESCE(up.total_xp, 0)          AS total_xp,
       COALESCE(up.current_level, 1)     AS current_level,
       COALESCE(up.challenges_played, 0) AS completed_runs,
       COALESCE(up.countries_solved, 0)  AS countries_solved,
       COALESCE(up.perfect_runs, 0)      AS perfect_runs,
       COALESCE(
               CASE
                   WHEN COALESCE(up.countries_played, 0) > 0
                       THEN ROUND(100.0 * up.countries_solved / NULLIF(up.countries_played, 0), 1)
                   ELSE win_rate(up.challenges_played, up.challenges_won)
                   END,
               0
       )                                 AS solve_rate,
       COALESCE(up.current_streak, 0)    AS current_streak,
       COALESCE(up.best_streak, 0)       AS best_streak,
       leaderboard.leaderboard_rank,
       up.favorite_difficulty            AS favorite_difficulty,
       COALESCE(up.streak_freezes, 0)    AS streak_freezes,
       COALESCE(budget_stats.completed_challenges, 0)       AS budget_completed_challenges,
       COALESCE(budget_stats.unique_projects_explored, 0)   AS budget_projects_explored,
       COALESCE(budget_stats.mastered_project_value_usd, 0) AS budget_projects_mastered_value_usd
FROM users u
LEFT JOIN user_profiles up ON up.user_id = u.id
LEFT JOIN user_budget_stats budget_stats ON budget_stats.user_id = u.id
LEFT JOIN LATERAL (
    SELECT COUNT(*)::INTEGER + 1 AS leaderboard_rank
    FROM user_profiles ranked
    JOIN users ranked_user ON ranked_user.id = ranked.user_id
    WHERE u.is_guest = FALSE
      AND ranked_user.is_guest = FALSE
      AND (
          ranked.total_xp > COALESCE(up.total_xp, 0)
          OR (ranked.total_xp = COALESCE(up.total_xp, 0) AND ranked.user_id < u.id)
      )
) leaderboard ON TRUE
WHERE LOWER(u.username) = LOWER(:username)
