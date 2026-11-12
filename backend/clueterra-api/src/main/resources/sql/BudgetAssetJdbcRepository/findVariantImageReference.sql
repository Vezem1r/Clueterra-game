SELECT media.object_key,
       media.original_url AS fallback_image_url,
       media.original_url AS original_image_url
FROM cl_budget_schema.question_variant variant
JOIN cl_budget_schema.question question ON question.id = CASE
    WHEN :role = 'PRIMARY' THEN variant.source_question_id
    WHEN :role = 'COMPARISON' THEN variant.comparison_question_id
END
JOIN cl_media_schema.image_current media
  ON media.image_resource_id = question.image_resource_id
 AND media.storage_bucket = 'budget-images'
 AND media.lifecycle_status IN ('ACTIVE', 'QUARANTINED')
 AND media.storage_status = 'AVAILABLE'
WHERE variant.id = :variantId
  AND EXISTS (
      SELECT 1
      FROM cl_budget_schema.daily_challenge_question challenge_question
      JOIN cl_budget_schema.daily_challenge challenge
        ON challenge.id = challenge_question.challenge_id
      WHERE challenge_question.question_variant_id = variant.id
        AND challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
        AND challenge.status = 'PUBLISHED'
  )
ORDER BY CASE media.rendition_type WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
         media.image_rendition_id
LIMIT 1
