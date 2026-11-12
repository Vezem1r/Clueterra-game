SELECT image.mime_type
FROM cl_schema.silhouette_game_question question
JOIN cl_media_schema.image_current image ON image.image_resource_id = question.photo_resource_id
WHERE image.object_key = :objectKey
  AND image.storage_bucket = 'hint-images'
  AND image.lifecycle_status = 'ACTIVE'
  AND image.storage_status = 'AVAILABLE'
LIMIT 1
