INSERT INTO cl_media_schema.image_finding (
    image_resource_id, image_rendition_id, finding_code, severity, details
) VALUES (
    :assetId, :renditionId, :findingCode, :severity, CAST(:details AS JSONB)
)
ON CONFLICT (image_resource_id, COALESCE(image_rendition_id, 0), finding_code) WHERE state = 'OPEN'
DO UPDATE SET
    severity = EXCLUDED.severity,
    details = EXCLUDED.details,
    occurrence_count = cl_media_schema.image_finding.occurrence_count + 1,
    last_seen_at = NOW()
