WITH unique_seeds AS (
    SELECT seed.wikidata_id,
           MAX(seed.sitelink_count) AS sitelink_count
    FROM cl_budget_staging.wikidata_discovery_seed seed
    WHERE seed.snapshot_id = :snapshotId
    GROUP BY seed.wikidata_id
)
SELECT seed.wikidata_id,
       seed.sitelink_count
FROM unique_seeds seed
LEFT JOIN cl_budget_staging.wikidata_enrichment_result result
       ON result.snapshot_id = :snapshotId
      AND result.wikidata_id = seed.wikidata_id
WHERE result.wikidata_id IS NULL
ORDER BY seed.sitelink_count DESC,
         seed.wikidata_id
