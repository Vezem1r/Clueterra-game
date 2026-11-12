SELECT project.wikidata_id,
       project.name,
       project.country_code,
       project.country_name,
       project.category,
       project.difficulty,
       lifecycle.lifecycle_status,
       CASE WHEN lifecycle.lifecycle_status = 'COMPLETED'
                 AND opening.opening_year = project.project_year
                 AND opening.opening_year <= EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER
            THEN opening.opening_year END AS opening_year,
       project.answer_cost_usd,
       cost.cost_kind,
       metric.metric_type,
       metric.normalized_value AS metric_value,
       metric.normalized_unit AS metric_unit,
       (project.is_active
           AND project.content_quality_status IN ('AUTO_APPROVED', 'EDITORIALLY_APPROVED')
           AND COALESCE(lifecycle.lifecycle_status, 'UNKNOWN') NOT IN ('CANCELLED', 'ABANDONED')
           AND budget_image.object_key IS NOT NULL
           AND resource.lifecycle_status = 'ACTIVE') AS eligible,
       budget_image.object_key,
       image.source_sha1,
       image.source_image_url,
       image.source_page_url,
       image.author_name,
       image.attribution_text,
       image.license_label,
       image.license_url
FROM cl_budget_schema.project project
LEFT JOIN cl_budget_schema.project_current_lifecycle lifecycle
       ON lifecycle.project_id = project.id
LEFT JOIN LATERAL (
    SELECT COALESCE(milestone.milestone_year,
                    EXTRACT(YEAR FROM milestone.milestone_date)::INTEGER) AS opening_year
    FROM cl_budget_schema.project_schedule_milestone milestone
    WHERE milestone.project_id = project.id
      AND milestone.milestone_type = 'OPENING'
      AND milestone.is_primary
      AND (milestone.milestone_date IS NULL OR milestone.milestone_date <= CURRENT_DATE)
    ORDER BY milestone.milestone_date DESC NULLS LAST, milestone.id DESC
    LIMIT 1
) opening ON TRUE
LEFT JOIN cl_budget_schema.project_image_projection image
       ON image.project_id = project.id
LEFT JOIN cl_media_schema.image_resource resource
       ON resource.id = image.image_resource_id
LEFT JOIN LATERAL (
    SELECT rendition.object_key
    FROM cl_media_schema.image_rendition rendition
    WHERE rendition.image_resource_id = image.image_resource_id
      AND rendition.storage_bucket = 'budget-images'
      AND rendition.storage_status = 'AVAILABLE'
    ORDER BY CASE rendition.rendition_type
        WHEN 'DISPLAY' THEN 0
        WHEN 'ORIGINAL' THEN 1
        ELSE 2 END, rendition.id DESC
    LIMIT 1
) budget_image ON TRUE
LEFT JOIN LATERAL (
    SELECT claim.cost_kind
    FROM cl_budget_schema.project_cost claim
    WHERE claim.project_id = project.id AND claim.is_primary
    ORDER BY CASE
        WHEN project.answer_cost_usd BETWEEN claim.amount_usd_low AND claim.amount_usd_high THEN 0
        ELSE 1
    END, CASE claim.cost_kind
        WHEN 'FINAL_COST' THEN 0
        WHEN 'REPORTED_COST' THEN 1
        WHEN 'CURRENT_ESTIMATE' THEN 2
        ELSE 3 END
    LIMIT 1
) cost ON TRUE
LEFT JOIN LATERAL (
    SELECT fact.metric_type, fact.normalized_value, fact.normalized_unit
    FROM cl_budget_schema.project_metric fact
    WHERE fact.project_id = project.id AND fact.is_primary
    ORDER BY CASE fact.metric_type
        WHEN 'LENGTH' THEN 0
        WHEN 'MAIN_SPAN' THEN 1
        WHEN 'HEIGHT' THEN 2
        WHEN 'CAPACITY' THEN 3
        WHEN 'POWER' THEN 4
        ELSE 5 END, fact.metric_type
    LIMIT 1
) metric ON TRUE
WHERE (:countryCode = '' OR project.country_code = :countryCode)
ORDER BY project.country_code, project.wikidata_id;
