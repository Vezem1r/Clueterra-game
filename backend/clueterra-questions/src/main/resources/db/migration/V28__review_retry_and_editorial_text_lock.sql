CREATE OR REPLACE VIEW cl_media_schema.hint_projection AS
SELECT hint.id,
       hint.country_id,
       hint.category,
       hint.hint_type,
       hint.hint_text,
       hint.difficulty,
       hint.quality_score,
       hint.source,
       hint.source_indicator,
       hint.source_year,
       hint.content_hash,
       hint.content_revision_hash,
       hint.review_status,
       hint.last_reviewed_at,
       hint.last_review_run_id,
       hint.ambiguity_count,
       hint.times_used,
       hint.times_correct,
       hint.is_active,
       hint.created_at,
       hint.updated_at,
       hint.image_resource_id,
       COALESCE(image.object_key, image.download_url) AS image_url,
       image.original_url AS original_image_url,
       image.source_page_url AS image_source_page_url,
       image.author_name AS image_author_name,
       image.attribution_text AS image_attribution_text,
       image.license_label AS image_license_label,
       image.license_url AS image_license_url,
       hint.review_retry_after_at,
       hint.editorial_text_locked
FROM cl_schema.hints hint
LEFT JOIN cl_media_schema.image_delivery image
       ON image.image_resource_id = hint.image_resource_id;

COMMENT ON VIEW cl_media_schema.hint_projection IS
    'Read model for hints; image metadata is resolved from the canonical media schema.';
