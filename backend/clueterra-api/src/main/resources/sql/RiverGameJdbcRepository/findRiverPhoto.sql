SELECT current_image.mime_type
FROM cl_schema.river_game_river_media media
JOIN cl_media_schema.image_current current_image
  ON current_image.image_resource_id = media.photo_resource_id
WHERE current_image.object_key = :objectKey
  AND current_image.storage_bucket = 'hint-images'
  AND current_image.lifecycle_status = 'ACTIVE'
  AND current_image.storage_status = 'AVAILABLE'
  AND EXISTS (
      SELECT 1 FROM cl_schema.river_game_question question
      WHERE question.river_key = media.river_key AND question.is_active
  )
LIMIT 1
