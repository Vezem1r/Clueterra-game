SELECT id AS rendition_id,
       storage_bucket,
       object_key
FROM cl_media_schema.image_rendition
WHERE storage_status IN ('AVAILABLE', 'MISSING', 'CORRUPT', 'PENDING_DELETE')
  AND orphaned_at IS NOT NULL
  AND orphaned_at < NOW() - MAKE_INTERVAL(hours => :graceHours)
ORDER BY orphaned_at, id
LIMIT 500
