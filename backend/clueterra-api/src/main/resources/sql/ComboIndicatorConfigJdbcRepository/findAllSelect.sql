WITH component_meta AS (
    SELECT indicator_code,
           COUNT(*)::int AS component_count,
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
           COUNT(*)::int AS predicate_count,
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
           COUNT(*)::int AS variant_count,
           MIN(template) AS hint_template,
           STRING_AGG(template, E'\n---\n' ORDER BY variant_no) AS variant_templates
    FROM combo_text_variant
    GROUP BY indicator_code
),
hint_meta AS (
    SELECT source_indicator,
           COUNT(*)::int AS hint_count,
           MIN(hint_text) AS example_hint
    FROM hints
    WHERE source = 'COMBINATORICS'
      AND is_active = TRUE
    GROUP BY source_indicator
)
SELECT cic.indicator_code,
       cic.display_name,
       cic.category,
       cic.hint_type,
       cic.family_type,
       cic.base_difficulty,
       cic.quality_score,
       cic.max_ambiguity_count,
       cic.is_active,
       COALESCE(cm.component_count, 0) AS component_count,
       COALESCE(pm.predicate_count, 0) AS predicate_count,
       COALESCE(vm.variant_count, 0) AS variant_count,
       cm.components_summary,
       pm.predicates_summary,
       vm.hint_template,
       vm.variant_templates,
       COALESCE(hm.hint_count, 0) AS hint_count,
       hm.example_hint
FROM combo_indicator_config cic
LEFT JOIN component_meta cm ON cm.indicator_code = cic.indicator_code
LEFT JOIN predicate_meta pm ON pm.indicator_code = cic.indicator_code
LEFT JOIN variant_meta vm ON vm.indicator_code = cic.indicator_code
LEFT JOIN hint_meta hm ON hm.source_indicator = cic.indicator_code
