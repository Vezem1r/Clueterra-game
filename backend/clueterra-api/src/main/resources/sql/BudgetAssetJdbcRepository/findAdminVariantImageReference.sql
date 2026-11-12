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
 AND media.lifecycle_status = 'ACTIVE'
 AND media.storage_status = 'AVAILABLE'
WHERE variant.id = :variantId
ORDER BY CASE media.rendition_type WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
         media.image_rendition_id
LIMIT 1
