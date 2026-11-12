CREATE TABLE IF NOT EXISTS cl_staging.osm_country_statistics (
    id             BIGSERIAL     PRIMARY KEY,
    country_code   VARCHAR(10)   NOT NULL,
    indicator_code VARCHAR(50)   NOT NULL,
    category       VARCHAR(30)   NOT NULL,
    value          NUMERIC(20, 4),
    value_text     VARCHAR(500),
    weight         NUMERIC(3, 2) DEFAULT 0.50,
    fetched_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_osm_stat_code UNIQUE (country_code, indicator_code)
);

CREATE INDEX IF NOT EXISTS idx_osm_country_code ON cl_staging.osm_country_statistics (country_code);

CREATE SEQUENCE IF NOT EXISTS country_stats_tsid_seq
    MINVALUE 0
    MAXVALUE 4194303
    CYCLE;

CREATE OR REPLACE FUNCTION sync_osm_stats_to_main(p_country_code VARCHAR DEFAULT NULL) RETURNS void AS
$$
BEGIN
    INSERT INTO country_statistics (id, country_id, source, indicator_code, indicator_name, category,
                                    value, value_text, unit, year, confidence, weight, is_current, fetched_at)
    SELECT ((FLOOR((EXTRACT(EPOCH FROM CLOCK_TIMESTAMP()) * 1000) - 1577836800000)::BIGINT) << 22) |
           (nextval('country_stats_tsid_seq')::BIGINT),
           c.id,
           'OPEN_STREET_MAP',
           o.indicator_code,
           INITCAP(REPLACE(REPLACE(o.indicator_code, 'OSM_', ''), '_', ' ')),
           o.category,
           o.value,
           o.value_text,
           CASE
               WHEN o.indicator_code LIKE '%_LENGTH_KM' THEN 'km'
               WHEN o.indicator_code LIKE '%_PEAK' OR o.indicator_code LIKE '%_VOLCANO' THEN 'm'
               WHEN o.value_text IS NOT NULL THEN NULL
               ELSE 'count'
               END,
           EXTRACT(YEAR FROM CURRENT_DATE),
           0.6,
           o.weight,
           TRUE,
           NOW()
    FROM cl_staging.osm_country_statistics o
             JOIN countries c ON (c.iso_alpha2 = o.country_code OR c.iso_alpha3 = o.country_code)
    WHERE (p_country_code IS NULL OR o.country_code = p_country_code)
    ON CONFLICT (country_id, source, indicator_code, year, effective_date)
        DO UPDATE SET value      = EXCLUDED.value,
                      value_text = EXCLUDED.value_text,
                      weight     = EXCLUDED.weight,
                      fetched_at = EXCLUDED.fetched_at;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION sync_osm_stats_to_main IS 'Syncs OSM statistics to main country_statistics table';
