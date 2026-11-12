SELECT object_key
FROM cl_media_schema.image_rendition
WHERE storage_bucket = :bucket AND storage_status <> 'DELETED'
