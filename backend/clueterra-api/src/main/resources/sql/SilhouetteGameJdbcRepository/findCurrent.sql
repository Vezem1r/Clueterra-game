SELECT state.round_number,
       question.tier,
       question.iso2,
       question.shape_path,
       hints.hints,
       state.current_hints_used,
       state.current_attempts,
       EXISTS (
           SELECT 1 FROM cl_api_schema.user_xp_events xp
           WHERE xp.user_id = state.user_id AND xp.source_type = 'SILHOUETTE_GAME' AND xp.source_id = question.id
       ) AS already_rewarded,
       xp_settings.base_xp,
       xp_settings.hint_penalty_percent,
       photo.object_key AS photo_object_key,
       photo.author_name AS photo_author_name,
       photo.license_label AS photo_license_label,
       photo.license_url AS photo_license_url,
       photo.source_page_url AS photo_source_page_url,
       question.landmark_names::TEXT AS landmark_names
FROM cl_api_schema.silhouette_game_player_state state
JOIN cl_schema.silhouette_game_question question ON question.id = state.current_question_id
CROSS JOIN LATERAL (
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'position', hint.position, 'kind', hint.kind,
               'costUnits', hint.cost_units, 'payload', hint.payload
           ) ORDER BY hint.position), '[]'::jsonb)::TEXT AS hints
    FROM cl_schema.silhouette_game_hint hint
    WHERE hint.question_id = question.id
) hints
CROSS JOIN (
    SELECT COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'silhouette_game_base_xp'), 150) AS base_xp,
           COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'silhouette_game_hint_penalty_percent'), 8)
               AS hint_penalty_percent
    FROM cl_api_schema.app_settings
    WHERE key IN ('silhouette_game_base_xp', 'silhouette_game_hint_penalty_percent')
) xp_settings
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
WHERE state.user_id = :userId
