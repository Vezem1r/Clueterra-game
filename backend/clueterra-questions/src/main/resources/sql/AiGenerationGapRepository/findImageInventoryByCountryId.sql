WITH accepted AS (
    SELECT h.source_indicator
    FROM cl_schema.hints h
    WHERE h.country_id = :countryId
      AND h.is_active = TRUE
      AND h.source_indicator IN ('AI_LANDMARK', 'AI_CURRENCY')

    UNION ALL

    SELECT proposal.source_indicator
    FROM cl_ai_schema.ai_generated_hint_proposal proposal
    WHERE proposal.country_id = :countryId
      AND proposal.status = 'PENDING_APPROVAL'
      AND proposal.source_indicator IN ('AI_LANDMARK', 'AI_CURRENCY')
), rejected AS (
    SELECT proposal.source_indicator,
           MAX(proposal.created_at) AS last_rejected_at
    FROM cl_ai_schema.ai_generated_hint_proposal proposal
    WHERE proposal.country_id = :countryId
      AND proposal.status IN ('REJECTED', 'DUPLICATE')
      AND proposal.source_indicator IN ('AI_LANDMARK', 'AI_CURRENCY')
    GROUP BY proposal.source_indicator
)
SELECT COUNT(*) FILTER (WHERE accepted.source_indicator = 'AI_LANDMARK')::INT AS landmark_count,
       COUNT(*) FILTER (WHERE accepted.source_indicator = 'AI_CURRENCY')::INT AS currency_count,
       MAX(rejected.last_rejected_at) FILTER (
           WHERE rejected.source_indicator = 'AI_LANDMARK'
       ) AS last_rejected_landmark_at,
       MAX(rejected.last_rejected_at) FILTER (
           WHERE rejected.source_indicator = 'AI_CURRENCY'
       ) AS last_rejected_currency_at
FROM accepted
FULL JOIN rejected ON FALSE
