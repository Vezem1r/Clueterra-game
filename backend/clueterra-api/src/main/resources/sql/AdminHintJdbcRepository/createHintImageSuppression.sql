INSERT INTO hint_image_suppression (
    country_id,
    source,
    source_indicator,
    category,
    hint_type,
    image_resource_id,
    reason,
    created_by
)
SELECT hint.country_id,
       hint.source,
       hint.source_indicator,
       hint.category,
       hint.hint_type,
       hint.image_resource_id,
       :reason,
       'admin'
FROM hints hint
WHERE hint.id = :id
  AND hint.image_resource_id IS NOT NULL
ON CONFLICT ON CONSTRAINT uq_hint_image_suppression
DO UPDATE SET reason = EXCLUDED.reason,
              created_by = EXCLUDED.created_by,
              is_active = TRUE,
              created_at = NOW(),
              resolved_at = NULL
