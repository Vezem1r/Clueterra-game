SELECT
    seo.wikidata_id,
    seo.slug,
    seo.cost_question,
    seo.seo_title,
    seo.meta_description,
    seo.keywords,
    seo.intro,
    seo.answer_detail,
    seo.scope_text,
    seo.context_text,
    seo.methodology_text,
    seo.source_name,
    seo.source_url,
    seo.updated_at,
    project.name,
    project.country_code,
    project.country_name,
    project.category,
    project.project_year,
    cost.answer_cost_usd,
    cost.source_amount,
    cost.source_currency,
    cost.cost_year,
    cost.cost_kind,
    cost.scope_label,
    lifecycle.lifecycle_status,
    media.source_sha1 AS image_source_sha1,
    image.alt_text AS image_alt_text,
    media.author_name AS image_author_name,
    media.attribution_text AS image_attribution_text,
    media.license_label AS image_license_label,
    media.license_url AS image_license_url,
    media.source_page_url AS image_source_page_url,
    costs.costs_json,
    metrics.metrics_json,
    milestones.milestones_json,
    facts.facts_json,
    sources.sources_json
FROM cl_budget_schema.project_seo_page seo
CROSS JOIN (VALUES ('^https?://[^/]*wikipedia\.org/')) wikipedia(url_pattern)
JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
JOIN LATERAL (
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
JOIN LATERAL (
    SELECT
        project_cost.cost_kind,
        (project_cost.amount_usd_low + project_cost.amount_usd_high) / 2 AS answer_cost_usd,
        (project_cost.source_amount_low + project_cost.source_amount_high) / 2 AS source_amount,
        project_cost.source_currency,
        project_cost.cost_year,
        NULLIF(BTRIM(snapshot.payload ->> 'scopeLabel'), '') AS scope_label
    FROM cl_budget_schema.project_cost project_cost
    LEFT JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = project_cost.source_snapshot_id
    WHERE project_cost.project_id = project.id
        AND project_cost.is_primary
        AND project_cost.cost_kind = seo.cost_kind
        AND project_cost.verification_status <> 'DISPUTED'
) cost ON TRUE
JOIN cl_budget_schema.project_current_lifecycle lifecycle
    ON lifecycle.project_id = project.id
JOIN LATERAL (
    SELECT COALESCE(
        JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'costKind', entry.cost_kind,
                'sourceAmount', (entry.source_amount_low + entry.source_amount_high) / 2,
                'sourceCurrency', entry.source_currency,
                'amountUsd', (entry.amount_usd_low + entry.amount_usd_high) / 2,
                'costYear', entry.cost_year,
                'featured', entry.cost_kind = seo.cost_kind,
                'scopeLabel', NULLIF(BTRIM(snapshot.payload ->> 'scopeLabel'), ''),
                'sourceName', cl_budget_schema.project_source_display_name(
                    COALESCE(registry.display_name, snapshot.title),
                    source_link.url),
                'sourceUrl', source_link.url)
            ORDER BY
                (entry.cost_kind = seo.cost_kind) DESC,
                CASE entry.cost_kind
                    WHEN 'ORIGINAL_BUDGET' THEN 10
                    WHEN 'APPROVED_BUDGET' THEN 20
                    WHEN 'CONTRACT_VALUE' THEN 30
                    WHEN 'FINANCING_AMOUNT' THEN 40
                    WHEN 'REVISED_BUDGET' THEN 50
                    WHEN 'CURRENT_ESTIMATE' THEN 60
                    WHEN 'EXPENDITURE_TO_DATE' THEN 70
                    WHEN 'FINAL_COST' THEN 80
                    ELSE 90
                END,
                entry.cost_year),
        '[]'::JSONB) AS costs_json
    FROM cl_budget_schema.project_cost entry
    JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = entry.source_snapshot_id
    LEFT JOIN cl_budget_schema.source_registry registry ON registry.code = snapshot.source_code
    CROSS JOIN LATERAL (
        SELECT CASE
            WHEN snapshot.canonical_url ~* wikipedia.url_pattern
                AND snapshot.reference_url !~* wikipedia.url_pattern
            THEN snapshot.reference_url
            ELSE COALESCE(snapshot.canonical_url, snapshot.reference_url)
        END AS url
    ) source_link
    WHERE entry.project_id = project.id
        AND entry.is_primary
        AND entry.verification_status <> 'DISPUTED'
) costs ON TRUE
JOIN LATERAL (
    SELECT COALESCE(
        JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'metricType', metric.metric_type,
                'value', metric.normalized_value,
                'unit', metric.normalized_unit,
                'sourceName', cl_budget_schema.project_source_display_name(
                    snapshot.title,
                    source_link.url),
                'sourceUrl', source_link.url)
            ORDER BY CASE metric.metric_type
                WHEN 'LENGTH' THEN 10
                WHEN 'MAIN_SPAN' THEN 20
                WHEN 'HEIGHT' THEN 30
                WHEN 'FLOOR_AREA' THEN 40
                WHEN 'CAPACITY' THEN 50
                WHEN 'POWER' THEN 60
                WHEN 'ANNUAL_PASSENGERS' THEN 70
                WHEN 'ANNUAL_CARGO_TONNES' THEN 80
                WHEN 'CONTAINER_CAPACITY' THEN 90
                WHEN 'STORAGE_VOLUME' THEN 100
                WHEN 'MASS' THEN 110
                ELSE 120
            END),
        '[]'::JSONB) AS metrics_json
    FROM cl_budget_schema.project_metric metric
    JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = metric.source_snapshot_id
    CROSS JOIN LATERAL (
        SELECT CASE
            WHEN snapshot.reference_url ~* wikipedia.url_pattern
                AND snapshot.canonical_url !~* wikipedia.url_pattern
            THEN snapshot.canonical_url
            ELSE COALESCE(snapshot.reference_url, snapshot.canonical_url)
        END AS url
    ) source_link
    WHERE metric.project_id = project.id
        AND metric.is_primary
) metrics ON TRUE
JOIN LATERAL (
    SELECT COALESCE(
        JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'milestoneType', milestone.milestone_type,
                'dateValue', milestone.milestone_date,
                'yearValue', milestone.milestone_year,
                'sourceName', cl_budget_schema.project_source_display_name(
                    snapshot.title,
                    source_link.url),
                'sourceUrl', source_link.url)
            ORDER BY
                COALESCE(milestone.milestone_date, MAKE_DATE(milestone.milestone_year, 1, 1)),
                CASE milestone.milestone_type
                    WHEN 'CONSTRUCTION_START' THEN 10
                    WHEN 'PLANNED_COMPLETION' THEN 20
                    WHEN 'CURRENT_COMPLETION_ESTIMATE' THEN 30
                    WHEN 'ACTUAL_COMPLETION' THEN 40
                    WHEN 'OPENING' THEN 50
                    ELSE 60
                END),
        '[]'::JSONB) AS milestones_json
    FROM cl_budget_schema.project_schedule_milestone milestone
    JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = milestone.source_snapshot_id
    CROSS JOIN LATERAL (
        SELECT CASE
            WHEN snapshot.reference_url ~* wikipedia.url_pattern
                AND snapshot.canonical_url !~* wikipedia.url_pattern
            THEN snapshot.canonical_url
            ELSE COALESCE(snapshot.reference_url, snapshot.canonical_url)
        END AS url
    ) source_link
    WHERE milestone.project_id = project.id
        AND milestone.is_primary
) milestones ON TRUE
JOIN LATERAL (
    SELECT COALESCE(
        JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'label', fact.fact_label,
                'value', fact.fact_value,
                'sourceName', cl_budget_schema.project_source_display_name(
                    fact.source_name,
                    fact.source_url),
                'sourceUrl', fact.source_url)
            ORDER BY fact.display_order, fact.fact_label),
        '[]'::JSONB) AS facts_json
    FROM cl_budget_schema.project_seo_fact fact
    WHERE fact.wikidata_id = seo.wikidata_id
) facts ON TRUE
JOIN LATERAL (
    SELECT COALESCE(
        JSONB_AGG(
            JSONB_BUILD_OBJECT(
                'name', source.source_name,
                'url', source.source_url,
                'claim', source.claim_context)
            ORDER BY source.display_order, source.source_name),
        '[]'::JSONB) AS sources_json
    FROM (
        SELECT
            0 AS display_order,
            seo.source_name,
            seo.source_url,
            'Evidence for the featured cost figure.'::TEXT AS claim_context
        UNION ALL
        SELECT
            reference.display_order,
            reference.source_name,
            reference.source_url,
            reference.claim_context
        FROM cl_budget_schema.project_seo_reference reference
        WHERE reference.wikidata_id = seo.wikidata_id
    ) source
) sources ON TRUE
WHERE (seo.slug = :slug OR seo.wikidata_id = :wikidataId)
    AND (NOT :requirePublished OR seo.publication_status = 'PUBLISHED')
