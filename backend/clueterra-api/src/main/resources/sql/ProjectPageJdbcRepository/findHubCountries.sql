SELECT
    project.country_code,
    MODE() WITHIN GROUP (ORDER BY project.country_name) AS country_name,
    COUNT(*) AS project_count
FROM cl_budget_schema.project_seo_page seo
JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
JOIN cl_budget_schema.project_image_projection image
    ON image.project_id = project.id
JOIN cl_budget_schema.project_current_lifecycle lifecycle
    ON lifecycle.project_id = project.id
WHERE seo.publication_status = 'PUBLISHED'
    AND lifecycle.lifecycle_status IN (:lifecycleStatuses)
    AND project.country_code IS NOT NULL
    AND EXISTS (
        SELECT 1
        FROM cl_budget_schema.project_cost project_cost
        WHERE project_cost.project_id = project.id
            AND project_cost.is_primary
            AND project_cost.cost_kind = seo.cost_kind
            AND project_cost.verification_status <> 'DISPUTED'
    )
GROUP BY project.country_code
HAVING COUNT(*) >= :minimumProjects
ORDER BY project_count DESC, country_name
