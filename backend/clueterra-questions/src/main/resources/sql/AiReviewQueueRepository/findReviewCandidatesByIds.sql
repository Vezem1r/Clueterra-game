SELECT h.id AS hint_id,
       h.country_id,
       c.iso_alpha2 AS country_code,
       c.name_en AS country_name,
       c.region AS country_region,
       c.subregion AS country_subregion,
       c.capital AS country_capital,
       c.difficulty_modifier AS difficulty_modifier,
       h.category,
       h.hint_type,
       h.hint_text,
       h.image_url,
       h.original_image_url,
       h.difficulty,
       h.quality_score,
       h.source,
       h.source_indicator,
       h.source_year,
       COALESCE(h.content_revision_hash, h.content_hash) AS content_revision_hash,
       h.review_status,
       h.updated_at,
       h.last_reviewed_at
FROM cl_media_schema.hint_projection h
JOIN cl_schema.countries c ON c.id = h.country_id
WHERE h.id IN (:hintIds)
  AND h.review_status = 'IN_REVIEW'
ORDER BY h.updated_at DESC, h.id DESC
