WITH current_media AS (
    SELECT *
    FROM cl_media_schema.image_current
    WHERE image_resource_id = :assetId
      AND (CAST(:renditionId AS BIGINT) IS NULL OR image_rendition_id = :renditionId)
    ORDER BY image_rendition_id NULLS FIRST
    LIMIT 1
)
SELECT cl_media_schema.register_image(
           current_media.provider,
           COALESCE(current_media.storage_bucket, :storageBucket),
           COALESCE(:objectPath, current_media.object_key),
           COALESCE(:originalImageUrl, current_media.original_url),
           COALESCE(:imageSourcePageUrl, current_media.source_page_url),
           COALESCE(:imageAuthorName, current_media.author_name),
           COALESCE(:imageAttributionText, current_media.attribution_text),
           COALESCE(:imageLicenseLabel, current_media.license_label),
           COALESCE(:imageLicenseUrl, current_media.license_url),
           COALESCE(:sourceSha1, current_media.source_sha1),
           current_media.external_id,
           COALESCE(:mimeType, current_media.mime_type),
           COALESCE(:width, current_media.width),
           COALESCE(:height, current_media.height),
           COALESCE(current_media.rendition_type, 'DISPLAY'),
           current_media.source_key
       )                               AS image_resource_id,
       current_media.image_rendition_id AS previous_rendition_id
FROM current_media
