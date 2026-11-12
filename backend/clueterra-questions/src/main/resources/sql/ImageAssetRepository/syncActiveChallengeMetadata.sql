UPDATE daily_challenges challenge
SET hints_json = (
    SELECT JSONB_AGG(
        CASE
            WHEN COALESCE(
                NULLIF(element.payload ->> 'imageResourceId', '')::BIGINT,
                hint.image_resource_id
            ) = :assetId THEN
                element.payload || JSONB_STRIP_NULLS(JSONB_BUILD_OBJECT(
                    'imageResourceId', :assetId,
                    'imageUrl', media.object_key,
                    'originalImageUrl', media.original_url,
                    'imageSourcePageUrl', media.source_page_url,
                    'imageAuthorName', media.author_name,
                    'imageAttributionText', media.attribution_text,
                    'imageLicenseLabel', media.license_label,
                    'imageLicenseUrl', media.license_url
                ))
            ELSE element.payload
        END
        ORDER BY element.ordinality
    )::TEXT
    FROM JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB)
        WITH ORDINALITY AS element(payload, ordinality)
    LEFT JOIN hints hint ON hint.id = NULLIF(element.payload ->> 'hintId', '')::BIGINT
    JOIN LATERAL (
        SELECT *
        FROM cl_media_schema.image_current current_media
        WHERE current_media.image_resource_id = :assetId
          AND current_media.storage_bucket = 'hint-images'
        ORDER BY CASE current_media.rendition_type WHEN 'DISPLAY' THEN 0 ELSE 1 END,
                 current_media.image_rendition_id
        LIMIT 1
    ) media ON TRUE
)
WHERE challenge.is_active = TRUE
  AND challenge.challenge_date >= CURRENT_DATE
  AND EXISTS (
      SELECT 1
      FROM JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
      LEFT JOIN hints hint ON hint.id = NULLIF(payload ->> 'hintId', '')::BIGINT
      WHERE COALESCE(
          NULLIF(payload ->> 'imageResourceId', '')::BIGINT,
          hint.image_resource_id
      ) = :assetId
  )
