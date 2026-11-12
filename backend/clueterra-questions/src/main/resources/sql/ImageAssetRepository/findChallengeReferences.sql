SELECT challenge.id AS challenge_id,
       (payload ->> 'countryOrder')::INTEGER AS country_order,
       (payload ->> 'hintOrder')::INTEGER AS hint_order
FROM daily_challenges challenge
CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
LEFT JOIN hints hint ON hint.id = NULLIF(payload ->> 'hintId', '')::BIGINT
WHERE challenge.is_active = TRUE
  AND challenge.challenge_date >= CASE WHEN :includeToday THEN CURRENT_DATE ELSE CURRENT_DATE + 1 END
  AND COALESCE(
      NULLIF(payload ->> 'imageResourceId', '')::BIGINT,
      hint.image_resource_id
  ) = :assetId
ORDER BY challenge.challenge_date, challenge.challenge_level, country_order, hint_order
