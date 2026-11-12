WITH suppressed AS (
    SELECT 1
    FROM hint_suppression
    WHERE country_id = :countryId
      AND source = :source
      AND source_indicator IS NOT DISTINCT FROM :sourceIndicator
      AND category = :category
      AND hint_type = :hintType
      AND action = 'DELETE'
      AND is_active = TRUE
), existing AS (
    SELECT hint.id,
           hint.content_hash,
           hint.hint_text,
           hint.editorial_text_locked,
           hint.review_retry_after_at,
           hint.image_resource_id,
           COALESCE(image.object_key, image.download_url) AS image_url
    FROM hints hint
    LEFT JOIN cl_media_schema.image_delivery image
           ON image.image_resource_id = hint.image_resource_id
    WHERE country_id = :countryId
      AND category = :category
      AND hint_type = :hintType
      AND source_indicator = :sourceIndicator
      AND NOT EXISTS (SELECT 1 FROM suppressed)
    LIMIT 1
), registered_image AS (
    SELECT CASE
        WHEN :hintType IN ('IMAGE_FACT', 'PANORAMA')
             AND NOT (COALESCE(existing.editorial_text_locked, FALSE)
                      AND existing.review_retry_after_at IS NULL
                      AND :source <> 'BUDGET_PROJECTS') THEN
            cl_media_schema.register_image(
                :source, 'hint-images', :imageUrl, :originalImageUrl,
                :imageSourcePageUrl, :imageAuthorName, :imageAttributionText,
                :imageLicenseLabel, :imageLicenseUrl,
                p_source_key_override => CASE
                    WHEN :source = 'BUDGET_PROJECTS'
                        THEN 'budget-projects:' || :imageUrl
                    ELSE cl_media_schema.preserved_source_key(
                        existing.image_resource_id,
                        existing.image_url,
                        :imageUrl)
                END)
        ELSE NULL
    END AS image_resource_id
    FROM (SELECT 1) seed
    LEFT JOIN existing ON TRUE
), soft_update AS (
    UPDATE hints
    SET hint_text = :hintText,
        difficulty = :difficulty,
        quality_score = :qualityScore,
        ambiguity_count = :ambiguityCount,
        source_year = :sourceYear,
        image_resource_id = (SELECT image_resource_id FROM registered_image),
        content_hash = :contentHash,
        content_revision_hash = :contentRevisionHash,
        review_status = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM :contentRevisionHash THEN 'STALE'
            ELSE hints.review_status
        END,
        last_reviewed_at = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM :contentRevisionHash THEN NULL
            ELSE hints.last_reviewed_at
        END,
        last_review_run_id = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM :contentRevisionHash THEN NULL
            ELSE hints.last_review_run_id
        END,
        review_claim_run_id = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM :contentRevisionHash THEN NULL
            ELSE hints.review_claim_run_id
        END,
        updated_at = :updatedAt
    WHERE id IN (
        SELECT id FROM existing
        WHERE (content_hash = :contentHash OR hint_text = :hintText)
          AND NOT (editorial_text_locked AND review_retry_after_at IS NULL))
    RETURNING id
), locked_budget_source_update AS (
    UPDATE hints
    SET difficulty = :difficulty,
        image_resource_id = registered_image.image_resource_id,
        content_revision_hash = CASE
            WHEN hints.image_resource_id IS DISTINCT FROM registered_image.image_resource_id
                THEN encode(sha256(convert_to(
                    trim(regexp_replace(hints.hint_text, '[[:space:]]+', ' ', 'g'))
                        || E'\n--image--\n' || trim(:imageUrl), 'UTF8')), 'hex')
            ELSE hints.content_revision_hash
        END,
        review_status = CASE
            WHEN hints.image_resource_id IS DISTINCT FROM registered_image.image_resource_id THEN 'STALE'
            ELSE hints.review_status
        END,
        last_reviewed_at = CASE
            WHEN hints.image_resource_id IS DISTINCT FROM registered_image.image_resource_id THEN NULL
            ELSE hints.last_reviewed_at
        END,
        last_review_run_id = CASE
            WHEN hints.image_resource_id IS DISTINCT FROM registered_image.image_resource_id THEN NULL
            ELSE hints.last_review_run_id
        END,
        review_claim_run_id = CASE
            WHEN hints.image_resource_id IS DISTINCT FROM registered_image.image_resource_id THEN NULL
            ELSE hints.review_claim_run_id
        END,
        updated_at = :updatedAt
    FROM registered_image
    WHERE hints.id IN (SELECT id FROM existing)
      AND :source = 'BUDGET_PROJECTS'
      AND hints.editorial_text_locked
      AND hints.review_retry_after_at IS NULL
      AND (hints.image_resource_id IS DISTINCT FROM registered_image.image_resource_id
           OR hints.difficulty IS DISTINCT FROM :difficulty)
    RETURNING hints.id
), archived AS (
    INSERT INTO hints_history (
        id, country_id, category, hint_type, hint_text, difficulty,
        quality_score, source, source_indicator, source_year, content_hash,
        ambiguity_count, times_used, times_correct, is_active, image_resource_id,
        original_created_at, archived_at, archive_reason)
    SELECT hint.id, hint.country_id, hint.category, hint.hint_type, hint.hint_text,
           hint.difficulty, hint.quality_score, hint.source, hint.source_indicator,
           hint.source_year, hint.content_hash, hint.ambiguity_count, hint.times_used,
           hint.times_correct, hint.is_active, hint.image_resource_id,
           hint.created_at, :archivedAt, 'replaced'
    FROM hints hint
    WHERE hint.id IN (SELECT id FROM existing)
      AND NOT (hint.editorial_text_locked AND hint.review_retry_after_at IS NULL)
      AND NOT EXISTS (SELECT 1 FROM soft_update)
    ON CONFLICT (id) DO NOTHING
    RETURNING id
), deleted AS (
    DELETE FROM hints WHERE id IN (SELECT id FROM archived)
), inserted AS (
    INSERT INTO hints (
        id, country_id, category, hint_type, hint_text, image_resource_id,
        difficulty, quality_score, source, source_indicator, source_year,
        content_hash, content_revision_hash, ambiguity_count, times_used,
        times_correct, is_active, review_status, last_reviewed_at,
        last_review_run_id, created_at, updated_at)
    SELECT :id, :countryId, :category, :hintType, :hintText,
           registered_image.image_resource_id, :difficulty, :qualityScore,
           :source, :sourceIndicator, :sourceYear, :contentHash,
           :contentRevisionHash, :ambiguityCount, :timesUsed, :timesCorrect,
           :isActive, :reviewStatus, :lastReviewedAt, :lastReviewRunId,
           :createdAt, :updatedAt
    FROM registered_image
    WHERE NOT EXISTS (SELECT 1 FROM soft_update)
      AND NOT EXISTS (SELECT 1 FROM existing
                      WHERE editorial_text_locked AND review_retry_after_at IS NULL)
      AND NOT EXISTS (SELECT 1 FROM suppressed)
    ON CONFLICT (country_id, content_hash) DO UPDATE
    SET category = EXCLUDED.category,
        hint_type = EXCLUDED.hint_type,
        difficulty = EXCLUDED.difficulty,
        quality_score = EXCLUDED.quality_score,
        source_indicator = EXCLUDED.source_indicator,
        source_year = EXCLUDED.source_year,
        ambiguity_count = EXCLUDED.ambiguity_count,
        image_resource_id = EXCLUDED.image_resource_id,
        content_revision_hash = EXCLUDED.content_revision_hash,
        review_status = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN 'STALE'
            ELSE hints.review_status
        END,
        last_reviewed_at = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN NULL
            ELSE hints.last_reviewed_at
        END,
        last_review_run_id = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN NULL
            ELSE hints.last_review_run_id
        END,
        review_claim_run_id = CASE
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN NULL
            ELSE hints.review_claim_run_id
        END,
        updated_at = EXCLUDED.updated_at
    WHERE NOT (hints.editorial_text_locked AND hints.review_retry_after_at IS NULL)
    RETURNING id
)
SELECT CASE
    WHEN EXISTS (SELECT 1 FROM soft_update) THEN 'SOFT_UPDATED'
    WHEN EXISTS (SELECT 1 FROM locked_budget_source_update) THEN 'SOFT_UPDATED'
    WHEN EXISTS (SELECT 1 FROM archived) THEN 'REPLACED'
    WHEN EXISTS (SELECT 1 FROM inserted) THEN 'INSERTED'
    ELSE 'NOOP'
END AS action
