-- Local global-brands staging data.
CREATE TABLE IF NOT EXISTS cl_staging.global_brands (
    id                 BIGSERIAL    PRIMARY KEY,
    country_code       VARCHAR(2)   NOT NULL,
    brand_name         VARCHAR(200) NOT NULL,
    industry           VARCHAR(50)  NOT NULL,
    founded_year       INTEGER,
    global_recognition INTEGER      NOT NULL DEFAULT 5,
    rank               INTEGER      NOT NULL DEFAULT 1,
    last_fetched       BOOLEAN      NOT NULL DEFAULT FALSE,
    fetch_timestamp    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (country_code, brand_name)
);

CREATE INDEX IF NOT EXISTS idx_brands_country ON cl_staging.global_brands (country_code);

CREATE INDEX IF NOT EXISTS idx_brands_fetched ON cl_staging.global_brands (last_fetched);

CREATE INDEX IF NOT EXISTS idx_brands_industry ON cl_staging.global_brands (industry);

COMMENT ON TABLE cl_staging.global_brands IS 'Curated global brand rankings by country';
