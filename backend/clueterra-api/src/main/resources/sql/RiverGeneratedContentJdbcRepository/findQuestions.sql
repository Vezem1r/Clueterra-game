SELECT q.id, c.iso_alpha2 AS country_code, c.name_en AS country_name,
       q.name_en, q.rank_in_country, q.length_km,
       q.plain_image_key, q.borders_image_key, q.is_active,
       media.wikipedia_url, photo.object_key AS photo_object_key,
       photo.author_name AS photo_author_name, photo.license_label AS photo_license_label,
       COALESCE(jsonb_agg(jsonb_build_object(
           'position', h.position, 'kind', h.kind,
           'text', h.hint_text, 'sourceUrl', h.source_url
       ) ORDER BY h.position) FILTER (WHERE h.position IS NOT NULL), '[]'::jsonb)::text AS hints
FROM cl_schema.river_game_question q
JOIN cl_schema.countries c ON c.id = q.country_id
LEFT JOIN cl_schema.river_game_hint h ON h.question_id = q.id
LEFT JOIN cl_schema.river_game_river_media media ON media.river_key = q.river_key
LEFT JOIN LATERAL (
    SELECT current_image.object_key, current_image.author_name, current_image.license_label
    FROM cl_media_schema.image_current current_image
    WHERE current_image.image_resource_id = media.photo_resource_id
      AND current_image.lifecycle_status = 'ACTIVE'
      AND current_image.storage_bucket = 'hint-images'
      AND current_image.storage_status = 'AVAILABLE'
      AND current_image.object_key LIKE 'river-photos/%'
    ORDER BY current_image.image_rendition_id DESC
    LIMIT 1
) photo ON TRUE
