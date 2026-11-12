UPDATE hint_suppression suppression
SET is_active = FALSE,
    resolved_at = NOW()
FROM hints hint
WHERE hint.id = :id
  AND suppression.is_active = TRUE
  AND suppression.country_id = hint.country_id
  AND suppression.source = hint.source
  AND suppression.category = hint.category
  AND suppression.hint_type = hint.hint_type
  AND suppression.source_indicator IS NOT DISTINCT FROM hint.source_indicator
