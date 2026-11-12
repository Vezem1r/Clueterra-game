SELECT updated, published_question_id
FROM cl_budget_schema.edit_budget_candidate(
    :wikidataId,
    :category,
    :editedBy,
    CAST(:content AS JSONB)
)
