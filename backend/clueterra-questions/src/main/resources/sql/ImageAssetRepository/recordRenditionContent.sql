UPDATE cl_media_schema.image_rendition
SET byte_size = COALESCE(:byteSize, byte_size),
    content_sha256 = COALESCE(:contentSha256, content_sha256),
    mime_type = COALESCE(:contentType, mime_type),
    storage_status = 'AVAILABLE',
    last_storage_check_at = NOW(),
    updated_at = NOW()
WHERE storage_bucket = :storageBucket
  AND object_key = :objectPath
  AND storage_status <> 'DELETED'
