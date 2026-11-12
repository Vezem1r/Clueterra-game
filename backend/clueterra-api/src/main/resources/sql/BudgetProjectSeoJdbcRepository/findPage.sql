SELECT
    seo.wikidata_id,
    project.name AS project_name,
    project.country_name,
    project.category,
    project.id IS NOT NULL AS project_available,
    image.project_id IS NOT NULL AS image_available,
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
    COALESCE((
        SELECT JSONB_AGG(JSONB_BUILD_OBJECT(
            'name', reference.source_name,
            'url', reference.source_url,
            'claim', reference.claim_context)
            ORDER BY reference.display_order)
        FROM cl_budget_schema.project_seo_reference reference
        WHERE reference.wikidata_id = seo.wikidata_id
    ), '[]'::JSONB) AS references_json,
    COALESCE((
        SELECT JSONB_AGG(JSONB_BUILD_OBJECT(
            'label', fact.fact_label,
            'value', fact.fact_value,
            'sourceName', fact.source_name,
            'sourceUrl', fact.source_url)
            ORDER BY fact.display_order)
        FROM cl_budget_schema.project_seo_fact fact
        WHERE fact.wikidata_id = seo.wikidata_id
    ), '[]'::JSONB) AS facts_json,
    seo.publication_status,
    seo.published_at,
    seo.updated_by,
    seo.updated_at
FROM cl_budget_schema.project_seo_page seo
LEFT JOIN cl_budget_schema.project project
    ON project.wikidata_id = seo.wikidata_id
    AND project.is_active
LEFT JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
WHERE (
        :search = ''
        OR LOWER(seo.wikidata_id) LIKE :searchPattern
        OR LOWER(seo.slug) LIKE :searchPattern
        OR LOWER(seo.seo_title) LIKE :searchPattern
        OR LOWER(COALESCE(project.name, '')) LIKE :searchPattern)
    AND (:publicationStatus = '' OR seo.publication_status = :publicationStatus)
ORDER BY
    CASE seo.publication_status WHEN 'PUBLISHED' THEN 0 ELSE 1 END,
    seo.updated_at DESC,
    seo.wikidata_id
LIMIT :limit
OFFSET :offset
