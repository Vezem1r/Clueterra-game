SELECT
    seo.wikidata_id,
    seo.slug,
    project.name,
    project.country_code,
    project.country_name,
    project.category,
    project.project_year,
    cost.answer_cost_usd,
    cost.source_amount,
    cost.source_currency,
    seo.cost_kind,
    cost.scope_label,
    lifecycle.lifecycle_status,
    ranking.ranking_scope,
    seo.meta_description,
    image.source_sha1 AS image_source_sha1,
    image.alt_text AS image_alt_text,
    seo.updated_at
FROM cl_budget_schema.project_seo_page seo
JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
JOIN cl_budget_schema.project_image_projection image
    ON image.project_id = project.id
JOIN cl_budget_schema.project_current_lifecycle lifecycle
    ON lifecycle.project_id = project.id
LEFT JOIN cl_budget_schema.project_ranking_scope ranking
    ON ranking.wikidata_id = seo.wikidata_id
JOIN LATERAL (
    SELECT
        (project_cost.amount_usd_low + project_cost.amount_usd_high) / 2 AS answer_cost_usd,
        (project_cost.source_amount_low + project_cost.source_amount_high) / 2 AS source_amount,
        project_cost.source_currency,
        NULLIF(BTRIM(snapshot.payload ->> 'scopeLabel'), '') AS scope_label
    FROM cl_budget_schema.project_cost project_cost
    LEFT JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = project_cost.source_snapshot_id
    WHERE project_cost.project_id = project.id
        AND project_cost.is_primary
        AND project_cost.cost_kind = seo.cost_kind
        AND project_cost.verification_status <> 'DISPUTED'
) cost ON TRUE
