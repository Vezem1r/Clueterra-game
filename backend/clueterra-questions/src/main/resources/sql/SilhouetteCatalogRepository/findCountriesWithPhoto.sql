SELECT question.iso2
FROM cl_schema.silhouette_game_question question
WHERE question.photo_resource_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM cl_media_schema.image_current current_image
      WHERE current_image.image_resource_id = question.photo_resource_id
        AND current_image.lifecycle_status = 'ACTIVE'
        AND current_image.storage_status = 'AVAILABLE'
        AND current_image.object_key LIKE 'country-photos/%'
  )
