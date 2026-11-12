SELECT media.object_key,
       media.original_url AS fallback_image_url,
       media.original_url AS original_image_url
FROM cl_budget_schema.project_seo_page seo
JOIN cl_budget_schema.project project
  ON project.wikidata_id = seo.wikidata_id
 AND project.is_active
JOIN cl_budget_schema.project_image image
  ON image.project_id = project.id
JOIN cl_media_schema.image_current media
  ON media.image_resource_id = image.image_resource_id
 AND media.storage_bucket = 'budget-images'
 AND media.lifecycle_status = 'ACTIVE'
 AND media.storage_status = 'AVAILABLE'
WHERE seo.wikidata_id = :wikidataId
  AND seo.publication_status = 'PUBLISHED'
  AND media.source_sha1 = :sourceSha1
ORDER BY CASE media.rendition_type WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
         media.image_rendition_id
LIMIT 1
