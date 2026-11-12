INSERT
INTO indicator_config (indicator_code, display_name, category, hint_type, quality_score, base_difficulty, weight,
                       high_interest, is_trending, trending_frequency, exclude_from_ranking, exclude_from_percentile,
                       hint_template)
VALUES ('HYDRORIVERS_RIVER_SKELETON_1', 'HydroRIVERS river skeleton with three labels', 'GEOGRAPHY',
        'IMAGE_FACT', 0.95, 24, 0.92, TRUE, FALSE, 'STATIC', TRUE, TRUE,
        'Three major rivers are labelled. Identify the country from its river network.'),
       ('HYDRORIVERS_RIVER_SKELETON_2', 'HydroRIVERS river skeleton with one label', 'GEOGRAPHY',
        'IMAGE_FACT', 0.95, 42, 0.92, TRUE, FALSE, 'STATIC', TRUE, TRUE,
        'One major river is labelled. Which country contains this river network?'),
       ('HYDRORIVERS_RIVER_SKELETON_3', 'HydroRIVERS river skeleton without labels', 'GEOGRAPHY',
        'IMAGE_FACT', 0.95, 60, 0.92, TRUE, FALSE, 'STATIC', TRUE, TRUE,
        'No names this time. Identify the country from its major river network alone.')
ON CONFLICT (indicator_code) DO UPDATE
    SET display_name            = EXCLUDED.display_name,
        category                = EXCLUDED.category,
        hint_type               = EXCLUDED.hint_type,
        quality_score           = EXCLUDED.quality_score,
        base_difficulty         = EXCLUDED.base_difficulty,
        weight                  = EXCLUDED.weight,
        high_interest           = EXCLUDED.high_interest,
        is_trending             = EXCLUDED.is_trending,
        trending_frequency      = EXCLUDED.trending_frequency,
        exclude_from_ranking    = EXCLUDED.exclude_from_ranking,
        exclude_from_percentile = EXCLUDED.exclude_from_percentile,
        hint_template           = EXCLUDED.hint_template;
