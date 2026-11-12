SELECT resource.lifecycle_status
FROM cl_media_schema.image_resource resource
WHERE resource.provider = 'WIKIMEDIA_COMMONS'
  AND resource.source_key = cl_media_schema.image_source_key(
      'WIKIMEDIA_COMMONS', NULL, NULL, NULL, NULL, :originalUrl, :sourcePageUrl)
