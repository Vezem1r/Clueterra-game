WITH completed_sessions AS (SELECT gs.id
                            FROM game_sessions gs
                                     JOIN daily_challenges dc ON dc.id = gs.challenge_id
                            WHERE dc.challenge_date = :today
                              AND gs.challenge_level = :challengeLevel
                              AND gs.status = :completedStatus),
     country_stats AS (SELECT gsc.country_id,
                              COUNT(*)::INT                                      AS times_featured,
                              COUNT(*) FILTER (WHERE gsc.is_guessed)::INT        AS times_guessed_correct,
                              ROUND(AVG(gsc.attempts_used)::NUMERIC, 2)          AS avg_attempts_needed,
                              ROUND(AVG(gsc.hints_revealed)::NUMERIC, 2)         AS avg_hints_needed,
                              COALESCE(ROUND(AVG(gsc.time_spent_ms))::BIGINT, 0) AS avg_active_time_ms
                       FROM game_session_countries gsc
                                JOIN completed_sessions cs ON cs.id = gsc.session_id
                       GROUP BY gsc.country_id
                       HAVING COUNT(*) >= :minSamples)
SELECT stats.country_id,
       c.iso_alpha2,
       c.name_en,
       c.flag_emoji,
       c.flag_url,
       c.region,
       stats.times_featured,
       stats.times_guessed_correct,
       stats.avg_attempts_needed,
       stats.avg_hints_needed,
       stats.avg_active_time_ms,
       COALESCE(ROUND(stats.times_guessed_correct::NUMERIC / NULLIF(stats.times_featured, 0) * 100, 1),
                0)         AS success_rate,
       confused.country_id AS confused_country_id,
       confused.iso_alpha2 AS confused_iso_alpha2,
       confused.name_en    AS confused_name_en,
       confused.flag_emoji AS confused_flag_emoji,
       confused.flag_url   AS confused_flag_url,
       confused.region     AS confused_region
FROM country_stats stats
         JOIN countries c ON c.id = stats.country_id
         LEFT JOIN LATERAL (
    SELECT guessed.id AS country_id,
           guessed.iso_alpha2,
           guessed.name_en,
           guessed.flag_emoji,
           guessed.flag_url,
           guessed.region
    FROM guess_attempts ga
             JOIN game_session_countries gsc ON gsc.id = ga.session_country_id
             JOIN countries guessed ON guessed.id = ga.guessed_country_id
    WHERE gsc.country_id = stats.country_id
      AND gsc.session_id IN (SELECT id FROM completed_sessions)
      AND NOT ga.is_correct
    GROUP BY guessed.id, guessed.iso_alpha2, guessed.name_en, guessed.flag_emoji, guessed.flag_url, guessed.region
    ORDER BY COUNT(*) DESC, guessed.name_en
    LIMIT 1
    ) confused ON TRUE
ORDER BY success_rate,
         stats.avg_attempts_needed DESC,
         stats.avg_hints_needed DESC,
         stats.avg_active_time_ms DESC,
         stats.times_featured DESC,
         c.name_en
LIMIT 1
