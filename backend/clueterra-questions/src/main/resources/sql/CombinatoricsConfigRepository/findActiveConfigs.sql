SELECT indicator_code,
       display_name,
       category,
       hint_type,
       family_type,
       base_difficulty,
       quality_score,
       max_ambiguity_count
FROM combo_indicator_config
WHERE is_active = true
ORDER BY indicator_code
