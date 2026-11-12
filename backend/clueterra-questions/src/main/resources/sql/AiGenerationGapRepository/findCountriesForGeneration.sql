WITH proposal_history AS (
    SELECT p.country_id,
           MAX(p.created_at) FILTER (
               WHERE p.status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPLIED')
           ) AS last_generated_at
    FROM cl_ai_schema.ai_generated_hint_proposal p
    GROUP BY p.country_id
),
hint_stats AS (
    SELECT c.id                                                AS country_id,
           c.iso_alpha2                                        AS country_code,
           c.name_en                                           AS country_name,
           COUNT(h.id) FILTER (WHERE h.is_active = TRUE)::INT  AS active_hint_count,
           ph.last_generated_at
    FROM cl_schema.countries c
             LEFT JOIN cl_schema.hints h ON h.country_id = c.id
             LEFT JOIN proposal_history ph ON ph.country_id = c.id
    WHERE c.is_active = TRUE
    GROUP BY c.id, c.iso_alpha2, c.name_en, ph.last_generated_at
)
SELECT country_id,
       country_code,
       country_name,
       active_hint_count,
       0::INT                   AS pending_proposal_count,
       active_hint_count        AS effective_hint_count,
       0::INT                   AS missing_hint_count,
       last_generated_at
FROM hint_stats
ORDER BY CASE
             WHEN last_generated_at IS NULL THEN 0
             ELSE 1
         END ASC,
         last_generated_at ASC,
         active_hint_count ASC,
         country_id ASC
LIMIT :limit
