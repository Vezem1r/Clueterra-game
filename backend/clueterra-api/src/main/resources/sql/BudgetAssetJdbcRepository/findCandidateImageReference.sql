SELECT media.object_key,
       media.original_url AS fallback_image_url,
       media.original_url AS original_image_url
FROM cl_budget_staging.wikidata_candidate candidate
JOIN cl_media_schema.image_current media
  ON media.image_resource_id = candidate.image_resource_id
 AND media.storage_bucket = 'budget-images'
 AND media.lifecycle_status = 'ACTIVE'
 AND media.storage_status = 'AVAILABLE'
WHERE candidate.wikidata_id = :wikidataId
  AND candidate.category = :category
  AND media.source_sha1 = :sourceSha1
  AND candidate.last_fetched
  AND candidate.image_status = 'AVAILABLE'
ORDER BY CASE media.rendition_type WHEN 'DISPLAY' THEN 0 WHEN 'ORIGINAL' THEN 1 ELSE 2 END,
         media.image_rendition_id
LIMIT 1
