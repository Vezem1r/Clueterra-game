CREATE SCHEMA IF NOT EXISTS cl_schema;
CREATE SCHEMA IF NOT EXISTS cl_budget_schema;
CREATE SCHEMA IF NOT EXISTS cl_media_schema;

CREATE TABLE IF NOT EXISTS cl_schema.river_game_question
(
    id BIGINT PRIMARY KEY,
    country_id BIGINT NOT NULL,
    river_key VARCHAR(80) NOT NULL,
    name_en VARCHAR(160) NOT NULL,
    answer_aliases TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    rank_in_country SMALLINT NOT NULL,
    length_km NUMERIC(9, 1),
    plain_image_key VARCHAR(512),
    borders_image_key VARCHAR(512),
    course JSONB NOT NULL DEFAULT '{"bbox":[7,47,8,52],"coordinates":[[[8,47],[7,52]]]}',
    facts JSONB NOT NULL DEFAULT '[{"kind":"LENGTH","text":"Its reported length is about 1,230 km."}]',
    facts_source_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cl_schema.river_game_river_media
(
    river_key VARCHAR(80) PRIMARY KEY,
    wikipedia_url TEXT,
    photo_resource_id BIGINT,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cl_schema.river_game_hint
(
    question_id BIGINT NOT NULL REFERENCES cl_schema.river_game_question (id),
    position SMALLINT NOT NULL,
    kind VARCHAR(20),
    hint_text TEXT NOT NULL,
    source_url TEXT,
    place JSONB,
    PRIMARY KEY (question_id, position)
);

CREATE TABLE IF NOT EXISTS cl_schema.silhouette_game_question
(
    id BIGINT PRIMARY KEY,
    country_id BIGINT NOT NULL DEFAULT 0,
    iso2 CHAR(2) NOT NULL UNIQUE,
    tier SMALLINT NOT NULL,
    region VARCHAR(40) NOT NULL DEFAULT 'Europe',
    subregion VARCHAR(60) NOT NULL DEFAULT 'Western Europe',
    center_lng DOUBLE PRECISION NOT NULL DEFAULT 2.3,
    center_lat DOUBLE PRECISION NOT NULL DEFAULT 46.6,
    bounds JSONB NOT NULL DEFAULT '[-5,41,9,51]',
    names JSONB NOT NULL DEFAULT '{"en":"Country","ru":"Страна","cs":"Země"}',
    shape_path TEXT NOT NULL DEFAULT 'M0 0L10 0L10 10Z',
    flag_colors JSONB NOT NULL DEFAULT '[{"color":"#0055a4","share":0.5}]',
    facts JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    photo_resource_id BIGINT,
    landmark_qid VARCHAR(20),
    landmark_names JSONB,
    landmark_sitelinks INTEGER,
    photo_selection VARCHAR(10) NOT NULL DEFAULT 'AUTO',
    photo_selected_by VARCHAR(255),
    photo_synced_at TIMESTAMPTZ,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cl_schema.silhouette_game_hint
(
    question_id BIGINT NOT NULL REFERENCES cl_schema.silhouette_game_question (id),
    position SMALLINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    cost_units SMALLINT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    PRIMARY KEY (question_id, position)
);

CREATE TABLE IF NOT EXISTS cl_schema.flag_game_question
(
    id BIGINT PRIMARY KEY,
    country_id BIGINT NOT NULL DEFAULT 0,
    iso2 CHAR(2) NOT NULL UNIQUE,
    tier SMALLINT NOT NULL,
    region VARCHAR(40) NOT NULL DEFAULT 'Europe',
    subregion VARCHAR(60) NOT NULL DEFAULT 'Western Europe',
    names JSONB NOT NULL DEFAULT '{"en":"Country","ru":"Страна","cs":"Země"}',
    content_hash CHAR(64) NOT NULL DEFAULT REPEAT('0', 64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cl_schema.flag_game_hint
(
    question_id BIGINT NOT NULL REFERENCES cl_schema.flag_game_question (id),
    position SMALLINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    cost_units SMALLINT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    PRIMARY KEY (question_id, position)
);

CREATE TABLE IF NOT EXISTS cl_schema.quiz_game_country
(
    id BIGINT PRIMARY KEY,
    country_id BIGINT NOT NULL DEFAULT 0,
    iso2 CHAR(2) NOT NULL UNIQUE,
    tier SMALLINT NOT NULL,
    levels SMALLINT NOT NULL DEFAULT 1,
    region VARCHAR(40) NOT NULL DEFAULT 'Europe',
    subregion VARCHAR(60) NOT NULL DEFAULT 'Western Europe',
    names JSONB NOT NULL DEFAULT '{"en":"Country","ru":"Страна","cs":"Země"}',
    longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    neighbours CHAR(2)[] NOT NULL DEFAULT '{}',
    content_hash CHAR(64) NOT NULL DEFAULT REPEAT('0', 64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cl_schema.quiz_game_question
(
    id BIGINT PRIMARY KEY,
    quiz_country_id BIGINT NOT NULL REFERENCES cl_schema.quiz_game_country (id),
    kind VARCHAR(20) NOT NULL,
    level SMALLINT NOT NULL DEFAULT 1,
    subject JSONB,
    options JSONB NOT NULL,
    answer_index SMALLINT NOT NULL,
    fact JSONB NOT NULL DEFAULT '{"type":"REGION","subregion":"Western Europe"}',
    content_hash CHAR(64) NOT NULL DEFAULT REPEAT('0', 64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (quiz_country_id, kind)
);

CREATE TABLE IF NOT EXISTS cl_media_schema.image_resource
(
    id                         BIGINT PRIMARY KEY,
    provider                   VARCHAR(40) NOT NULL,
    source_key                 TEXT NOT NULL,
    external_id                TEXT,
    lifecycle_status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    validation_status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    consecutive_failures       INTEGER NOT NULL DEFAULT 0,
    next_validation_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_validated_at          TIMESTAMPTZ,
    last_validation_error      TEXT,
    current_rights_revision_id BIGINT
);

CREATE TABLE IF NOT EXISTS cl_media_schema.image_rights_revision
(
    id                BIGINT PRIMARY KEY,
    image_resource_id BIGINT NOT NULL
        REFERENCES cl_media_schema.image_resource (id) ON DELETE CASCADE,
    original_url      TEXT,
    download_url      TEXT,
    source_page_url   TEXT,
    author_name       TEXT,
    attribution_text  TEXT,
    license_label     VARCHAR(120),
    license_url       TEXT,
    source_sha1       VARCHAR(100),
    mime_type         VARCHAR(100),
    width             INTEGER,
    height            INTEGER
);

ALTER TABLE cl_media_schema.image_resource
    DROP CONSTRAINT IF EXISTS fk_test_image_resource_current_rights;
ALTER TABLE cl_media_schema.image_resource
    ADD CONSTRAINT fk_test_image_resource_current_rights
        FOREIGN KEY (current_rights_revision_id)
            REFERENCES cl_media_schema.image_rights_revision (id);

CREATE TABLE IF NOT EXISTS cl_media_schema.image_rendition
(
    id                    BIGINT PRIMARY KEY,
    image_resource_id     BIGINT NOT NULL
        REFERENCES cl_media_schema.image_resource (id) ON DELETE CASCADE,
    rights_revision_id    BIGINT
        REFERENCES cl_media_schema.image_rights_revision (id),
    storage_bucket        VARCHAR(100) NOT NULL,
    object_key            TEXT NOT NULL,
    rendition_type        VARCHAR(30) NOT NULL DEFAULT 'DISPLAY',
    mime_type             VARCHAR(100),
    width                 INTEGER,
    height                INTEGER,
    byte_size             BIGINT,
    content_sha256        VARCHAR(64),
    storage_status        VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    last_storage_check_at TIMESTAMPTZ,
    orphaned_at           TIMESTAMPTZ
);

CREATE OR REPLACE VIEW cl_media_schema.image_current AS
SELECT resource.id AS image_resource_id,
       resource.provider,
       resource.source_key,
       resource.external_id,
       resource.lifecycle_status,
       resource.validation_status,
       resource.consecutive_failures,
       resource.next_validation_at,
       resource.last_validated_at,
       resource.last_validation_error,
       rights.id AS rights_revision_id,
       rights.original_url,
       rights.download_url,
       rights.source_page_url,
       rights.author_name,
       rights.attribution_text,
       rights.license_label,
       rights.license_url,
       rights.source_sha1,
       rights.mime_type AS source_mime_type,
       rights.width AS source_width,
       rights.height AS source_height,
       rendition.id AS image_rendition_id,
       rendition.storage_bucket,
       rendition.object_key,
       rendition.rendition_type,
       rendition.mime_type,
       rendition.width,
       rendition.height,
       rendition.byte_size,
       rendition.content_sha256,
       rendition.storage_status,
       rendition.last_storage_check_at,
       rendition.orphaned_at
FROM cl_media_schema.image_resource resource
LEFT JOIN cl_media_schema.image_rights_revision rights
    ON rights.id = resource.current_rights_revision_id
LEFT JOIN cl_media_schema.image_rendition rendition
    ON rendition.image_resource_id = resource.id
   AND rendition.storage_status <> 'DELETED';

CREATE TABLE IF NOT EXISTS cl_budget_schema.project
(
    id BIGINT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.question
(
    id                    BIGINT PRIMARY KEY,
    project_id            BIGINT NOT NULL REFERENCES cl_budget_schema.project (id),
    country_code          CHAR(2) NOT NULL,
    region_name           VARCHAR(120) NOT NULL,
    category              VARCHAR(30) NOT NULL,
    answer_cost_usd       NUMERIC(20, 2) NOT NULL,
    image_alt_text        VARCHAR(500) NOT NULL,
    image_resource_id     BIGINT NOT NULL
        REFERENCES cl_media_schema.image_resource (id),
    is_published          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.question_variant
(
    id                     BIGINT PRIMARY KEY,
    source_question_id     BIGINT NOT NULL REFERENCES cl_budget_schema.question (id),
    comparison_question_id BIGINT REFERENCES cl_budget_schema.question (id),
    question_type          VARCHAR(30) NOT NULL,
    answer_value           NUMERIC(24, 6),
    answer_unit            VARCHAR(40),
    answer_choice          VARCHAR(20),
    prompt_payload         JSONB NOT NULL,
    reveal_payload         JSONB NOT NULL,
    difficulty             VARCHAR(20) NOT NULL,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.daily_challenge
(
    id             BIGINT PRIMARY KEY,
    challenge_date DATE NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
);

CREATE TABLE IF NOT EXISTS cl_budget_schema.daily_challenge_question
(
    challenge_id       BIGINT NOT NULL REFERENCES cl_budget_schema.daily_challenge (id) ON DELETE CASCADE,
    position           SMALLINT NOT NULL,
    question_variant_id BIGINT NOT NULL REFERENCES cl_budget_schema.question_variant (id),
    PRIMARY KEY (challenge_id, position),
    UNIQUE (challenge_id, question_variant_id)
);

CREATE TABLE IF NOT EXISTS cl_schema.countries
(
    id                  BIGINT PRIMARY KEY,
    iso_alpha2          VARCHAR(2),
    iso_alpha3          VARCHAR(3),
    name_en             VARCHAR(255),
    difficulty_modifier NUMERIC(5, 2)            DEFAULT 1.0,
    is_active           BOOLEAN                  DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cl_schema.daily_challenges
(
    id              BIGINT PRIMARY KEY,
    challenge_date  DATE        NOT NULL,
    challenge_level VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    countries_json  TEXT        NOT NULL DEFAULT '[]',
    hints_json      TEXT        NOT NULL DEFAULT '[]',
    difficulty      VARCHAR(20) NOT NULL DEFAULT 'EASY',
    total_countries INTEGER     NOT NULL DEFAULT 5,
    total_hints     INTEGER     NOT NULL DEFAULT 25,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cl_schema.hints
(
    id         BIGINT PRIMARY KEY,
    country_id BIGINT REFERENCES cl_schema.countries (id),
    hint_text  TEXT,
    is_active  BOOLEAN                  DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
