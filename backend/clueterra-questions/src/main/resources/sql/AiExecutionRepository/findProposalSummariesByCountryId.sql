SELECT id,
       status,
       category,
       hint_type,
       hint_text,
       COALESCE(stored_image_url, image_source_url) AS image_url,
       difficulty,
       quality_score,
       source_indicator,
       created_at
FROM cl_media_schema.ai_hint_proposal_projection
WHERE country_id = :countryId
  AND hint_text IS NOT NULL
  AND status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'DUPLICATE', 'APPLIED')
ORDER BY created_at DESC
LIMIT :limit
