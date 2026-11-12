UPDATE cl_media_schema.image_rendition
SET storage_status = CASE WHEN :present THEN 'AVAILABLE' ELSE 'MISSING' END,
    last_storage_check_at = NOW(),
    updated_at = NOW()
WHERE id = :renditionId
  AND storage_status <> 'DELETED'
