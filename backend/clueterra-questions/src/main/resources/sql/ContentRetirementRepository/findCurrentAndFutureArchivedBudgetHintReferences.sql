SELECT challenge.id AS challenge_id,
       (payload ->> 'countryOrder')::INTEGER AS country_order,
       (payload ->> 'hintOrder')::INTEGER AS hint_order
FROM daily_challenges challenge
CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
JOIN hints_history archived
  ON archived.id = (payload ->> 'hintId')::BIGINT
WHERE challenge.is_active = TRUE
  AND challenge.challenge_date >= CURRENT_DATE
  AND archived.source = 'BUDGET_PROJECTS'
  AND archived.archive_reason = 'stale_source_regen'
ORDER BY challenge.challenge_date,
         challenge.challenge_level,
         country_order,
         hint_order;
