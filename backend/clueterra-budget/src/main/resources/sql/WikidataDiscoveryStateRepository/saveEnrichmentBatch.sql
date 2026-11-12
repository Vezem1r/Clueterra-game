SELECT cl_budget_schema.save_wikidata_enrichment_batch(
    :snapshotId,
    :category,
    CAST(:results AS JSONB)
)
