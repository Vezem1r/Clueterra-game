WITH catalog AS (
    SELECT
        candidate.*,
        COUNT(*) OVER (PARTITION BY LOWER(BTRIM(candidate.name))) AS duplicate_name_count,
        CASE
            WHEN candidate.image_source_sha1 IS NULL THEN 1
            ELSE COUNT(*) OVER (PARTITION BY candidate.image_source_sha1)
        END AS duplicate_image_count
    FROM cl_budget_schema.candidate_review candidate
    WHERE candidate.last_fetched
)
SELECT
    candidate.wikidata_id,
    candidate.category,
    candidate.name,
    candidate.description,
    candidate.country_code,
    candidate.country_name,
    candidate.region_code,
    candidate.project_year,
    candidate.difficulty,
    candidate.sitelink_count,
    candidate.monthly_pageviews,
    candidate.interest_score,
    candidate.quality_score,
    candidate.source_rank,
    candidate.quality_status,
    candidate.evidence_tier,
    candidate.cost_confidence_score,
    candidate.quality_flags,
    candidate.source_url,
    candidate.review_status,
    candidate.review_note,
    candidate.reviewed_by,
    candidate.reviewed_at,
    candidate.fetched_at,
    candidate.duplicate_name_count,
    candidate.duplicate_image_count,
    candidate.image_source_sha1,
    candidate.image_object_key,
    candidate.image_original_url,
    candidate.image_source_page_url,
    candidate.image_file_title,
    candidate.image_author_name,
    candidate.image_credit,
    candidate.image_license_label,
    candidate.image_license_url,
    candidate.image_description,
    candidate.image_mime_type,
    candidate.image_source_bytes,
    candidate.image_width,
    candidate.image_height,
    candidate.image_quality_score,
    question.id AS question_id,
    candidate.answer_cost_usd,
    candidate.source_cost_amount AS source_amount,
    candidate.source_cost_currency AS source_currency,
    candidate.cost_year,
    candidate.cost_normalization_note AS normalization_note,
    candidate.cost_source_name,
    candidate.cost_source_url,
    candidate.cost_reference_url,
    COALESCE(candidate.content_edited_at, candidate.fetched_at)::DATE AS cost_snapshot_at,
    candidate.source_payload
FROM catalog candidate
LEFT JOIN LATERAL (
    SELECT published.id
    FROM cl_budget_schema.question published
    WHERE published.wikidata_id = candidate.wikidata_id
        AND published.is_published
    ORDER BY
        (published.category = candidate.category) DESC,
        published.version DESC
    LIMIT 1
) question ON TRUE
WHERE candidate.wikidata_id = :wikidataId
    AND candidate.category = :category
