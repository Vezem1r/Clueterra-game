SELECT
    project.wikidata_id,
    project.name AS project_name,
    project.country_name,
    project.category,
    project.project_year,
    cost.answer_cost_usd,
    cost.source_currency,
    project.content_quality_status,
    project.cost_evidence_tier,
    project.cost_confidence_score,
    image.project_id IS NOT NULL AS image_available,
    cost.id IS NOT NULL AS primary_cost_available,
    (SELECT COUNT(*)
     FROM cl_budget_schema.project_metric metric
     WHERE metric.project_id = project.id)
        + (SELECT COUNT(*)
           FROM cl_budget_schema.project_schedule_milestone milestone
           WHERE milestone.project_id = project.id) AS fact_count,
    seo.publication_status,
    seo.slug
FROM cl_budget_schema.project project
LEFT JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
LEFT JOIN cl_budget_schema.project_seo_page seo
    ON seo.wikidata_id = project.wikidata_id
LEFT JOIN LATERAL (
    SELECT
        project_cost.id,
        (project_cost.amount_usd_low + project_cost.amount_usd_high) / 2 AS answer_cost_usd,
        project_cost.source_currency
    FROM cl_budget_schema.project_cost project_cost
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
WHERE project.is_active
    AND (
        :search = ''
        OR LOWER(project.wikidata_id) LIKE :searchPattern
        OR LOWER(project.name) LIKE :searchPattern
        OR LOWER(project.country_name) LIKE :searchPattern)
    AND (
        :pageStatus = ''
        OR COALESCE(seo.publication_status, 'MISSING') = :pageStatus)
ORDER BY
    CASE COALESCE(seo.publication_status, 'MISSING')
        WHEN 'MISSING' THEN 0
        WHEN 'DRAFT' THEN 1
        ELSE 2
    END,
    project.cost_confidence_score DESC,
    project.name,
    project.wikidata_id
LIMIT :limit
OFFSET :offset
