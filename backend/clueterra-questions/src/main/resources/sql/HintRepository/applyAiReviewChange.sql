WITH existing AS (
    SELECT projection.*
    FROM cl_media_schema.hint_projection projection
    WHERE projection.id = :hintId
), registered_image AS (
    SELECT CASE
        WHEN CAST(:imageUrl AS TEXT) IS NULL THEN NULL
        WHEN existing.hint_type IN ('IMAGE_FACT', 'PANORAMA') THEN
            cl_media_schema.register_image(
                existing.source,
                'hint-images',
                :imageUrl,
                existing.original_image_url,
                existing.image_source_page_url,
                existing.image_author_name,
                existing.image_attribution_text,
                existing.image_license_label,
                existing.image_license_url,
                p_source_key_override => resource.source_key)
        ELSE NULL
    END AS image_resource_id
    FROM existing
    LEFT JOIN cl_media_schema.image_resource resource
           ON resource.id = existing.image_resource_id
), updated AS (
    UPDATE hints
    SET hint_text = :hintText,
        image_resource_id = (SELECT image_resource_id FROM registered_image),
        difficulty = :difficulty,
        quality_score = :qualityScore,
        is_active = :isActive,
        content_revision_hash = :contentRevisionHash,
        review_status = 'REVIEWED',
        last_reviewed_at = :reviewedAt,
        last_review_run_id = :reviewRunId,
        review_retry_after_at = :reviewRetryAfterAt,
        editorial_text_locked = :editorialTextLocked,
        review_claim_run_id = NULL,
        updated_at = :reviewedAt
    WHERE id = :hintId
      AND (review_claim_run_id = :reviewRunId OR review_claim_run_id IS NULL)
      AND (
          (CAST(:expectedRevisionHash AS TEXT) IS NOT NULL
              AND COALESCE(content_revision_hash, content_hash) = CAST(:expectedRevisionHash AS TEXT))
          OR (CAST(:expectedRevisionHash AS TEXT) IS NULL
              AND hint_text IS NOT DISTINCT FROM :expectedHintText
              AND image_resource_id IS NOT DISTINCT FROM :expectedImageResourceId)
      )
    RETURNING *
)
SELECT updated.*,
       COALESCE(image.object_key, image.download_url) AS image_url,
       image.original_url AS original_image_url,
       image.source_page_url AS image_source_page_url,
       image.author_name AS image_author_name,
       image.attribution_text AS image_attribution_text,
       image.license_label AS image_license_label,
       image.license_url AS image_license_url
FROM updated
LEFT JOIN cl_media_schema.image_delivery image
       ON image.image_resource_id = updated.image_resource_id
