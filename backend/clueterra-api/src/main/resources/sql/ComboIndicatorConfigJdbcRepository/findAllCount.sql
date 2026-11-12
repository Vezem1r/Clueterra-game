WITH component_meta AS (
    SELECT indicator_code,
           STRING_AGG(
               component_key || ' <- ' || stat_source || '.' || stat_indicator_code,
               E'\n'
               ORDER BY component_key
           ) AS components_summary
    FROM combo_indicator_component
    GROUP BY indicator_code
),
predicate_meta AS (
    SELECT indicator_code,
           STRING_AGG(
               left_component_key || ' ' || operator || ' ' ||
               CASE
                   WHEN comparison_mode = 'COMPONENT' THEN COALESCE(right_component_key, '?')
                   ELSE COALESCE(
                       TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM right_value_numeric::text)),
                       '?'
                   )
               END,
               E'\n'
               ORDER BY predicate_order
           ) AS predicates_summary
    FROM combo_indicator_predicate
    GROUP BY indicator_code
),
variant_meta AS (
    SELECT indicator_code,
           STRING_AGG(template, E'\n---\n' ORDER BY variant_no) AS variant_templates
    FROM combo_text_variant
    GROUP BY indicator_code
)
SELECT COUNT(*)
FROM combo_indicator_config cic
LEFT JOIN component_meta cm ON cm.indicator_code = cic.indicator_code
LEFT JOIN predicate_meta pm ON pm.indicator_code = cic.indicator_code
LEFT JOIN variant_meta vm ON vm.indicator_code = cic.indicator_code
