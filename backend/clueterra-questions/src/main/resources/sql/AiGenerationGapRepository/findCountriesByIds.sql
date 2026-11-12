WITH proposal_counts AS (
    SELECT p.country_id,
           COUNT(*) FILTER (
               WHERE p.status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPLIED')
           )::INT                  AS pending_proposal_count,
           MAX(p.created_at) FILTER (
               WHERE p.status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPLIED')
           )                       AS last_generated_at
    FROM cl_ai_schema.ai_generated_hint_proposal p
    WHERE p.country_id IN (:countryIds)
    GROUP BY p.country_id
)
SELECT c.id AS country_id,
       c.iso_alpha2 AS country_code,
       c.name_en AS country_name,
       COUNT(h.id) FILTER (WHERE h.is_active = TRUE)::INT AS active_hint_count,
       COALESCE(pc.pending_proposal_count, 0)::INT AS pending_proposal_count,
       (COUNT(h.id) FILTER (WHERE h.is_active = TRUE) + COALESCE(pc.pending_proposal_count, 0))::INT AS effective_hint_count,
       GREATEST(
               0,
               :minActiveHintsThreshold
                       - (COUNT(h.id) FILTER (WHERE h.is_active = TRUE) + COALESCE(pc.pending_proposal_count, 0))
       )::INT AS missing_hint_count,
       pc.last_generated_at
FROM cl_schema.countries c
LEFT JOIN cl_schema.hints h ON h.country_id = c.id
LEFT JOIN proposal_counts pc ON pc.country_id = c.id
WHERE c.id IN (:countryIds)
GROUP BY c.id, c.iso_alpha2, c.name_en, pc.pending_proposal_count, pc.last_generated_at
ORDER BY CASE
             WHEN pc.last_generated_at IS NULL THEN 0
             ELSE 1
         END ASC,
         pc.last_generated_at ASC,
         effective_hint_count ASC,
         c.name_en ASC
