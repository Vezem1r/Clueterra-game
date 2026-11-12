CREATE TABLE IF NOT EXISTS hints (
    id                    BIGINT        PRIMARY KEY,
    country_id            BIGINT        NOT NULL,
    category              VARCHAR(30)   NOT NULL,
    hint_type             VARCHAR(30)   NOT NULL,
    hint_text             TEXT          NOT NULL,
    difficulty            INTEGER       NOT NULL CHECK (difficulty >= 1 AND difficulty <= 100),
    quality_score         NUMERIC(5, 2) NOT NULL CHECK (quality_score >= 0 AND quality_score <= 100),
    source                VARCHAR(30)   NOT NULL,
    source_indicator      VARCHAR(50),
    source_year           INTEGER,
    image_resource_id     BIGINT,
    content_hash          VARCHAR(64)   NOT NULL,
    content_revision_hash VARCHAR(64),
    review_status         VARCHAR(20)   NOT NULL DEFAULT 'NOT_REVIEWED',
    last_reviewed_at      TIMESTAMPTZ,
    last_review_run_id    BIGINT,
    ambiguity_count       INTEGER,
    times_used            INTEGER       NOT NULL DEFAULT 0,
    times_correct         INTEGER       DEFAULT 0,
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP,
    review_claim_run_id   BIGINT,
    review_retry_after_at TIMESTAMPTZ,
    editorial_text_locked BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_hints_country FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE CASCADE,
    CONSTRAINT uq_hints_content UNIQUE (country_id, content_hash),
    CONSTRAINT chk_hints_review_status
        CHECK (review_status IN ('NOT_REVIEWED', 'IN_REVIEW', 'ACTION_PENDING', 'REVIEWED', 'STALE', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_hints_country_id ON hints (country_id);

CREATE INDEX IF NOT EXISTS idx_hints_category ON hints (category);

CREATE INDEX IF NOT EXISTS idx_hints_difficulty ON hints (difficulty);

CREATE INDEX IF NOT EXISTS idx_hints_is_active ON hints (is_active) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_hints_country_active ON hints (country_id, is_active) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_hints_active_difficulty ON hints (is_active, difficulty) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_hints_quality_score ON hints (quality_score DESC);

CREATE INDEX IF NOT EXISTS idx_hints_review_queue
    ON hints (review_status, last_reviewed_at, updated_at)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_hints_image_resource
    ON hints (image_resource_id) WHERE image_resource_id IS NOT NULL;

CREATE INDEX idx_hints_review_claim
    ON cl_schema.hints (review_claim_run_id)
    WHERE review_status = 'IN_REVIEW';

CREATE INDEX idx_hints_due_for_review
    ON cl_schema.hints (review_retry_after_at)
    WHERE is_active = TRUE AND review_status = 'REVIEWED' AND review_retry_after_at IS NOT NULL;

COMMENT ON TABLE hints IS 'Generated hints for countries';

COMMENT ON COLUMN hints.category IS 'Category: GEOGRAPHY, ECONOMY, DEMOGRAPHICS, etc.';

COMMENT ON COLUMN hints.hint_type IS 'Type: RANKING, COMPARISON, PERCENTILE, ABSOLUTE_FACT, etc.';

COMMENT ON COLUMN hints.difficulty IS 'Difficulty score from 1 (easy) to 100 (hard)';

COMMENT ON COLUMN hints.quality_score IS 'Quality score from 0.00 to 100.00';

COMMENT ON COLUMN hints.content_hash IS 'SHA-256 hash for deduplication';

COMMENT ON COLUMN hints.ambiguity_count IS 'Number of countries this hint could apply to';

COMMENT ON COLUMN hints.times_used IS 'Number of times this hint was shown';

COMMENT ON COLUMN hints.times_correct IS 'Number of times players guessed correctly with this hint';

COMMENT ON COLUMN hints.content_revision_hash IS 'Revision hash for review invalidation when text or image changes';

COMMENT ON COLUMN hints.review_status IS 'AI review lifecycle state for this hint';

COMMENT ON COLUMN hints.last_reviewed_at IS 'Timestamp of the latest AI review';

COMMENT ON COLUMN hints.last_review_run_id IS 'Latest AI run that reviewed this hint';

COMMENT ON COLUMN cl_schema.hints.review_claim_run_id IS 'Run that exclusively claimed this hint with FOR UPDATE SKIP LOCKED';

COMMENT ON COLUMN cl_schema.hints.review_retry_after_at IS 'Next AI review date for a time-sensitive clue';

COMMENT ON COLUMN cl_schema.hints.editorial_text_locked IS 'Prevents a source sync from replacing manually or AI-edited clue text';

CREATE TABLE IF NOT EXISTS hints_history (
    id                  BIGINT        PRIMARY KEY,
    country_id          BIGINT        NOT NULL,
    category            VARCHAR(30)   NOT NULL,
    hint_type           VARCHAR(30)   NOT NULL,
    hint_text           TEXT          NOT NULL,
    difficulty          INTEGER       NOT NULL,
    quality_score       NUMERIC(5, 2) NOT NULL,
    source              VARCHAR(30)   NOT NULL,
    source_indicator    VARCHAR(50),
    source_year         INTEGER,
    image_resource_id   BIGINT,
    content_hash        VARCHAR(64)   NOT NULL,
    ambiguity_count     INTEGER,
    times_used          INTEGER       NOT NULL DEFAULT 0,
    times_correct       INTEGER       DEFAULT 0,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    original_created_at TIMESTAMP,
    archived_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    archive_reason      VARCHAR(100)  NOT NULL DEFAULT 'data_refresh',
    CONSTRAINT fk_hints_history_country FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_hints_history_country ON hints_history (country_id);

CREATE INDEX IF NOT EXISTS idx_hints_history_archived ON hints_history (archived_at);

CREATE INDEX IF NOT EXISTS idx_hints_history_source ON hints_history (source_indicator);

CREATE INDEX IF NOT EXISTS idx_hints_history_image_resource
    ON hints_history (image_resource_id) WHERE image_resource_id IS NOT NULL;

COMMENT ON TABLE hints_history IS 'Archive of replaced dynamic hints';
