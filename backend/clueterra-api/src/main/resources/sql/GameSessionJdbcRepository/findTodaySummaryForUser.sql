SELECT gs.id                                       AS session_id,
       gs.challenge_level,
       gs.status,
       gs.current_country_idx,
       COUNT(gsc.id)::INT                          AS total_countries,
       COUNT(*) FILTER (WHERE gsc.is_guessed)::INT AS guessed_countries,
       COALESCE(SUM(gsc.hints_revealed), 0)::INT   AS total_hints_revealed,
       COALESCE(SUM(gsc.attempts_used), 0)::INT    AS total_attempts_used,
       gs.started_at,
       COALESCE(SUM(gsc.time_spent_ms), 0)::BIGINT AS active_time_ms,
       gs.completed_at,
       CASE
           WHEN gs.status = 'COMPLETED' THEN gs.total_xp_earned
           ELSE COALESCE(SUM(gsc.xp_earned), 0)::INT
       END                                          AS total_xp_earned
FROM user_daily_participation udp
         JOIN game_sessions gs ON gs.id = udp.session_id
         JOIN daily_challenges dc ON dc.id = gs.challenge_id
         JOIN game_session_countries gsc ON gsc.session_id = gs.id
WHERE udp.user_id = :userId
  AND udp.challenge_date = :today
  AND dc.challenge_date = :today
GROUP BY gs.id, gs.challenge_level, gs.status, gs.current_country_idx,
         gs.started_at, gs.completed_at, gs.total_xp_earned
