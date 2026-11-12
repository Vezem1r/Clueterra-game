SELECT COUNT(DISTINCT seo.wikidata_id)
FROM cl_budget_schema.project_seo_page seo
JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
JOIN LATERAL (
    SELECT (project_cost.amount_usd_low + project_cost.amount_usd_high) / 2 AS answer_cost_usd
    FROM cl_budget_schema.project_cost project_cost
    WHERE project_cost.project_id = project.id
        AND project_cost.is_primary
        AND project_cost.cost_kind = seo.cost_kind
        AND project_cost.verification_status <> 'DISPUTED'
) cost ON TRUE
