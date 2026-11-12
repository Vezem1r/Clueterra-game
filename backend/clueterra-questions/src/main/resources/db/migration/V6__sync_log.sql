CREATE TABLE IF NOT EXISTS data_sync_log (
    id                  BIGINT      PRIMARY KEY,
    source              VARCHAR(30) NOT NULL,
    sync_type           VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    countries_processed INTEGER     DEFAULT 0,
    statistics_saved    INTEGER     DEFAULT 0,
    hints_generated     INTEGER     DEFAULT 0,
    hints_deactivated   INTEGER     DEFAULT 0,
    error_message       TEXT,
    error_stack         TEXT,
    started_at          TIMESTAMP   NOT NULL,
    completed_at        TIMESTAMP,
    duration_ms         BIGINT,
    batch_id            BIGINT      DEFAULT NULL,
    country_code        VARCHAR(3),
    fetch_details       JSONB       DEFAULT NULL,
    CHECK (sync_type IN ('FULL', 'INCREMENTAL')),
    CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_sync_source ON data_sync_log (source);

CREATE INDEX IF NOT EXISTS idx_sync_status ON data_sync_log (status);

CREATE INDEX IF NOT EXISTS idx_sync_started_at ON data_sync_log (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_source_started ON data_sync_log (source, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_source_status ON data_sync_log (source, status);

CREATE INDEX IF NOT EXISTS idx_sync_batch ON data_sync_log (batch_id);

CREATE INDEX IF NOT EXISTS idx_sync_country_code ON data_sync_log (country_code);

COMMENT ON TABLE data_sync_log IS 'History of data synchronizations from external APIs';

COMMENT ON COLUMN data_sync_log.source IS 'Data source: WORLD_BANK, CIA_FACTBOOK, WIKIDATA, etc.';

COMMENT ON COLUMN data_sync_log.sync_type IS 'Type: FULL (complete refresh) or INCREMENTAL (updates only)';

COMMENT ON COLUMN data_sync_log.status IS 'Status: STARTED, COMPLETED, or FAILED';

COMMENT ON COLUMN data_sync_log.countries_processed IS 'Number of countries processed';

COMMENT ON COLUMN data_sync_log.statistics_saved IS 'Number of statistics saved';

COMMENT ON COLUMN data_sync_log.hints_generated IS 'Number of hints generated during this sync';

COMMENT ON COLUMN data_sync_log.hints_deactivated IS 'Number of hints deactivated during this sync';

COMMENT ON COLUMN data_sync_log.error_message IS 'Error details if status is FAILED';

COMMENT ON COLUMN data_sync_log.error_stack IS 'Stack trace if status is FAILED';

COMMENT ON COLUMN data_sync_log.duration_ms IS 'Sync duration in milliseconds';

COMMENT ON COLUMN data_sync_log.fetch_details IS 'Per-country fetch failure details: [{countryCode, feedType, error, statusCode}]';

-- Hint generation audit log.
CREATE TABLE IF NOT EXISTS hint_generation_log (
    id                 BIGINT      PRIMARY KEY,
    country_id         BIGINT      REFERENCES countries (id) ON DELETE CASCADE,
    trigger_type       VARCHAR(30) NOT NULL,
    hints_attempted    INTEGER     NOT NULL DEFAULT 0,
    hints_created      INTEGER     NOT NULL DEFAULT 0,
    hints_duplicates   INTEGER     NOT NULL DEFAULT 0,
    hints_failed       INTEGER     NOT NULL DEFAULT 0,
    avg_difficulty     DECIMAL(5, 2),
    avg_quality_score  DECIMAL(5, 4),
    categories_covered INTEGER     DEFAULT 0,
    hint_types_covered INTEGER     DEFAULT 0,
    started_at         TIMESTAMP   NOT NULL,
    completed_at       TIMESTAMP,
    duration_ms        BIGINT,
    error_message      TEXT,
    details            JSONB,
    lens_breakdown     JSONB,
    batch_id           BIGINT      DEFAULT NULL,
    country_code       VARCHAR(3),
    CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'SYNC', 'REGENERATION'))
);

CREATE INDEX IF NOT EXISTS idx_hint_gen_country ON hint_generation_log (country_id);

CREATE INDEX IF NOT EXISTS idx_hint_gen_trigger ON hint_generation_log (trigger_type);

CREATE INDEX IF NOT EXISTS idx_hint_gen_started ON hint_generation_log (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_hint_gen_country_started ON hint_generation_log (country_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_hint_gen_batch ON hint_generation_log (batch_id);

CREATE INDEX IF NOT EXISTS idx_hint_gen_country_code ON hint_generation_log (country_code);

COMMENT ON COLUMN hint_generation_log.details IS 'Detailed deduplication results and rejection reasons';

COMMENT ON COLUMN hint_generation_log.lens_breakdown IS 'Hints generated per lens type';

-- Batch operation audit log.
CREATE TABLE IF NOT EXISTS operation_batch_log (
    id               BIGINT      PRIMARY KEY,
    operation_type   VARCHAR(50) NOT NULL,
    trigger_type     VARCHAR(30) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    total_items      INTEGER     DEFAULT 0,
    successful_items INTEGER     DEFAULT 0,
    failed_items     INTEGER     DEFAULT 0,
    started_at       TIMESTAMP   NOT NULL,
    completed_at     TIMESTAMP,
    duration_ms      BIGINT,
    error_message    TEXT,
    summary          JSONB,
    CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'SYNC')),
    CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED', 'PARTIAL')),
    CONSTRAINT operation_batch_log_operation_type_check
        CHECK (operation_type IN (
            'HINT_GENERATION',
            'DATA_SYNC',
            'CHALLENGE_GENERATION',
            'IMAGE_MAINTENANCE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_batch_operation_type ON operation_batch_log (operation_type);

CREATE INDEX IF NOT EXISTS idx_batch_trigger_type ON operation_batch_log (trigger_type);

CREATE INDEX IF NOT EXISTS idx_batch_status ON operation_batch_log (status);

CREATE INDEX IF NOT EXISTS idx_batch_started_at ON operation_batch_log (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_batch_operation_started ON operation_batch_log (operation_type, started_at DESC);

COMMENT ON TABLE operation_batch_log IS 'Aggregated log for batch operations (multiple countries/sources)';

COMMENT ON COLUMN operation_batch_log.operation_type IS
    'Type: HINT_GENERATION, DATA_SYNC, CHALLENGE_GENERATION, IMAGE_MAINTENANCE';

COMMENT ON COLUMN operation_batch_log.trigger_type IS 'Trigger: SCHEDULED (cron), MANUAL (API), SYNC (after data sync)';

COMMENT ON COLUMN operation_batch_log.status IS 'Status: STARTED, COMPLETED, FAILED, PARTIAL (some items failed)';

COMMENT ON COLUMN operation_batch_log.summary IS 'JSON summary with detailed statistics';
