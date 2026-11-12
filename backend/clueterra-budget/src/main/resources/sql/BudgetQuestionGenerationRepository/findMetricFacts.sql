SELECT
    metric.project_id,
    metric.metric_type,
    metric.normalized_value,
    metric.normalized_unit,
    snapshot.title AS source_name,
    snapshot.canonical_url AS source_url,
    snapshot.reference_url
FROM cl_budget_schema.project_metric metric
JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = metric.source_snapshot_id
JOIN cl_budget_schema.question question
    ON question.project_id = metric.project_id
    AND question.is_published
WHERE metric.is_primary
ORDER BY metric.project_id, metric.metric_type
