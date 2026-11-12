SELECT *
FROM cl_budget_schema.create_budget_daily_challenge(
    :date,
    :trigger,
    CAST(:questionVariantIds AS JSONB)
)
