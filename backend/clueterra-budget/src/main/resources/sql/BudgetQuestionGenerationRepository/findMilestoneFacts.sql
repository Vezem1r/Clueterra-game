SELECT
    milestone.project_id,
    milestone.milestone_type,
    milestone.milestone_date,
    milestone.milestone_year,
    milestone.date_precision,
    snapshot.title AS source_name,
    snapshot.canonical_url AS source_url,
    snapshot.reference_url
FROM cl_budget_schema.project_schedule_milestone milestone
JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = milestone.source_snapshot_id
JOIN cl_budget_schema.question question
    ON question.project_id = milestone.project_id
    AND question.is_published
WHERE milestone.is_primary
ORDER BY milestone.project_id, milestone.milestone_type
