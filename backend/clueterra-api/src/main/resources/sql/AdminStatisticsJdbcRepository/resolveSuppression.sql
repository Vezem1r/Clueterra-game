UPDATE statistic_suppression suppression
SET is_active = FALSE,
    resolved_at = NOW()
FROM country_statistics statistic
WHERE statistic.id = :id
  AND suppression.is_active = TRUE
  AND suppression.country_id = statistic.country_id
  AND suppression.source = statistic.source
  AND suppression.source_indicator = statistic.indicator_code
  AND suppression.source_year IS NOT DISTINCT FROM statistic.year
  AND suppression.effective_date IS NOT DISTINCT FROM statistic.effective_date
