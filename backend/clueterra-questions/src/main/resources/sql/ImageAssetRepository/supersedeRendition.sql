UPDATE cl_media_schema.image_rendition
SET storage_status = 'PENDING_DELETE',
    orphaned_at = COALESCE(orphaned_at, NOW()),
    updated_at = NOW()
WHERE id = :renditionId
  AND object_key <> :objectPath
