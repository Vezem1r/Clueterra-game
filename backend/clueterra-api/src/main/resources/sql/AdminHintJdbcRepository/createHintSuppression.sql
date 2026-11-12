INSERT INTO hint_suppression (
    country_id,
    source,
    source_indicator,
    category,
    hint_type,
    action,
    reason,
    created_by
)
SELECT hint.country_id,
       hint.source,
       hint.source_indicator,
       hint.category,
       hint.hint_type,
       :action,
       :reason,
       'admin'
FROM hints hint
WHERE hint.id = :id
ON CONFLICT (
    country_id,
    source,
    COALESCE(source_indicator, ''),
    category,
    hint_type
) WHERE is_active = TRUE
DO UPDATE SET action = EXCLUDED.action,
              reason = EXCLUDED.reason,
              created_by = EXCLUDED.created_by,
              created_at = NOW(),
              resolved_at = NULL
