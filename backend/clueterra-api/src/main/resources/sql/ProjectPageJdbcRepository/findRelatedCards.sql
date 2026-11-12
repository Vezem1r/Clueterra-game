WITH current_project AS (
    SELECT category
    FROM cl_budget_schema.project
    WHERE wikidata_id = :wikidataId
        AND is_active
)
SELECT
    seo.wikidata_id,
    seo.slug,
    project.name,
    project.country_name,
    project.category,
    project.project_year,
    (cost.amount_usd_low + cost.amount_usd_high) / 2 AS answer_cost_usd,
    (cost.source_amount_low + cost.source_amount_high) / 2 AS source_amount,
    cost.source_currency,
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
JOIN cl_budget_schema.project_cost cost
    ON cost.project_id = project.id
    AND cost.is_primary
    AND cost.cost_kind = seo.cost_kind
    AND cost.verification_status <> 'DISPUTED'
CROSS JOIN current_project
WHERE seo.publication_status = 'PUBLISHED'
    AND seo.wikidata_id <> :wikidataId
ORDER BY
    CASE WHEN project.category = current_project.category THEN 0 ELSE 1 END,
    seo.published_at DESC,
    project.name ASC
LIMIT :limit
