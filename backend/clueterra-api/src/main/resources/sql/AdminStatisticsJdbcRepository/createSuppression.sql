INSERT INTO statistic_suppression (
    country_id,
    source,
    source_indicator,
    source_year,
    effective_date,
    reason,
    created_by
)
SELECT statistic.country_id,
       statistic.source,
       statistic.indicator_code,
       statistic.year,
       statistic.effective_date,
       'Statistic marked as not current by an administrator',
       'admin'
FROM country_statistics statistic
WHERE statistic.id = :id
ON CONFLICT (
    country_id,
    source,
    source_indicator,
    COALESCE(source_year, -1),
    COALESCE(effective_date, DATE '0001-01-01')
) WHERE is_active = TRUE
DO UPDATE SET reason = EXCLUDED.reason,
              created_by = EXCLUDED.created_by,
              created_at = NOW(),
              resolved_at = NULL
