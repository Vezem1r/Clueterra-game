SELECT *
FROM cl_budget_schema.replace_budget_question_variants(
    CAST(:variants AS JSONB)
)
