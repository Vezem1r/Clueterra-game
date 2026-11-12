SELECT COUNT(*)
FROM cl_budget_schema.question_variant variant
JOIN cl_budget_schema.question source_question ON source_question.id = variant.source_question_id
LEFT JOIN cl_budget_schema.question comparison_question
    ON comparison_question.id = variant.comparison_question_id
