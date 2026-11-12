WITH current_owner AS (
    SELECT hint.image_resource_id,
           hint.editorial_text_locked,
           hint.review_retry_after_at,
           COALESCE(image.object_key, image.download_url) AS image_url
    FROM hints hint
    LEFT JOIN cl_media_schema.image_delivery image
           ON image.image_resource_id = hint.image_resource_id
    WHERE hint.country_id = :countryId
      AND hint.content_hash = :contentHash
), registered_image AS (
    SELECT CASE
        WHEN :hintType IN ('IMAGE_FACT', 'PANORAMA')
             AND NOT (COALESCE(current_owner.editorial_text_locked, FALSE)
                      AND current_owner.review_retry_after_at IS NULL) THEN
            cl_media_schema.register_image(
                :source, 'hint-images', :imageUrl, :originalImageUrl,
                :imageSourcePageUrl, :imageAuthorName, :imageAttributionText,
                :imageLicenseLabel, :imageLicenseUrl,
                p_source_key_override => CASE
                    WHEN :source = 'BUDGET_PROJECTS'
                        THEN 'budget-projects:' || :imageUrl
                    ELSE cl_media_schema.preserved_source_key(
                        current_owner.image_resource_id,
                        current_owner.image_url,
                        :imageUrl)
                END)
        ELSE NULL
    END AS image_resource_id
    FROM (SELECT 1) seed
    LEFT JOIN current_owner ON TRUE
), persisted AS (
    INSERT INTO hints (
        id, country_id, category, hint_type, hint_text, image_resource_id,
        difficulty, quality_score, source, source_indicator, source_year,
        content_hash, content_revision_hash, ambiguity_count, times_used,
        times_correct, is_active, review_status, last_reviewed_at,
        last_review_run_id, created_at, updated_at)
    SELECT :id, :countryId, :category, :hintType, :hintText, image_resource_id,
           :difficulty, :qualityScore, :source, :sourceIndicator, :sourceYear,
           :contentHash, :contentRevisionHash, :ambiguityCount, :timesUsed,
           :timesCorrect, :isActive, :reviewStatus, :lastReviewedAt,
           :lastReviewRunId, :createdAt, :updatedAt
    FROM registered_image
    ON CONFLICT (country_id, content_hash) DO UPDATE
    SET hint_text = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                         THEN hints.hint_text ELSE EXCLUDED.hint_text END,
        image_resource_id = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                                 THEN hints.image_resource_id ELSE EXCLUDED.image_resource_id END,
        difficulty = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                          THEN hints.difficulty ELSE EXCLUDED.difficulty END,
        quality_score = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                             THEN hints.quality_score ELSE EXCLUDED.quality_score END,
        content_revision_hash = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                                     THEN hints.content_revision_hash ELSE EXCLUDED.content_revision_hash END,
        review_status = CASE
            WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                THEN hints.review_status
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN 'STALE'
            ELSE hints.review_status
        END,
        last_reviewed_at = CASE
            WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                THEN hints.last_reviewed_at
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN NULL
            ELSE hints.last_reviewed_at
        END,
        last_review_run_id = CASE
            WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                THEN hints.last_review_run_id
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN NULL
            ELSE hints.last_review_run_id
        END,
        review_claim_run_id = CASE
            WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                THEN hints.review_claim_run_id
            WHEN hints.content_revision_hash IS DISTINCT FROM EXCLUDED.content_revision_hash
                THEN NULL
            ELSE hints.review_claim_run_id
        END,
        ambiguity_count = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                               THEN hints.ambiguity_count ELSE EXCLUDED.ambiguity_count END,
        updated_at = CASE WHEN hints.editorial_text_locked AND hints.review_retry_after_at IS NULL
                          THEN hints.updated_at ELSE EXCLUDED.updated_at END
    RETURNING *, (xmax = 0) AS was_inserted
)
SELECT persisted.*,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN COALESCE(image.object_key, image.download_url) ELSE :imageUrl::TEXT END AS image_url,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN image.original_url ELSE :originalImageUrl::TEXT END AS original_image_url,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN image.source_page_url ELSE :imageSourcePageUrl::TEXT END AS image_source_page_url,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN image.author_name ELSE :imageAuthorName::TEXT END AS image_author_name,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN image.attribution_text ELSE :imageAttributionText::TEXT END AS image_attribution_text,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN image.license_label ELSE :imageLicenseLabel::TEXT END AS image_license_label,
       CASE WHEN persisted.editorial_text_locked AND persisted.review_retry_after_at IS NULL
            THEN image.license_url ELSE :imageLicenseUrl::TEXT END AS image_license_url
FROM persisted
LEFT JOIN cl_media_schema.image_delivery image ON image.image_resource_id = persisted.image_resource_id
