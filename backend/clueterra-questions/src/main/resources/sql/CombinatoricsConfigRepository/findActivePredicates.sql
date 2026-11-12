SELECT indicator_code,
       predicate_order,
       left_component_key,
       operator,
       comparison_mode,
       right_value_numeric,
       right_component_key
FROM combo_indicator_predicate
ORDER BY indicator_code, predicate_order
