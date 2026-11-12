WITH ranked AS (
    SELECT hint.id,
           ROW_NUMBER() OVER (
               PARTITION BY hint.source
               ORDER BY hint.quality_score NULLS FIRST,
                        COALESCE(hint.last_reviewed_at, TO_TIMESTAMP(0)),
                        hint.id
           ) AS source_rank
    FROM cl_schema.hints hint
    WHERE hint.is_active = TRUE
      AND hint.source NOT IN ('MAPILLARY', 'AI_GENERATED')
      AND (hint.review_status IN ('NOT_REVIEWED', 'STALE', 'FAILED')
           OR (hint.review_status = 'REVIEWED' AND hint.review_retry_after_at <= NOW()))
      AND NOT EXISTS (
          SELECT 1
          FROM cl_ai_schema.ai_hint_change_item item
          WHERE item.hint_id = hint.id
            AND item.status IN ('PENDING', 'APPROVED')
            AND item.original_snapshot ->> 'contentRevisionHash'
                = COALESCE(hint.content_revision_hash, hint.content_hash)
      )
), selected AS (
    SELECT hint.id
    FROM cl_schema.hints hint
    JOIN ranked ON ranked.id = hint.id
    WHERE hint.is_active = TRUE
    ORDER BY ranked.source_rank,
             hint.quality_score NULLS FIRST,
             CASE hint.review_status
                 WHEN 'NOT_REVIEWED' THEN 0
                 WHEN 'STALE' THEN 1
                 WHEN 'FAILED' THEN 2
                 WHEN 'REVIEWED' THEN 3
                 ELSE 3
             END,
             hint.id
    FOR UPDATE OF hint SKIP LOCKED
    LIMIT :limit
), claimed AS (
    UPDATE cl_schema.hints hint
    SET review_status = 'IN_REVIEW',
        review_claim_run_id = :runId,
        updated_at = NOW()
    FROM selected
    WHERE hint.id = selected.id
    RETURNING hint.*
)
SELECT claimed.id AS hint_id,
       claimed.country_id,
       country.iso_alpha2 AS country_code,
       country.name_en AS country_name,
       country.region AS country_region,
       country.subregion AS country_subregion,
       country.capital AS country_capital,
       country.difficulty_modifier,
       claimed.category,
       claimed.hint_type,
       claimed.hint_text,
       COALESCE(media.object_key, media.download_url) AS image_url,
       media.original_url AS original_image_url,
       claimed.difficulty,
       claimed.quality_score,
       claimed.source,
       claimed.source_indicator,
       claimed.source_year,
       COALESCE(claimed.content_revision_hash, claimed.content_hash) AS content_revision_hash,
       claimed.review_status,
       claimed.updated_at,
       claimed.last_reviewed_at
FROM claimed
JOIN cl_schema.countries country ON country.id = claimed.country_id
LEFT JOIN cl_media_schema.image_delivery media ON media.image_resource_id = claimed.image_resource_id
ORDER BY claimed.id
