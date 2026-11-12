SELECT COALESCE(stats.explored_countries, 0) AS explored_countries,
       COALESCE(stats.mastered_countries, 0) AS mastered_countries,
       catalog.total_countries,
       COALESCE(stats.completed_levels, 0) AS completed_levels,
       catalog.total_levels,
       COALESCE(stats.answered_questions, 0) AS answered_questions,
       COALESCE(stats.correct_answers, 0) AS correct_answers,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM cl_api_schema.user_xp_events event
        WHERE event.user_id = :userId AND event.source_type = 'QUIZ_GAME') AS xp_awarded
FROM (SELECT CAST(:userId AS BIGINT) AS user_id) player
CROSS JOIN (
    SELECT COUNT(*) AS total_countries, COALESCE(SUM(levels), 0) AS total_levels
    FROM cl_schema.quiz_game_country
    WHERE is_active
) catalog
LEFT JOIN cl_api_schema.user_quiz_stats stats ON stats.user_id = player.user_id
