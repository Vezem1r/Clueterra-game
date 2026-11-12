SELECT snapshot_id,
       root_id,
       cursor_value,
       status,
       records_buffered
FROM cl_budget_staging.wikidata_discovery_checkpoint
WHERE snapshot_id = :snapshotId
  AND root_id = :rootId
