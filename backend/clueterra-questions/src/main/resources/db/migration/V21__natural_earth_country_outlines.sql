INSERT
INTO indicator_config (indicator_code, display_name, category, hint_type, quality_score, base_difficulty, weight,
                       high_interest, is_trending, trending_frequency, exclude_from_ranking, exclude_from_percentile,
                       hint_template)
VALUES ('NATURAL_EARTH_COUNTRY_OUTLINE_1', 'Natural Earth country outline 1', 'GEOGRAPHY', 'IMAGE_FACT',
        0.94, 28, 0.88, TRUE, FALSE, 'STATIC', TRUE, TRUE,
        'This country silhouette is your clue. Follow the coastline and border shape.'),
       ('NATURAL_EARTH_COUNTRY_OUTLINE_2', 'Natural Earth country outline 2', 'GEOGRAPHY', 'IMAGE_FACT',
        0.94, 28, 0.88, TRUE, FALSE, 'STATIC', TRUE, TRUE,
        'Study the outline: its proportions, coastlines, and islands point to the answer.'),
       ('NATURAL_EARTH_COUNTRY_OUTLINE_3', 'Natural Earth country outline 3', 'GEOGRAPHY', 'IMAGE_FACT',
        0.94, 28, 0.88, TRUE, FALSE, 'STATIC', TRUE, TRUE,
        'A map without labels: recognize the country from its shape alone.')
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
