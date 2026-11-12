SELECT country.id AS country_id,
       country.iso_alpha2 AS iso2,
       image.source_page_url
FROM cl_schema.countries country
JOIN cl_schema.silhouette_game_question question ON question.country_id = country.id AND question.is_active
JOIN cl_schema.hints hint ON hint.country_id = country.id
JOIN cl_media_schema.image_current image ON image.image_resource_id = hint.image_resource_id
WHERE country.is_active
  AND hint.is_active
  AND hint.source_indicator IN ('AI_LANDMARKS', 'AI_LANDMARK')
  AND image.lifecycle_status = 'ACTIVE'
  AND image.storage_status = 'AVAILABLE'
  AND image.license_label IS NOT NULL
  AND image.source_page_url LIKE 'https://commons.wikimedia.org/wiki/File:%'
GROUP BY country.id, country.iso_alpha2, image.source_page_url
ORDER BY country.iso_alpha2, image.source_page_url
