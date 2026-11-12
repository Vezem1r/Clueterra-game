WITH referenced_resources AS (
    SELECT plain_image_resource_id AS image_resource_id
    FROM cl_schema.river_game_question
    WHERE is_active = TRUE
    UNION
    SELECT borders_image_resource_id
    FROM cl_schema.river_game_question
    WHERE is_active = TRUE
    UNION
    SELECT media.photo_resource_id
    FROM cl_schema.river_game_river_media media
    WHERE media.photo_resource_id IS NOT NULL
      AND EXISTS (
          SELECT 1 FROM cl_schema.river_game_question question
          WHERE question.river_key = media.river_key AND question.is_active = TRUE
      )
    UNION
    SELECT photo_resource_id
    FROM cl_schema.silhouette_game_question
    WHERE photo_resource_id IS NOT NULL
    UNION
    SELECT image_resource_id
    FROM cl_schema.hints
    WHERE image_resource_id IS NOT NULL
      AND is_active = TRUE
    UNION
    SELECT image_resource_id
    FROM cl_schema.country_statistics
    WHERE image_resource_id IS NOT NULL
      AND is_current = TRUE
    UNION
    SELECT image_resource_id
    FROM cl_ai_schema.ai_generated_hint_proposal
    WHERE image_resource_id IS NOT NULL
      AND status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPLIED')
    UNION
    SELECT image.image_resource_id
    FROM cl_budget_schema.project_image image
    JOIN cl_budget_schema.project project ON project.id = image.project_id
    WHERE image.image_resource_id IS NOT NULL
      AND project.is_active = TRUE
    UNION
    SELECT image_resource_id
    FROM cl_budget_schema.question
    WHERE image_resource_id IS NOT NULL
      AND is_published = TRUE
    UNION
    SELECT image_resource_id
    FROM cl_budget_staging.wikidata_candidate
    WHERE image_resource_id IS NOT NULL
      AND image_status = 'AVAILABLE'
    UNION
    SELECT NULLIF(payload ->> 'imageResourceId', '')::BIGINT
    FROM cl_schema.daily_challenges challenge
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(challenge.hints_json::JSONB) payload
    WHERE challenge.is_active = TRUE
      AND challenge.challenge_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
      AND NULLIF(payload ->> 'imageResourceId', '') IS NOT NULL
    UNION
    SELECT source_question.image_resource_id
    FROM cl_budget_schema.daily_challenge challenge
    JOIN cl_budget_schema.daily_challenge_question challenge_question
      ON challenge_question.challenge_id = challenge.id
    JOIN cl_budget_schema.question_variant variant
      ON variant.id = challenge_question.question_variant_id
    JOIN cl_budget_schema.question source_question
      ON source_question.id = variant.source_question_id
    WHERE challenge.challenge_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
      AND source_question.image_resource_id IS NOT NULL
    UNION
    SELECT comparison_question.image_resource_id
    FROM cl_budget_schema.daily_challenge challenge
    JOIN cl_budget_schema.daily_challenge_question challenge_question
      ON challenge_question.challenge_id = challenge.id
    JOIN cl_budget_schema.question_variant variant
      ON variant.id = challenge_question.question_variant_id
    JOIN cl_budget_schema.question comparison_question
      ON comparison_question.id = variant.comparison_question_id
    WHERE challenge.challenge_date >= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::DATE
      AND comparison_question.image_resource_id IS NOT NULL
), refreshed AS (
    UPDATE cl_media_schema.image_rendition rendition
    SET orphaned_at = CASE
            WHEN referenced.image_resource_id IS NULL
                THEN COALESCE(rendition.orphaned_at, NOW())
            ELSE NULL
        END,
        updated_at = CASE
            WHEN (referenced.image_resource_id IS NULL) IS DISTINCT FROM (rendition.orphaned_at IS NOT NULL)
                THEN NOW()
            ELSE rendition.updated_at
        END
    FROM cl_media_schema.image_resource resource
    LEFT JOIN referenced_resources referenced ON referenced.image_resource_id = resource.id
    WHERE rendition.image_resource_id = resource.id
      AND rendition.storage_status IN ('AVAILABLE', 'MISSING', 'CORRUPT')
      AND (referenced.image_resource_id IS NULL) IS DISTINCT FROM (rendition.orphaned_at IS NOT NULL)
    RETURNING rendition.id
)
SELECT COUNT(*)::INTEGER FROM refreshed
