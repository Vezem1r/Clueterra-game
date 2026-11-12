SELECT q.id, q.iso2, q.names ->> 'en' AS name_en, q.tier, q.region, q.subregion, q.shape_path,
       q.is_active, q.facts::TEXT AS facts, q.generated_at,
       hints.hints, photo.object_key AS photo_object_key, photo.author_name AS photo_author_name,
       photo.source_page_url AS photo_source_page_url, photo.license_label AS photo_license_label,
       photo.license_url AS photo_license_url, photo.source_width AS photo_width, photo.source_height AS photo_height,
       q.photo_selection, q.photo_selected_by, q.landmark_qid, q.landmark_names ->> 'en' AS landmark_name,
       q.landmark_sitelinks,
       stats.players, stats.solved, stats.skips, stats.reveals, stats.average_hints
FROM cl_schema.silhouette_game_question q
CROSS JOIN LATERAL (
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'position', h.position, 'kind', h.kind, 'costUnits', h.cost_units, 'payload', h.payload
           ) ORDER BY h.position), '[]'::jsonb)::TEXT AS hints
    FROM cl_schema.silhouette_game_hint h
    WHERE h.question_id = q.id
) hints
CROSS JOIN LATERAL (
    SELECT COUNT(*) AS players,
           COUNT(*) FILTER (WHERE progress.first_solved_at IS NOT NULL) AS solved,
           COALESCE(SUM(progress.skip_count), 0) AS skips,
           COALESCE(SUM(progress.reveal_count), 0) AS reveals,
           AVG(progress.best_hints_used) AS average_hints
    FROM cl_api_schema.silhouette_game_progress progress
    WHERE progress.question_id = q.id
) stats
LEFT JOIN LATERAL (
    SELECT current_image.object_key, current_image.author_name, current_image.source_page_url,
           current_image.license_label, current_image.license_url, current_image.source_width, current_image.source_height
    FROM cl_media_schema.image_current current_image
    WHERE current_image.image_resource_id = q.photo_resource_id
      AND current_image.lifecycle_status = 'ACTIVE'
      AND current_image.storage_bucket = 'hint-images'
      AND current_image.storage_status = 'AVAILABLE'
      AND current_image.object_key LIKE 'country-photos/%'
    ORDER BY current_image.image_rendition_id DESC
    LIMIT 1
) photo ON TRUE
