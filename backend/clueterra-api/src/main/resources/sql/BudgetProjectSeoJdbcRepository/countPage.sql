SELECT COUNT(DISTINCT seo.wikidata_id)
FROM cl_budget_schema.project_seo_page seo
LEFT JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
WHERE (
        :search = ''
        OR LOWER(seo.wikidata_id) LIKE :searchPattern
        OR LOWER(seo.slug) LIKE :searchPattern
        OR LOWER(seo.seo_title) LIKE :searchPattern
        OR LOWER(COALESCE(project.name, '')) LIKE :searchPattern)
    AND (:publicationStatus = '' OR seo.publication_status = :publicationStatus)
