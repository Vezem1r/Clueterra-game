SELECT question.iso2,
       question.subregion,
       question.names::TEXT AS names,
       question.facts::TEXT AS facts,
       question.flag_colors::TEXT AS flag_colors,
       question.center_lng,
       question.center_lat,
       question.bounds::TEXT AS bounds,
       question.shape_path,
       photo.object_key AS photo_object_key,
       photo.author_name AS photo_author_name,
       photo.license_label AS photo_license_label,
       photo.license_url AS photo_license_url,
       photo.source_page_url AS photo_source_page_url,
       question.landmark_names::TEXT AS landmark_names
FROM cl_schema.silhouette_game_question question
LEFT JOIN LATERAL (
    SELECT image.object_key, image.author_name, image.license_label, image.license_url, image.source_page_url
    FROM cl_media_schema.image_current image
    WHERE image.image_resource_id = question.photo_resource_id
      AND image.lifecycle_status = 'ACTIVE'
      AND image.storage_status = 'AVAILABLE'
      AND image.storage_bucket = 'hint-images'
      AND image.object_key LIKE 'country-photos/%'
    ORDER BY image.image_rendition_id DESC
    LIMIT 1
) photo ON TRUE
WHERE question.id = :questionId
