SELECT COALESCE(stats.attempted_rivers, 0) AS attempted_rivers,
       COALESCE(stats.solved_rivers, 0) AS solved_rivers,
       COALESCE(stats.answer_attempts, 0) AS answer_attempts,
       COALESCE(stats.skips, 0) AS skips,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM cl_api_schema.user_xp_events event
        WHERE event.user_id = :userId AND event.source_type = 'RIVER_GAME') AS xp_awarded,
       (SELECT current_question_id FROM cl_api_schema.river_game_player_state
        WHERE user_id = :userId) AS current_question_id
FROM (SELECT CAST(:userId AS BIGINT) AS user_id) player
LEFT JOIN cl_api_schema.user_river_stats stats ON stats.user_id = player.user_id
