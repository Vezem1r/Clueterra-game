SELECT h.category, COUNT(*)::INT AS cnt
FROM cl_schema.hints h
WHERE h.country_id = :countryId
  AND h.is_active = TRUE
GROUP BY h.category
ORDER BY cnt DESC
