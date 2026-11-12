SELECT h.*,
       c.iso_alpha2 AS country_code,
       c.name_en AS country_name
FROM cl_media_schema.hint_projection h
JOIN countries c ON c.id = h.country_id
WHERE h.is_active = TRUE
  AND LOWER(h.hint_text) LIKE :query
  AND (CAST(:countryId AS BIGINT) IS NULL OR h.country_id = :countryId)
ORDER BY h.quality_score DESC NULLS LAST, h.updated_at DESC
LIMIT :limit
