WITH current_hint AS (
    SELECT hint.*,
           projection.image_url AS current_image_url
    FROM hints hint
    LEFT JOIN cl_media_schema.hint_projection projection
           ON projection.id = hint.id
    WHERE hint.id = :id
), registered_image AS (
    SELECT cl_media_schema.register_image(
        current_hint.source,
        'hint-images',
        :imageUrl,
        :originalImageUrl,
        :imageSourcePageUrl,
        :imageAuthorName,
        :imageAttributionText,
        :imageLicenseLabel,
        :imageLicenseUrl,
        p_source_key_override => cl_media_schema.preserved_source_key(
            current_hint.image_resource_id,
            current_hint.current_image_url,
            :imageUrl)) AS image_resource_id
    FROM current_hint
), updated AS (
    UPDATE hints
    SET image_resource_id = registered_image.image_resource_id,
        content_revision_hash = :contentRevisionHash,
        review_status = 'STALE',
        last_reviewed_at = NULL,
        last_review_run_id = NULL,
        updated_at = :now
    FROM registered_image
    WHERE hints.id = :id
    RETURNING hints.*
)
