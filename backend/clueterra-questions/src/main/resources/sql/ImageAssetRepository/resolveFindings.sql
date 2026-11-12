UPDATE cl_media_schema.image_finding
SET state = 'RESOLVED',
    resolved_at = NOW(),
    last_seen_at = NOW()
WHERE image_resource_id = :assetId
  AND image_rendition_id IS NOT DISTINCT FROM :renditionId
  AND state = 'OPEN'
  AND finding_code NOT IN (:activeFindingCodes)
