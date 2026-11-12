SELECT challenge.id AS challenge_id,
       (payload ->> 'countryOrder')::INTEGER AS country_order,
       (payload ->> 'hintOrder')::INTEGER AS hint_order
FROM daily_challenges challenge
CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
JOIN hints hint ON hint.id = (payload ->> 'hintId')::BIGINT
JOIN hint_suppression suppression
  ON suppression.action = 'DELETE'
 AND suppression.is_active = TRUE
 AND suppression.country_id = hint.country_id
 AND suppression.source = hint.source
 AND suppression.category = hint.category
 AND suppression.hint_type = hint.hint_type
 AND suppression.source_indicator IS NOT DISTINCT FROM hint.source_indicator
WHERE challenge.is_active = TRUE
  AND challenge.challenge_date > CURRENT_DATE
  AND hint.is_active = FALSE
ORDER BY challenge.challenge_date,
         challenge.challenge_level,
         country_order,
         hint_order
