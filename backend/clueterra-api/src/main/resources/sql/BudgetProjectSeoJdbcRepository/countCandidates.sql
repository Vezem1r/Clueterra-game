SELECT COUNT(*)
FROM cl_budget_schema.project project
LEFT JOIN cl_budget_schema.project_seo_page seo
    ON seo.wikidata_id = project.wikidata_id
WHERE project.is_active
    AND (
        :search = ''
        OR LOWER(project.wikidata_id) LIKE :searchPattern
        OR LOWER(project.name) LIKE :searchPattern
        OR LOWER(project.country_name) LIKE :searchPattern)
    AND (
        :pageStatus = ''
        OR COALESCE(seo.publication_status, 'MISSING') = :pageStatus)
