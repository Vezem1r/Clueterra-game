SELECT
    candidate.wikidata_id,
    candidate.category,
    candidate.name,
    candidate.image_failure_reason
FROM cl_budget_staging.wikidata_candidate candidate
WHERE candidate.last_fetched
  AND candidate.editorial_status <> 'REJECTED'
  AND candidate.image_status = 'FAILED'
ORDER BY candidate.name, candidate.wikidata_id, candidate.category
LIMIT :limit
