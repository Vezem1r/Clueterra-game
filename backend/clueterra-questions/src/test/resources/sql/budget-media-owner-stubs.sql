CREATE SCHEMA IF NOT EXISTS cl_budget_schema;
CREATE SCHEMA IF NOT EXISTS cl_budget_staging;

CREATE TABLE IF NOT EXISTS cl_budget_schema.project (
    id BIGINT PRIMARY KEY,
    name TEXT,
    country_code VARCHAR(2),
    wikidata_id TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.project_image (
    project_id BIGINT NOT NULL,
    image_resource_id BIGINT
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.question (
    id BIGINT PRIMARY KEY,
    name_snapshot TEXT,
    country_code VARCHAR(2),
    wikidata_id TEXT,
    image_resource_id BIGINT,
    is_published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.question_variant (
    id BIGINT PRIMARY KEY,
    source_question_id BIGINT,
    comparison_question_id BIGINT
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.daily_challenge (
    id BIGINT PRIMARY KEY,
    challenge_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.daily_challenge_question (
    challenge_id BIGINT NOT NULL,
    question_variant_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS cl_budget_staging.wikidata_candidate (
    wikidata_id TEXT NOT NULL,
    category TEXT NOT NULL,
    image_resource_id BIGINT,
    image_status TEXT
);
