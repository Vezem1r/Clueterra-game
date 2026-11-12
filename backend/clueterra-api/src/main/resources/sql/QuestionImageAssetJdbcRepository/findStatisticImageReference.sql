WITH today_image_resources AS MATERIALIZED (
    SELECT DISTINCT NULLIF(payload ->> 'imageResourceId', '')::BIGINT AS image_resource_id
    FROM daily_challenges challenge
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
    WHERE challenge.is_active = TRUE
      AND challenge.challenge_date = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
      AND NULLIF(payload ->> 'imageResourceId', '') IS NOT NULL
)
SELECT COALESCE(asset.object_key, asset.download_url) AS image_url,
       asset.original_url AS original_image_url,
       asset.source_page_url AS image_source_page_url,
       statistic.source
FROM country_statistics statistic
LEFT JOIN LATERAL (
    SELECT media.*
    FROM cl_media_schema.image_current media
    WHERE media.image_resource_id = statistic.image_resource_id
      AND (
          (media.storage_bucket = 'hint-images' AND media.storage_status = 'AVAILABLE')
          OR (media.object_key IS NULL AND media.download_url ~ '^/api/assets/flags/[a-z]{2}\.svg$')
      )
      AND (
          media.lifecycle_status = 'ACTIVE'
          OR (
              media.lifecycle_status = 'QUARANTINED'
              AND EXISTS (
                  SELECT 1
                  FROM today_image_resources today
                  WHERE today.image_resource_id = media.image_resource_id
              )
          )
      )
    ORDER BY CASE media.rendition_type WHEN 'DISPLAY' THEN 0 ELSE 1 END
    LIMIT 1
) asset ON TRUE
WHERE statistic.id = :id
  AND COALESCE(asset.object_key, asset.download_url) IS NOT NULL
