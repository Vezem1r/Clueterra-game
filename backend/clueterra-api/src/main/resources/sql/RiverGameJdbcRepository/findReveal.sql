SELECT question.id,
       question.name_en,
       country.iso_alpha2 AS country_code,
       question.length_km,
       question.borders_image_key,
       question.facts_source_url,
       question.facts::TEXT AS facts,
       media.wikipedia_url,
       photo.object_key AS photo_object_key,
       photo.author_name AS photo_author_name,
       photo.license_label AS photo_license_label,
       photo.license_url AS photo_license_url,
       photo.source_page_url AS photo_source_page_url
FROM cl_schema.river_game_question question
JOIN cl_schema.countries country ON country.id = question.country_id
LEFT JOIN cl_schema.river_game_river_media media ON media.river_key = question.river_key
LEFT JOIN LATERAL (
    SELECT current_image.object_key, current_image.author_name, current_image.license_label,
           current_image.license_url, current_image.source_page_url
    FROM cl_media_schema.image_current current_image
    WHERE current_image.image_resource_id = media.photo_resource_id
      AND current_image.lifecycle_status = 'ACTIVE'
      AND current_image.storage_bucket = 'hint-images'
      AND current_image.storage_status = 'AVAILABLE'
      AND current_image.object_key LIKE 'river-photos/%'
    ORDER BY current_image.image_rendition_id DESC
    LIMIT 1
) photo ON TRUE
WHERE question.id = :questionId
