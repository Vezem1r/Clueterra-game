SELECT country_id,
       hint_text
FROM cl_ai_schema.ai_generated_hint_proposal
WHERE country_id IN (:countryIds)
  AND hint_text IS NOT NULL
  AND status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'DUPLICATE', 'APPLIED')
ORDER BY country_id ASC, created_at DESC
