SELECT COALESCE(stats.attempted_countries, 0) AS attempted_countries,
       COALESCE(stats.solved_countries, 0) AS solved_countries,
       (SELECT COUNT(*) FROM cl_schema.silhouette_game_question WHERE is_active) AS total_countries,
       COALESCE(stats.answer_attempts, 0) AS answer_attempts,
       COALESCE(stats.skips, 0) AS skips,
       (SELECT COALESCE(SUM(event.xp_awarded), 0)
        FROM cl_api_schema.user_xp_events event
        WHERE event.user_id = :userId AND event.source_type = 'SILHOUETTE_GAME') AS xp_awarded
FROM (SELECT CAST(:userId AS BIGINT) AS user_id) player
LEFT JOIN cl_api_schema.user_silhouette_stats stats ON stats.user_id = player.user_id
