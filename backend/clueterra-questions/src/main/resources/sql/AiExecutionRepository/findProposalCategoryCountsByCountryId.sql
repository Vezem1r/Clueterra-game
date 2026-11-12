SELECT category, COUNT(*)::INT AS cnt
FROM cl_ai_schema.ai_generated_hint_proposal
WHERE country_id = :countryId
  AND status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL')
  AND category IS NOT NULL
GROUP BY category
ORDER BY cnt DESC
