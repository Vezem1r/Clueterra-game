SELECT dc.id,
       dc.challenge_date,
       dc.challenge_level,
       dc.difficulty,
       dc.total_countries,
       dc.total_hints,
       dc.countries_json,
       dc.hints_json,
       COALESCE(country_stats.continent_count, 0)::INT AS continent_count,
       session_stats.avg_xp                           AS avg_xp
FROM daily_challenges dc
         LEFT JOIN LATERAL (
    SELECT COUNT(DISTINCT c.region) AS continent_count
    FROM jsonb_array_elements(dc.countries_json::jsonb) country_entry
             JOIN cl_schema.countries c ON c.id = (country_entry ->> 'countryId')::BIGINT
) country_stats ON TRUE
         LEFT JOIN LATERAL (
    SELECT CASE WHEN COUNT(*) > 0 THEN ROUND(AVG(gs.total_xp_earned))::INT END AS avg_xp
    FROM game_sessions gs
    WHERE gs.challenge_id = dc.id
      AND gs.status = :completedStatus
) session_stats ON TRUE
WHERE dc.challenge_date = :today
  AND dc.challenge_level = :level
  AND dc.is_active = TRUE
