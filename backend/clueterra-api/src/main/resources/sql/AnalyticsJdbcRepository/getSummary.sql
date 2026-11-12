SELECT COALESCE(SUM(total_sessions), 0)           AS total_sessions,
       COALESCE(SUM(completed_sessions), 0)       AS completed_sessions,
       COALESCE(SUM(abandoned_sessions), 0)       AS abandoned_sessions,
       COALESCE(SUM(unique_users), 0)             AS unique_users,
       COALESCE(SUM(total_guesses), 0)            AS total_guesses,
       COALESCE(SUM(correct_guesses), 0)          AS correct_guesses,
       COALESCE(AVG(avg_attempts_per_country), 0) AS avg_attempts_per_country,
       COALESCE(
               SUM(avg_time_per_country_ms::NUMERIC * total_sessions)
                   / NULLIF(SUM(total_sessions), 0), 0
       )                                          AS avg_time_per_session_ms
FROM game_statistics
WHERE date BETWEEN :startDate AND :endDate
