UPDATE cl_media_schema.image_resource resource
SET validation_status = 'AVAILABLE',
    lifecycle_status = CASE
        WHEN EXISTS (
            SELECT 1
            FROM cl_media_schema.image_resource_suppression suppression
            WHERE suppression.image_resource_id = resource.id
              AND suppression.is_active = TRUE
        ) THEN resource.lifecycle_status
        ELSE 'ACTIVE'
    END,
    consecutive_failures = 0,
    next_validation_at = NOW() + INTERVAL '30 days',
    last_validated_at = NOW(),
    last_validation_error = NULL,
    updated_at = NOW()
WHERE resource.id = :assetId
