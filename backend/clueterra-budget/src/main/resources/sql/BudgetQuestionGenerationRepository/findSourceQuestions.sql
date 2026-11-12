SELECT
    question.id AS question_id,
    question.project_id,
    question.wikidata_id,
    question.name_snapshot,
    question.description_snapshot,
    question.description_source_name,
    question.description_source_url,
    question.description_license_label,
    question.description_license_url,
    question.country_code,
    question.country_name,
    question.region_name,
    question.category,
    question.project_year,
    answer_cost.cost_kind AS answer_cost_kind,
    lifecycle.lifecycle_status,
    expected_completion.milestone_year AS expected_completion_year,
    question.answer_cost_usd,
    question.cost_source_name,
    question.cost_source_url,
    question.cost_reference_url,
    question.difficulty
FROM cl_budget_schema.question question
JOIN cl_budget_schema.project project
    ON project.id = question.project_id
    AND project.is_active
JOIN cl_budget_schema.project_cost answer_cost
    ON answer_cost.id = question.answer_project_cost_id
LEFT JOIN cl_budget_schema.project_current_lifecycle lifecycle
    ON lifecycle.project_id = project.id
LEFT JOIN LATERAL (
    SELECT milestone.milestone_year
    FROM cl_budget_schema.project_schedule_milestone milestone
    WHERE milestone.project_id = project.id
      AND milestone.is_primary
      AND milestone.milestone_type IN ('CURRENT_COMPLETION_ESTIMATE', 'PLANNED_COMPLETION')
    ORDER BY
        CASE milestone.milestone_type WHEN 'CURRENT_COMPLETION_ESTIMATE' THEN 0 ELSE 1 END,
        milestone.created_at DESC
    LIMIT 1
) expected_completion ON TRUE
WHERE question.is_published
    AND question.answer_project_cost_id IS NOT NULL
ORDER BY question.id
