WITH registered_image AS (
    SELECT cl_media_schema.register_image(
        'AI_GENERATED',
        'hint-images',
        :storedImageUrl,
        :imageSourceUrl,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL) AS image_resource_id
)
INSERT INTO cl_ai_schema.ai_generated_hint_proposal (
    id, run_id, stage_id, change_set_id, country_id, status, category,
    hint_type, hint_text, image_resource_id, difficulty, quality_score,
    source, source_indicator, source_year, fact_summary, evidence_summary,
    duplicate_check_summary, search_terms, created_at)
SELECT :id, :runId, :stageId, :changeSetId, :countryId, :status, :category,
       :hintType, :hintText, image_resource_id, :difficulty, :qualityScore,
       :source, :sourceIndicator, :sourceYear, :factSummary, :evidenceSummary,
       :duplicateCheckSummary, :searchTerms::jsonb, :createdAt
FROM registered_image
ON CONFLICT (id) DO UPDATE
SET run_id = EXCLUDED.run_id,
    stage_id = EXCLUDED.stage_id,
    change_set_id = EXCLUDED.change_set_id,
    country_id = EXCLUDED.country_id,
    status = EXCLUDED.status,
    category = EXCLUDED.category,
    hint_type = EXCLUDED.hint_type,
    hint_text = EXCLUDED.hint_text,
    image_resource_id = EXCLUDED.image_resource_id,
    difficulty = EXCLUDED.difficulty,
    quality_score = EXCLUDED.quality_score,
    source = EXCLUDED.source,
    source_indicator = EXCLUDED.source_indicator,
    source_year = EXCLUDED.source_year,
    fact_summary = EXCLUDED.fact_summary,
    evidence_summary = EXCLUDED.evidence_summary,
    duplicate_check_summary = EXCLUDED.duplicate_check_summary,
    search_terms = EXCLUDED.search_terms,
    created_at = EXCLUDED.created_at
