SELECT seo.slug, seo.updated_at
FROM cl_budget_schema.project_seo_page seo
JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
WHERE seo.publication_status = 'PUBLISHED'
    AND EXISTS (
        SELECT 1
        FROM cl_budget_schema.project_cost cost
        WHERE cost.project_id = project.id
            AND cost.is_primary
            AND cost.cost_kind = seo.cost_kind
            AND cost.verification_status <> 'DISPUTED')
ORDER BY seo.slug
