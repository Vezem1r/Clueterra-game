CREATE TABLE IF NOT EXISTS country_statistics (
    id                BIGINT        PRIMARY KEY,
    country_id        BIGINT        NOT NULL,
    source            VARCHAR(30)   NOT NULL,
    indicator_code    VARCHAR(50)   NOT NULL,
    indicator_name    VARCHAR(200)  NOT NULL,
    category          VARCHAR(30)   NOT NULL,
    value             NUMERIC(20, 4),
    value_text        TEXT,
    unit              VARCHAR(50),
    year              INTEGER,
    global_rank       INTEGER,
    regional_rank     INTEGER,
    percentile        NUMERIC(5, 2),
    deviation_score   NUMERIC(10, 4),
    is_current        BOOLEAN       NOT NULL DEFAULT TRUE,
    confidence        NUMERIC(3, 2) DEFAULT 1.00,
    weight            NUMERIC(3, 2) DEFAULT 0.50,
    image_resource_id BIGINT,
    fetched_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    manual_override   BOOLEAN       NOT NULL DEFAULT FALSE,
    effective_date    DATE          DEFAULT NULL,
    is_dynamic        BOOLEAN       DEFAULT FALSE,
    CONSTRAINT fk_statistics_country FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE CASCADE,
    CONSTRAINT uq_statistics_unique UNIQUE NULLS NOT DISTINCT (country_id, source, indicator_code, year, effective_date)
);

CREATE INDEX IF NOT EXISTS idx_statistics_country_id ON country_statistics (country_id);

CREATE INDEX IF NOT EXISTS idx_statistics_source ON country_statistics (source);

CREATE INDEX IF NOT EXISTS idx_statistics_deviation ON country_statistics (deviation_score);

CREATE INDEX IF NOT EXISTS idx_statistics_category ON country_statistics (category);

CREATE INDEX IF NOT EXISTS idx_statistics_indicator ON country_statistics (indicator_code);

CREATE INDEX IF NOT EXISTS idx_statistics_year ON country_statistics (year DESC);

CREATE INDEX IF NOT EXISTS idx_statistics_fetched_at ON country_statistics (fetched_at DESC);

CREATE INDEX IF NOT EXISTS idx_statistics_country_category ON country_statistics (country_id, category);

CREATE INDEX IF NOT EXISTS idx_statistics_source_year ON country_statistics (source, year DESC);

CREATE INDEX IF NOT EXISTS idx_statistics_is_current ON country_statistics (is_current) WHERE is_current = TRUE;

CREATE INDEX IF NOT EXISTS idx_statistics_weight ON country_statistics (weight DESC);

CREATE INDEX IF NOT EXISTS idx_country_statistics_image_resource
    ON country_statistics (image_resource_id) WHERE image_resource_id IS NOT NULL;

COMMENT ON TABLE country_statistics IS 'All indicators fetched from external APIs';

COMMENT ON COLUMN country_statistics.source IS 'Data source: WORLD_BANK, CIA_FACTBOOK, WIKIDATA, etc.';

COMMENT ON COLUMN country_statistics.indicator_code IS 'Source-specific code (e.g., NY.GDP.PCAP.CD for World Bank)';

COMMENT ON COLUMN country_statistics.category IS 'Category: ECONOMY, DEMOGRAPHICS, GEOGRAPHY, etc.';

COMMENT ON COLUMN country_statistics.value IS 'Numeric value of the indicator';

COMMENT ON COLUMN country_statistics.value_text IS 'Text representation of non-numeric values';

COMMENT ON COLUMN country_statistics.unit IS 'Unit of measurement: USD, %, years, km², etc.';

COMMENT ON COLUMN country_statistics.global_rank IS 'Rank among all countries (1 = highest)';

COMMENT ON COLUMN country_statistics.regional_rank IS 'Rank within the region';

COMMENT ON COLUMN country_statistics.percentile IS 'Percentile (0-100)';

COMMENT ON COLUMN country_statistics.is_current IS 'Whether this is the most current data for this indicator';

COMMENT ON COLUMN country_statistics.confidence IS 'Confidence score of the data (0.00-1.00)';

COMMENT ON COLUMN country_statistics.effective_date IS 'For time-series/trending indicators: date this snapshot was recorded; NULL for static indicators';

COMMENT ON COLUMN country_statistics.is_dynamic IS 'Whether this statistic comes from a dynamic/trending data source';
