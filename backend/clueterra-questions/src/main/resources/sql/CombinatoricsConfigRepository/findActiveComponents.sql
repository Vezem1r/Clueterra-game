SELECT indicator_code,
       component_key,
       stat_source,
       stat_indicator_code,
       formatter,
       required
FROM combo_indicator_component
ORDER BY indicator_code, component_key
