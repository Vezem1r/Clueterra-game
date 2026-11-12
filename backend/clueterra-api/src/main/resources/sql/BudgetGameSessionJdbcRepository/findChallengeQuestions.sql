SELECT
    variant.id AS variant_id,
    challenge_question.position,
    variant.question_type,
    variant.difficulty,
    variant.prompt_payload::TEXT AS prompt_payload,
    CASE
        WHEN JSONB_TYPEOF(variant.reveal_payload -> 'hints') = 'array'
            THEN JSONB_ARRAY_LENGTH(variant.reveal_payload -> 'hints')
        ELSE 0
    END AS available_hint_count,
    primary_media.object_key AS primary_image_object_key,
    primary_question.image_alt_text AS primary_image_alt_text,
    primary_media.author_name AS primary_image_author_name,
    primary_media.attribution_text AS primary_image_attribution_text,
    primary_media.license_label AS primary_image_license_label,
    primary_media.license_url AS primary_image_license_url,
    primary_media.source_page_url AS primary_image_source_page_url,
    comparison_media.object_key AS comparison_image_object_key,
    comparison_question.image_alt_text AS comparison_image_alt_text,
    comparison_media.author_name AS comparison_image_author_name,
    comparison_media.attribution_text AS comparison_image_attribution_text,
    comparison_media.license_label AS comparison_image_license_label,
    comparison_media.license_url AS comparison_image_license_url,
    comparison_media.source_page_url AS comparison_image_source_page_url
FROM cl_budget_schema.daily_challenge_question challenge_question
JOIN cl_budget_schema.daily_challenge challenge
    ON challenge.id = challenge_question.challenge_id
JOIN cl_budget_schema.question_variant variant
    ON variant.id = challenge_question.question_variant_id
JOIN cl_budget_schema.question primary_question
    ON primary_question.id = variant.source_question_id
LEFT JOIN cl_budget_schema.question comparison_question
    ON comparison_question.id = variant.comparison_question_id
JOIN LATERAL (
    SELECT current_media.*
    FROM cl_media_schema.image_current current_media
    WHERE current_media.image_resource_id = primary_question.image_resource_id
      AND current_media.storage_bucket = 'budget-images'
      AND (
          current_media.lifecycle_status = 'ACTIVE'
          OR (
              current_media.lifecycle_status = 'QUARANTINED'
              AND challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
              AND challenge.status = 'PUBLISHED'
          )
      )
      AND current_media.storage_status = 'AVAILABLE'
    ORDER BY CASE current_media.rendition_type
        WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
        current_media.image_rendition_id
    LIMIT 1
) primary_media ON TRUE
LEFT JOIN LATERAL (
    SELECT current_media.*
    FROM cl_media_schema.image_current current_media
    WHERE current_media.image_resource_id = comparison_question.image_resource_id
      AND current_media.storage_bucket = 'budget-images'
      AND (
          current_media.lifecycle_status = 'ACTIVE'
          OR (
              current_media.lifecycle_status = 'QUARANTINED'
              AND challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
              AND challenge.status = 'PUBLISHED'
          )
      )
      AND current_media.storage_status = 'AVAILABLE'
    ORDER BY CASE current_media.rendition_type
        WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
        current_media.image_rendition_id
    LIMIT 1
) comparison_media ON TRUE
WHERE challenge_question.challenge_id = :challengeId
  AND (comparison_question.id IS NULL OR comparison_media.image_resource_id IS NOT NULL)
ORDER BY challenge_question.position
