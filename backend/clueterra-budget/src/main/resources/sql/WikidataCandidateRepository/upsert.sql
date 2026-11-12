WITH current_image AS (
    SELECT candidate.image_resource_id,
           resource.source_key,
           COALESCE(image.object_key, image.download_url) AS image_url
    FROM cl_budget_staging.wikidata_candidate candidate
    LEFT JOIN cl_media_schema.image_resource resource
           ON resource.id = candidate.image_resource_id
    LEFT JOIN cl_media_schema.image_delivery image
           ON image.image_resource_id = candidate.image_resource_id
    WHERE candidate.wikidata_id = :wikidataId
      AND candidate.category = :category
), incoming_image AS (
    SELECT cl_media_schema.register_image(
        'WIKIMEDIA_COMMONS',
        'budget-images',
        :imageObjectKey,
        :imageOriginalUrl,
        :imageSourcePageUrl,
        :imageAuthorName,
        :imageCredit,
        :imageLicenseLabel,
        :imageLicenseUrl,
        :imageSourceSha1,
        CAST(:imagePageId AS TEXT),
        :imageMimeType,
        :imageWidth,
        :imageHeight,
        p_source_key_override => cl_media_schema.preserved_source_key(
            current_image.image_resource_id,
            current_image.image_url,
            :imageObjectKey)
    ) AS image_resource_id
    FROM (SELECT 1) seed
    LEFT JOIN current_image ON TRUE
)
INSERT INTO cl_budget_staging.wikidata_candidate AS current_candidate (
    wikidata_id,
    category,
    name,
    description,
    country_code,
    country_name,
    region_code,
    project_year,
    image_file_title,
    image_resource_id,
    image_description,
    image_quality_score,
    source_cost_amount,
    source_cost_currency,
    answer_cost_usd,
    cost_year,
    cost_units_per_usd,
    cost_exchange_rate_date,
    cost_source_name,
    cost_source_url,
    cost_reference_url,
    cost_normalization_note,
    source_url,
    source_payload,
    sitelink_count,
    monthly_pageviews,
    interest_score,
    quality_score,
    quality_status,
    evidence_tier,
    cost_confidence_score,
    quality_flags,
    difficulty,
    source_rank,
    last_fetched,
    fetched_at,
    updated_at)
SELECT :wikidataId,
       :category,
       :name,
       :description,
       :countryCode,
       :countryName,
       :regionCode,
       :projectYear,
       :imageFileTitle,
       incoming_image.image_resource_id,
       :imageDescription,
       :imageQualityScore,
       :sourceCostAmount,
       :sourceCostCurrency,
       :answerCostUsd,
       :costYear,
       :costUnitsPerUsd,
       :costExchangeRateDate,
       :costSourceName,
       :costSourceUrl,
       NULLIF(:costReferenceUrl, ''),
       :costNormalizationNote,
       :sourceUrl,
       CAST(:sourcePayload AS JSONB),
       :sitelinkCount,
       :monthlyPageviews,
       :interestScore,
       :qualityScore,
       :qualityStatus,
       :evidenceTier,
       :costConfidenceScore,
       CAST(:qualityFlags AS JSONB),
       :difficulty,
       :sourceRank,
       TRUE,
       NOW(),
       NOW()
