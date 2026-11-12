SELECT cl_budget_schema.start_or_resume_wikidata_discovery(
    :category,
    CAST(:rootIds AS JSONB),
    :processorVersion
)
