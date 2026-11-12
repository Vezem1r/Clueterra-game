SELECT
    variant.id AS variant_id,
    variant.question_type,
    variant.difficulty,
    source_question.project_id AS source_project_id,
    comparison_question.project_id AS comparison_project_id,
    source_question.category
FROM cl_budget_schema.question_variant variant
JOIN cl_budget_schema.question source_question
    ON source_question.id = variant.source_question_id
    AND source_question.is_published
LEFT JOIN cl_budget_schema.question comparison_question
    ON comparison_question.id = variant.comparison_question_id
    AND comparison_question.is_published
WHERE variant.is_active
    AND (
        variant.comparison_question_id IS NULL
        OR comparison_question.id IS NOT NULL
    )
ORDER BY variant.id
