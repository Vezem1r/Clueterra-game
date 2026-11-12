SELECT
    cost.project_id,
    cost.cost_kind,
    cost.source_amount_low,
    cost.source_amount_high,
    cost.source_currency,
    cost.amount_usd_low,
    cost.amount_usd_high,
    cost.cost_year,
    cost.units_per_usd,
    cost.exchange_rate_date,
    cost.exchange_rate_basis,
    cost.exchange_rate_provider,
    cost.evidence_text,
    cost.scope_note,
    snapshot.title AS source_name,
    snapshot.canonical_url AS source_url,
    snapshot.reference_url
FROM cl_budget_schema.project_cost cost
JOIN cl_budget_schema.source_snapshot snapshot ON snapshot.id = cost.source_snapshot_id
JOIN cl_budget_schema.question question
    ON question.project_id = cost.project_id
    AND question.is_published
WHERE cost.is_primary
ORDER BY cost.project_id, cost.cost_kind
