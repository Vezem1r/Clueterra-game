SELECT candidate_payload::TEXT AS candidate_payload
FROM cl_budget_staging.wikidata_enrichment_result
WHERE snapshot_id = :snapshotId
  AND status = 'READY'
ORDER BY wikidata_id
