SELECT COALESCE(owner.owner_type, 'RESOURCE')        AS owner_type,
       owner.owner_id                                AS owner_id,
       COALESCE(owner.owner_label, media.source_key) AS owner_label,
       owner.country_code,
       COALESCE(owner.source, media.provider)        AS source,
       owner.source_indicator,
       media.object_key                              AS image_url,
       media.original_url                            AS original_image_url,
       media.source_page_url                         AS image_source_page_url,
       media.author_name                             AS image_author_name,
       media.attribution_text                        AS image_attribution_text,
       media.license_label                           AS image_license_label,
       media.license_url AS image_license_url,
       COALESCE(media.download_url, media.original_url) AS recovery_source_url,
       media.image_resource_id AS asset_id,
       media.image_rendition_id AS rendition_id,
       media.provider,
       media.source_key,
       COALESCE(
           media.storage_bucket,
           CASE WHEN owner.owner_type LIKE 'BUDGET_%' THEN 'budget-images' ELSE 'hint-images' END
       ) AS storage_bucket,
       media.object_key AS object_path,
       media.source_sha1,
       media.lifecycle_status,
       media.consecutive_failures,
       owner_counts.owner_count
FROM cl_media_schema.image_current media
LEFT JOIN LATERAL (
    SELECT linked.owner_type,
           linked.owner_id,
           linked.owner_label,
           linked.country_code,
           linked.source,
           linked.source_indicator
    FROM (
        SELECT 'HINT' AS owner_type,
               hint.id AS owner_id,
               hint.hint_text AS owner_label,
               country.iso_alpha2 AS country_code,
               hint.source,
               hint.source_indicator,
               1 AS priority
        FROM cl_schema.hints hint
        JOIN cl_schema.countries country ON country.id = hint.country_id
        WHERE hint.image_resource_id = media.image_resource_id
          AND hint.is_active = TRUE

        UNION ALL

        SELECT 'STATISTIC', statistic.id, statistic.indicator_name, country.iso_alpha2,
               statistic.source, statistic.indicator_code, 2
        FROM cl_schema.country_statistics statistic
        JOIN cl_schema.countries country ON country.id = statistic.country_id
        WHERE statistic.image_resource_id = media.image_resource_id
          AND statistic.is_current = TRUE

        UNION ALL

        SELECT 'BUDGET_PROJECT', project.id, project.name, project.country_code,
               'WIKIMEDIA_COMMONS', project.wikidata_id, 3
        FROM cl_budget_schema.project_image project_image
        JOIN cl_budget_schema.project project ON project.id = project_image.project_id
        WHERE project_image.image_resource_id = media.image_resource_id
          AND project.is_active = TRUE

        UNION ALL

        SELECT 'BUDGET_QUESTION', question.id, question.name_snapshot, question.country_code,
               'WIKIMEDIA_COMMONS', question.wikidata_id, 4
        FROM cl_budget_schema.question question
        WHERE question.image_resource_id = media.image_resource_id
          AND question.is_published = TRUE

        UNION ALL

        SELECT 'AI_PROPOSAL', proposal.id, proposal.hint_text, country.iso_alpha2,
               proposal.source, proposal.source_indicator, 5
        FROM cl_ai_schema.ai_generated_hint_proposal proposal
        JOIN cl_schema.countries country ON country.id = proposal.country_id
        WHERE proposal.image_resource_id = media.image_resource_id
          AND proposal.status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPLIED')

        UNION ALL

        SELECT 'RIVER_PHOTO', SUBSTRING(river_media.river_key FROM 2)::BIGINT, question.name_en,
               country.iso_alpha2, 'WIKIMEDIA_COMMONS', river_media.river_key, 6
        FROM cl_schema.river_game_river_media river_media
        JOIN cl_schema.river_game_question question
          ON question.river_key = river_media.river_key AND question.is_active = TRUE
        JOIN cl_schema.countries country ON country.id = question.country_id
        WHERE river_media.photo_resource_id = media.image_resource_id

        UNION ALL

        SELECT 'COUNTRY_PHOTO', country.id, country.name_en, country.iso_alpha2,
               'WIKIMEDIA_COMMONS', country.iso_alpha2, 7
        FROM cl_schema.silhouette_game_question question
        JOIN cl_schema.countries country ON country.id = question.country_id
        WHERE question.photo_resource_id = media.image_resource_id

    ) linked
    ORDER BY linked.priority, linked.owner_id
    LIMIT 1
) owner ON TRUE
JOIN LATERAL (
    SELECT COUNT(*)::INTEGER AS owner_count
    FROM (
        SELECT hint.id::TEXT AS owner_key FROM cl_schema.hints hint
        WHERE hint.image_resource_id = media.image_resource_id
          AND hint.is_active = TRUE
        UNION ALL
        SELECT statistic.id::TEXT FROM cl_schema.country_statistics statistic
        WHERE statistic.image_resource_id = media.image_resource_id
          AND statistic.is_current = TRUE
        UNION ALL
        SELECT proposal.id::TEXT FROM cl_ai_schema.ai_generated_hint_proposal proposal
        WHERE proposal.image_resource_id = media.image_resource_id
          AND proposal.status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPLIED')
        UNION ALL
        SELECT project_image.project_id::TEXT
        FROM cl_budget_schema.project_image project_image
        JOIN cl_budget_schema.project project ON project.id = project_image.project_id
        WHERE project_image.image_resource_id = media.image_resource_id
          AND project.is_active = TRUE
        UNION ALL
        SELECT question.id::TEXT FROM cl_budget_schema.question question
        WHERE question.image_resource_id = media.image_resource_id
          AND question.is_published = TRUE
        UNION ALL
        SELECT 'river:' || river_media.river_key
        FROM cl_schema.river_game_river_media river_media
        WHERE river_media.photo_resource_id = media.image_resource_id
          AND EXISTS (
              SELECT 1 FROM cl_schema.river_game_question question
              WHERE question.river_key = river_media.river_key AND question.is_active = TRUE
          )
        UNION ALL
        SELECT 'country:' || question.iso2
        FROM cl_schema.silhouette_game_question question
        WHERE question.photo_resource_id = media.image_resource_id
        UNION ALL
        SELECT candidate.wikidata_id || ':' || candidate.category
        FROM cl_budget_staging.wikidata_candidate candidate
        WHERE candidate.image_resource_id = media.image_resource_id
          AND candidate.image_status = 'AVAILABLE'
        UNION ALL
        SELECT challenge.id::TEXT || ':'
            || COALESCE(payload ->> 'countryOrder', '') || ':'
            || COALESCE(payload ->> 'hintOrder', '')
        FROM cl_schema.daily_challenges challenge
        CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
        WHERE challenge.is_active = TRUE
          AND challenge.challenge_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
          AND NULLIF(payload ->> 'imageResourceId', '')::BIGINT
                = media.image_resource_id
    ) linked_owners
) owner_counts ON TRUE
WHERE media.lifecycle_status = 'ACTIVE'
  AND NOT (media.provider = 'BUNDLED' AND media.source_key LIKE 'river-game:%')
  AND media.next_validation_at <= NOW()
  AND NOT (media.object_key IS NULL AND COALESCE(media.original_url, '') LIKE '/%')
  AND owner_counts.owner_count > 0
ORDER BY media.next_validation_at, media.image_resource_id, media.image_rendition_id NULLS FIRST
