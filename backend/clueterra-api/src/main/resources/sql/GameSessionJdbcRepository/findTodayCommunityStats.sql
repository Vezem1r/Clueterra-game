WITH completed_sessions AS (SELECT gs.id,
                                   gs.total_xp_earned
                            FROM game_sessions gs
                                     JOIN daily_challenges dc ON dc.id = gs.challenge_id
                            WHERE dc.challenge_date = :today
                              AND gs.challenge_level = :challengeLevel
                              AND gs.status = :completedStatus),
     completed_countries AS (SELECT gsc.*
                             FROM game_session_countries gsc
                                      JOIN completed_sessions cs ON cs.id = gsc.session_id),
     completed_attempts AS (SELECT ga.*
                            FROM guess_attempts ga
                                     JOIN completed_countries cc ON cc.id = ga.session_country_id),
     session_times AS (SELECT cc.session_id,
                              SUM(cc.time_spent_ms)::BIGINT AS active_time_ms
                       FROM completed_countries cc
                       GROUP BY cc.session_id)
SELECT COALESCE((SELECT COUNT(*)::INT FROM completed_sessions), 0)                           AS completed_sessions_count,
       COALESCE((SELECT ROUND(AVG(total_xp_earned))::INT FROM completed_sessions),
                0)                                                                           AS avg_xp_per_completed_run,
       COALESCE((SELECT ROUND(AVG(active_time_ms))::BIGINT FROM session_times),
                0)                                                                           AS avg_active_time_per_session_ms,
       COALESCE((SELECT ROUND(AVG(attempts_used)::NUMERIC, 2) FROM completed_countries),
                0)                                                                           AS avg_attempts_per_country,
       COALESCE((SELECT ROUND(AVG(hints_revealed)::NUMERIC, 2) FROM completed_countries), 0) AS avg_hints_per_country,
       COALESCE(
               (SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE is_correct) / NULLIF(COUNT(*), 0), 1)
                FROM completed_attempts),
               0
       )                                                                                     AS accuracy_rate
