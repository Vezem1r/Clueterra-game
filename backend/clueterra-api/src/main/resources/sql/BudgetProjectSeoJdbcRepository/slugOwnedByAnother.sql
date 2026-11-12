SELECT EXISTS (
    SELECT 1
    FROM cl_budget_schema.project_seo_page seo
    WHERE seo.slug = :slug
        AND seo.wikidata_id <> :wikidataId)
