SELECT COUNT(*)
FROM cl_budget_staging.wikidata_candidate candidate
WHERE candidate.last_fetched
  AND candidate.editorial_status <> 'REJECTED'
  AND (
    candidate.image_status = 'PENDING'
        OR (
        candidate.image_status = 'PROCESSING'
            AND candidate.image_last_attempted_at < :staleBefore
        )
    )