FROM incoming_image
ON CONFLICT (wikidata_id, category) DO UPDATE
SET name = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.name ELSE EXCLUDED.name END,
    description = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.description ELSE EXCLUDED.description END,
    country_code = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.country_code ELSE EXCLUDED.country_code END,
    country_name = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.country_name ELSE EXCLUDED.country_name END,
    region_code = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.region_code ELSE EXCLUDED.region_code END,
    project_year = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.project_year ELSE EXCLUDED.project_year END,
    image_file_title = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.image_file_title ELSE EXCLUDED.image_file_title END,
    image_resource_id = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.image_resource_id ELSE EXCLUDED.image_resource_id END,
    image_description = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.image_description ELSE EXCLUDED.image_description END,
    image_quality_score = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.image_quality_score ELSE EXCLUDED.image_quality_score END,
    source_cost_amount = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.source_cost_amount ELSE EXCLUDED.source_cost_amount END,
    source_cost_currency = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.source_cost_currency ELSE EXCLUDED.source_cost_currency END,
    answer_cost_usd = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.answer_cost_usd ELSE EXCLUDED.answer_cost_usd END,
    cost_year = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_year ELSE EXCLUDED.cost_year END,
    cost_units_per_usd = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_units_per_usd ELSE EXCLUDED.cost_units_per_usd END,
    cost_exchange_rate_date = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_exchange_rate_date ELSE EXCLUDED.cost_exchange_rate_date END,
    cost_source_name = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_source_name ELSE EXCLUDED.cost_source_name END,
    cost_source_url = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_source_url ELSE EXCLUDED.cost_source_url END,
    cost_reference_url = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_reference_url ELSE EXCLUDED.cost_reference_url END,
    cost_normalization_note = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_normalization_note ELSE EXCLUDED.cost_normalization_note END,
    source_url = EXCLUDED.source_url,
    source_payload = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.source_payload ELSE EXCLUDED.source_payload END,
    sitelink_count = EXCLUDED.sitelink_count,
    monthly_pageviews = EXCLUDED.monthly_pageviews,
    interest_score = EXCLUDED.interest_score,
    quality_score = EXCLUDED.quality_score,
    quality_status = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.quality_status ELSE EXCLUDED.quality_status END,
    evidence_tier = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.evidence_tier ELSE EXCLUDED.evidence_tier END,
    cost_confidence_score = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.cost_confidence_score ELSE EXCLUDED.cost_confidence_score END,
    quality_flags = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.quality_flags ELSE EXCLUDED.quality_flags END,
    difficulty = CASE WHEN current_candidate.editorial_content IS NOT NULL
        THEN current_candidate.difficulty ELSE EXCLUDED.difficulty END,
    source_rank = EXCLUDED.source_rank,
    editorial_status = CASE
        WHEN current_candidate.editorial_content IS NOT NULL THEN current_candidate.editorial_status
        WHEN current_candidate.editorial_status = 'REJECTED' THEN 'REJECTED'
        WHEN current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
             AND current_candidate.answer_cost_usd = EXCLUDED.answer_cost_usd
             AND current_candidate.cost_source_url = EXCLUDED.cost_source_url
             AND current_candidate.description = EXCLUDED.description
            THEN current_candidate.editorial_status
        ELSE 'NONE'
    END,
    editorial_note = CASE
        WHEN current_candidate.editorial_content IS NOT NULL
             OR current_candidate.editorial_status = 'REJECTED'
             OR (current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
                 AND current_candidate.answer_cost_usd = EXCLUDED.answer_cost_usd
                 AND current_candidate.cost_source_url = EXCLUDED.cost_source_url
                 AND current_candidate.description = EXCLUDED.description)
            THEN current_candidate.editorial_note
        ELSE NULL
    END,
    editorial_by = CASE
        WHEN current_candidate.editorial_content IS NOT NULL
             OR current_candidate.editorial_status = 'REJECTED'
             OR (current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
                 AND current_candidate.answer_cost_usd = EXCLUDED.answer_cost_usd
                 AND current_candidate.cost_source_url = EXCLUDED.cost_source_url
                 AND current_candidate.description = EXCLUDED.description)
            THEN current_candidate.editorial_by
        ELSE NULL
    END,
    editorial_at = CASE
        WHEN current_candidate.editorial_content IS NOT NULL
             OR current_candidate.editorial_status = 'REJECTED'
             OR (current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
                 AND current_candidate.answer_cost_usd = EXCLUDED.answer_cost_usd
                 AND current_candidate.cost_source_url = EXCLUDED.cost_source_url
                 AND current_candidate.description = EXCLUDED.description)
            THEN current_candidate.editorial_at
        ELSE NULL
    END,
    image_status = CASE
        WHEN current_candidate.editorial_content IS NOT NULL THEN current_candidate.image_status
        WHEN current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
             AND current_candidate.image_status = 'AVAILABLE' THEN 'AVAILABLE'
        WHEN current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
             AND current_candidate.image_status = 'PROCESSING' THEN 'PROCESSING'
        ELSE 'PENDING'
    END,
    image_attempt_count = CASE
        WHEN current_candidate.editorial_content IS NOT NULL
             OR current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
            THEN current_candidate.image_attempt_count
        ELSE 0
    END,
    image_last_attempted_at = CASE
        WHEN current_candidate.editorial_content IS NOT NULL
             OR current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
            THEN current_candidate.image_last_attempted_at
        ELSE NULL
    END,
    image_failure_reason = CASE
        WHEN current_candidate.editorial_content IS NOT NULL THEN current_candidate.image_failure_reason
        WHEN current_candidate.image_resource_id IS NOT DISTINCT FROM EXCLUDED.image_resource_id
             AND current_candidate.image_status = 'FAILED'
            THEN current_candidate.image_failure_reason
        ELSE NULL
    END,
    last_fetched = TRUE,
    fetched_at = NOW(),
    updated_at = NOW()
WHERE current_candidate.candidate_origin = 'WIKIDATA'
  AND current_candidate.editorial_status <> 'APPROVED'
