SELECT
    project.wikidata_id,
    project.name AS project_name,
    project.description,
    project.country_name,
    project.category,
    project.project_year,
    cost.answer_cost_usd,
    cost.source_amount,
    cost.source_currency,
    cost.cost_year,
    cost.normalization_note,
    cost.source_name,
    cost.source_url,
    project.cost_evidence_tier,
    project.cost_confidence_score,
    JSONB_ARRAY_LENGTH(project.quality_flags) > 0 AS has_quality_flags,
    media.image_resource_id IS NOT NULL AS image_available,
    media.image_resource_id IS NOT NULL
        AND BTRIM(media.object_key) <> ''
        AND BTRIM(image.alt_text) <> ''
        AND BTRIM(media.author_name) <> ''
        AND BTRIM(media.license_label) <> ''
        AND COALESCE(media.source_width, media.width) >= 800
        AND COALESCE(media.source_height, media.height) >= 450 AS image_metadata_complete,
    cost.id IS NOT NULL AS primary_cost_available,
    cost.verification_status AS cost_verification_status,
    cost.evidence_text AS cost_evidence_text,
    cost.scope_note AS cost_scope_note,
    (SELECT COUNT(*)
     FROM cl_budget_schema.project_metric metric
     WHERE metric.project_id = project.id) AS metric_count,
    (SELECT COUNT(*)
     FROM cl_budget_schema.project_schedule_milestone milestone
     WHERE milestone.project_id = project.id) AS milestone_count
FROM cl_budget_schema.project project
LEFT JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
LEFT JOIN LATERAL (
    SELECT current_media.*
    FROM cl_media_schema.image_current current_media
    WHERE current_media.image_resource_id = image.image_resource_id
      AND current_media.storage_bucket = 'budget-images'
      AND current_media.lifecycle_status = 'ACTIVE'
      AND current_media.storage_status = 'AVAILABLE'
    ORDER BY CASE current_media.rendition_type
        WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
        current_media.image_rendition_id
    LIMIT 1
) media ON TRUE
LEFT JOIN cl_budget_schema.project_seo_page seo
    ON seo.wikidata_id = project.wikidata_id
LEFT JOIN LATERAL (
    SELECT
        project_cost.id,
        (project_cost.amount_usd_low + project_cost.amount_usd_high) / 2 AS answer_cost_usd,
        (project_cost.source_amount_low + project_cost.source_amount_high) / 2 AS source_amount,
        project_cost.source_currency,
        project_cost.cost_year,
        CASE
            WHEN project_cost.source_currency = 'USD'
                THEN 'The cited amount is already in US dollars; no currency conversion is applied.'
            ELSE 'The source amount is converted using the recorded '
                || LOWER(REPLACE(project_cost.exchange_rate_basis, '_', ' '))
                || ' exchange-rate basis from '
                || project_cost.exchange_rate_provider || '.'
        END AS normalization_note,
        COALESCE(registry.display_name, snapshot.title, project.cost_source_name) AS source_name,
        COALESCE(snapshot.reference_url, snapshot.canonical_url, project.cost_reference_url, project.cost_source_url) AS source_url,
        project_cost.verification_status,
        project_cost.evidence_text,
        project_cost.scope_note
    FROM cl_budget_schema.project_cost project_cost
    LEFT JOIN cl_budget_schema.source_snapshot snapshot
        ON snapshot.id = project_cost.source_snapshot_id
    LEFT JOIN cl_budget_schema.source_registry registry
        ON registry.code = snapshot.source_code
    WHERE project_cost.project_id = project.id
        AND project_cost.is_primary
        AND project_cost.verification_status <> 'DISPUTED'
        AND (seo.cost_kind IS NULL OR project_cost.cost_kind = seo.cost_kind)
    ORDER BY
        CASE WHEN
            ROUND((project_cost.amount_usd_low + project_cost.amount_usd_high) / 2, 2)
                = project.answer_cost_usd
            AND ROUND((project_cost.source_amount_low + project_cost.source_amount_high) / 2, 2)
                = project.source_amount
            AND project_cost.source_currency = project.source_currency
            AND project_cost.cost_year IS NOT DISTINCT FROM project.cost_year
        THEN 0 ELSE 1 END,
        project_cost.id
    LIMIT 1
) cost ON TRUE
WHERE project.wikidata_id = :wikidataId
    AND project.is_active
