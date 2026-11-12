CREATE TABLE IF NOT EXISTS indicator_config (
    indicator_code          VARCHAR(100)  PRIMARY KEY,
    display_name            VARCHAR(200),
    category                VARCHAR(50),
    unit                    VARCHAR(50),
    weight                  DECIMAL(3, 2) DEFAULT 0.50,
    profile_level           VARCHAR(20)   DEFAULT 'LOW',
    lower_is_better         BOOLEAN       DEFAULT FALSE,
    exclude_from_ranking    BOOLEAN       DEFAULT FALSE,
    exclude_from_percentile BOOLEAN       DEFAULT FALSE,
    hint_template           TEXT,
    hint_type               VARCHAR(30),
    base_difficulty         INTEGER       DEFAULT 50,
    quality_score           DECIMAL(3, 2) DEFAULT 0.50,
    is_active               BOOLEAN       DEFAULT TRUE,
    created_at              TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    is_trending             BOOLEAN       DEFAULT FALSE,
    trending_frequency      VARCHAR(20)   DEFAULT NULL,
    high_interest           BOOLEAN       DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_indicator_config_category ON indicator_config (category);

CREATE INDEX IF NOT EXISTS idx_indicator_config_profile ON indicator_config (profile_level);

CREATE INDEX IF NOT EXISTS idx_indicator_config_active ON indicator_config (is_active);

COMMENT ON TABLE indicator_config IS 'Configuration for country statistic indicators';

COMMENT ON COLUMN indicator_config.indicator_code IS 'Unique indicator code (e.g., SP.POP.TOTL, GDP_PPP_2024)';

COMMENT ON COLUMN indicator_config.display_name IS 'Human-readable name for hint text';

COMMENT ON COLUMN indicator_config.quality_score IS 'Hint quality score (0.00-1.00), higher = better hint';

COMMENT ON COLUMN indicator_config.hint_template IS 'Custom template for hint text, supports %s and named placeholders like {value}';

COMMENT ON COLUMN indicator_config.profile_level IS 'HIGH, MEDIUM, or LOW - affects difficulty calculation';

COMMENT ON COLUMN indicator_config.lower_is_better IS 'TRUE for indicators like unemployment, mortality where lower is better';

COMMENT ON COLUMN indicator_config.exclude_from_ranking IS 'Exclude from ranking-based hints (e.g., DRIVING_SIDE)';

COMMENT ON COLUMN indicator_config.exclude_from_percentile IS 'Exclude from percentile-based hints';

COMMENT ON COLUMN indicator_config.high_interest IS 'Indicators that generate notable value hints for extreme cases';
