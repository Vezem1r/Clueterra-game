SELECT cl_budget_schema.save_wikidata_discovery_page(
    :snapshotId,
    :category,
    :rootId,
    :cursorValue,
    :complete,
    CAST(:seeds AS JSONB)
)
