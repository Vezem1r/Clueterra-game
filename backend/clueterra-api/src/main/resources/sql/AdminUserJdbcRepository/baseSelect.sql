SELECT u.id,
       u.email,
       u.username,
       u.role,
       u.enabled,
       u.created_at,
       u.reports_muted,
       p.display_name,
       p.avatar_url,
       p.total_xp,
       p.current_level,
       p.challenges_played,
       p.challenges_won,
       p.current_streak,
       p.best_streak,
       COALESCE(p.streak_freezes, 0) AS streak_freezes,
       COALESCE(bs.completed_challenges, 0) AS budget_completed_challenges,
       COALESCE(bs.answered_questions, 0) AS budget_answered_questions,
       COALESCE(ROUND(100.0 * bs.full_xp_answers / NULLIF(bs.answered_questions, 0), 1), 0)
           AS budget_full_xp_rate,
       COALESCE(ROUND(bs.accuracy_total / NULLIF(bs.answered_questions, 0), 1), 0)
           AS budget_average_accuracy,
       COALESCE(bs.clean_sweeps, 0) AS budget_clean_sweeps,
       COALESCE(bs.unique_projects_explored, 0) AS budget_projects_explored,
       COALESCE(bs.higher_lower_best_streak, 0) AS budget_best_higher_lower_streak,
       COALESCE(bs.mastered_project_value_usd, 0) AS budget_projects_mastered_value_usd,
       COALESCE(rs.attempted_rivers, 0) AS river_attempted,
       COALESCE(rs.solved_rivers, 0) AS river_solved,
       COALESCE(rs.answer_attempts, 0) AS river_guesses,
       COALESCE(rs.skips, 0) AS river_skips,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM user_xp_events event
        WHERE event.user_id = u.id AND event.source_type = 'RIVER_GAME') AS river_xp_awarded,
       state.current_question_id AS river_current_question_id,
       COALESCE(ss.attempted_countries, 0) AS silhouette_attempted,
       COALESCE(ss.solved_countries, 0) AS silhouette_solved,
       (SELECT COUNT(*) FROM cl_schema.silhouette_game_question WHERE is_active) AS silhouette_total,
       COALESCE(ss.answer_attempts, 0) AS silhouette_guesses,
       COALESCE(ss.skips, 0) AS silhouette_skips,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM user_xp_events event
        WHERE event.user_id = u.id AND event.source_type = 'SILHOUETTE_GAME') AS silhouette_xp_awarded,
       COALESCE(fs.attempted_flags, 0) AS flag_attempted,
       COALESCE(fs.solved_flags, 0) AS flag_solved,
       (SELECT COUNT(*) FROM cl_schema.flag_game_question WHERE is_active) AS flag_total,
       COALESCE(fs.answer_attempts, 0) AS flag_guesses,
       COALESCE(fs.skips, 0) AS flag_skips,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM user_xp_events event
        WHERE event.user_id = u.id AND event.source_type = 'FLAG_GAME') AS flag_xp_awarded,
       COALESCE(qs.explored_countries, 0) AS quiz_explored,
       COALESCE(qs.mastered_countries, 0) AS quiz_mastered,
       (SELECT COUNT(*) FROM cl_schema.quiz_game_country WHERE is_active) AS quiz_total,
       COALESCE(qs.completed_levels, 0) AS quiz_completed_levels,
       (SELECT COALESCE(SUM(levels), 0) FROM cl_schema.quiz_game_country WHERE is_active) AS quiz_total_levels,
       COALESCE(qs.answered_questions, 0) AS quiz_answered,
       COALESCE(qs.correct_answers, 0) AS quiz_correct,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM user_xp_events event
        WHERE event.user_id = u.id AND event.source_type = 'QUIZ_GAME') AS quiz_xp_awarded,
       u.profile_locked,
       u.is_guest
FROM users u
LEFT JOIN user_profiles p ON u.id = p.user_id
LEFT JOIN user_budget_stats bs ON u.id = bs.user_id
LEFT JOIN user_river_stats rs ON u.id = rs.user_id
LEFT JOIN river_game_player_state state ON u.id = state.user_id
LEFT JOIN user_silhouette_stats ss ON u.id = ss.user_id
LEFT JOIN user_flag_stats fs ON u.id = fs.user_id
LEFT JOIN user_quiz_stats qs ON u.id = qs.user_id
