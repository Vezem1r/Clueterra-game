SELECT COALESCE(p.total_xp, 0)          AS total_xp,
       COALESCE(p.current_level, 1)     AS current_level,
       COALESCE(p.challenges_played, 0) AS challenges_played,
       COALESCE(p.challenges_won, 0)    AS challenges_won,
       COALESCE(p.current_streak, 0)    AS current_streak,
       COALESCE(p.best_streak, 0)       AS best_streak,
       COALESCE(p.perfect_runs, 0)      AS perfect_runs,
       COALESCE(bs.completed_challenges, 0)      AS budget_completed_challenges,
       COALESCE(bs.exact_guesses, 0)             AS budget_exact_guesses,
       COALESCE(bs.clean_sweeps, 0)              AS budget_clean_sweeps,
       COALESCE(bs.unique_projects_explored, 0)  AS budget_unique_projects,
       COALESCE(bs.higher_lower_best_streak, 0)  AS budget_best_higher_lower_streak,
       COALESCE(bs.higher_lower_correct, 0)       AS budget_higher_lower_correct,
       COALESCE(bs.cost_per_unit_correct, 0)      AS budget_cost_per_unit_correct,
       COALESCE(bs.countries_mastered, 0)         AS budget_distinct_countries,
       COALESCE(bs.mastered_project_value_usd, 0) AS budget_mastered_value_usd,
       COALESCE(bs.severe_misses, 0)              AS budget_severe_misses,
       COALESCE(rs.attempted_rivers, 0)          AS river_attempted,
       COALESCE(rs.solved_rivers, 0)             AS river_solved,
       COALESCE(ss.solved_countries, 0)          AS silhouette_solved,
       (SELECT COUNT(*) FROM cl_schema.silhouette_game_question WHERE is_active) AS silhouette_total,
       COALESCE(fs.solved_flags, 0)              AS flag_solved,
       (SELECT COUNT(*) FROM cl_schema.flag_game_question WHERE is_active) AS flag_total,
       COALESCE(qs.explored_countries, 0)            AS quiz_explored,
       (SELECT COUNT(*) FROM cl_schema.quiz_game_country WHERE is_active) AS quiz_total
FROM users u
LEFT JOIN user_profiles p ON p.user_id = u.id
LEFT JOIN user_budget_stats bs ON bs.user_id = u.id
LEFT JOIN cl_api_schema.user_river_stats rs ON rs.user_id = u.id
LEFT JOIN cl_api_schema.user_silhouette_stats ss ON ss.user_id = u.id
LEFT JOIN cl_api_schema.user_flag_stats fs ON fs.user_id = u.id
LEFT JOIN cl_api_schema.user_quiz_stats qs ON qs.user_id = u.id
WHERE u.id = :userId
