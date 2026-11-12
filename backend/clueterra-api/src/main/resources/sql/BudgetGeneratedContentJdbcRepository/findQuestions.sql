SELECT
    variant.id,
    variant.question_type,
    variant.generation_key,
    variant.difficulty,
    variant.is_active,
    source_question.name_snapshot AS project_name,
    source_question.country_name,
    source_question.category,
    comparison_question.name_snapshot AS comparison_project_name,
    variant.answer_value,
    variant.answer_unit,
    variant.answer_choice,
    variant.prompt_payload,
    variant.reveal_payload,
    variant.generated_at,
    variant.retired_at
FROM cl_budget_schema.question_variant variant
JOIN cl_budget_schema.question source_question ON source_question.id = variant.source_question_id
LEFT JOIN cl_budget_schema.question comparison_question
    ON comparison_question.id = variant.comparison_question_id
