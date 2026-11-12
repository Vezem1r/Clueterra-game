SELECT challenge_level AS level,
       completed_runs,
       countries_played,
       countries_solved,
       last_completed_at,
       COALESCE(
               CASE
                   WHEN countries_played > 0
                       THEN ROUND(100.0 * countries_solved / NULLIF(countries_played, 0), 1)
                   ELSE 0
                   END,
               0
       )               AS solve_rate
FROM user_difficulty_stats
WHERE user_id = :userId
ORDER BY CASE challenge_level
             WHEN 'EASY' THEN 1
             WHEN 'MEDIUM' THEN 2
             WHEN 'HARD' THEN 3
             ELSE 4
             END
