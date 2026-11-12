SELECT wikidata_id,
       MAX(sitelink_count) AS sitelink_count
FROM cl_budget_staging.wikidata_discovery_seed
WHERE snapshot_id = :snapshotId
GROUP BY wikidata_id
ORDER BY sitelink_count DESC,
         wikidata_id
