CREATE SCHEMA IF NOT EXISTS cl_ai_schema;

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_run (
    id                      BIGINT      PRIMARY KEY,
    run_type                VARCHAR(20) NOT NULL,
    trigger_type            VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    provider                VARCHAR(40),
    model                   VARCHAR(120),
    prompt_version          VARCHAR(80),
    requested_by            VARCHAR(120),
    total_stages            INTEGER     NOT NULL DEFAULT 0,
    prepared_items          INTEGER     NOT NULL DEFAULT 0,
    processed_items         INTEGER     NOT NULL DEFAULT 0,
    changed_items           INTEGER     NOT NULL DEFAULT 0,
    generated_items         INTEGER     NOT NULL DEFAULT 0,
    estimated_input_tokens  BIGINT      NOT NULL DEFAULT 0,
    estimated_output_tokens BIGINT      NOT NULL DEFAULT 0,
    actual_input_tokens     BIGINT      NOT NULL DEFAULT 0,
    actual_output_tokens    BIGINT      NOT NULL DEFAULT 0,
    tool_call_count         INTEGER     NOT NULL DEFAULT 0,
    configuration           JSONB,
    summary                 JSONB,
    error_message           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    CONSTRAINT chk_ai_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT chk_ai_run_status
        CHECK (status IN ('PREPARED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'NO_WORK', 'SKIPPED')),
    CONSTRAINT chk_ai_run_type
        CHECK (run_type IN ('REVIEW', 'GENERATION', 'EMBEDDING'))
);

CREATE INDEX IF NOT EXISTS idx_ai_run_type_status
    ON cl_ai_schema.ai_run (run_type, status);

CREATE INDEX IF NOT EXISTS idx_ai_run_created_at
    ON cl_ai_schema.ai_run (created_at DESC);

COMMENT ON TABLE cl_ai_schema.ai_run IS 'Top-level AI run metadata for review and generation flows';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_run_stage (
    id                      BIGINT      PRIMARY KEY,
    run_id                  BIGINT      NOT NULL,
    stage_index             INTEGER     NOT NULL,
    stage_type              VARCHAR(30) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    item_count              INTEGER     NOT NULL DEFAULT 0,
    estimated_input_tokens  BIGINT      NOT NULL DEFAULT 0,
    estimated_output_tokens BIGINT      NOT NULL DEFAULT 0,
    actual_input_tokens     BIGINT      NOT NULL DEFAULT 0,
    actual_output_tokens    BIGINT      NOT NULL DEFAULT 0,
    image_count             INTEGER     NOT NULL DEFAULT 0,
    payload                 JSONB       NOT NULL DEFAULT '{}'::jsonb,
    result_summary          JSONB,
    error_message           TEXT,
    provider_request_id     VARCHAR(120),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    CONSTRAINT fk_ai_run_stage_run
        FOREIGN KEY (run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_run_stage_idx
        UNIQUE (run_id, stage_index),
    CONSTRAINT chk_ai_stage_type
        CHECK (stage_type IN ('REVIEW_BATCH', 'GENERATION_BATCH', 'POST_PROCESS')),
    CONSTRAINT chk_ai_stage_status
        CHECK (status IN ('PREPARED', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_run_stage_run_status
    ON cl_ai_schema.ai_run_stage (run_id, status);

COMMENT ON TABLE cl_ai_schema.ai_run_stage IS 'Prepared or executed stage batches inside an AI run';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_run_usage (
    id                     BIGINT      PRIMARY KEY,
    run_id                 BIGINT      NOT NULL,
    stage_id               BIGINT,
    request_index          INTEGER     NOT NULL DEFAULT 1,
    provider_request_id    VARCHAR(120),
    finish_reason          VARCHAR(50),
    estimated_input_tokens BIGINT      NOT NULL DEFAULT 0,
    prompt_tokens          BIGINT      NOT NULL DEFAULT 0,
    completion_tokens      BIGINT      NOT NULL DEFAULT 0,
    total_tokens           BIGINT      NOT NULL DEFAULT 0,
    tool_call_count        INTEGER     NOT NULL DEFAULT 0,
    image_input_count      INTEGER     NOT NULL DEFAULT 0,
    latency_ms             BIGINT,
    metadata               JSONB,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ai_run_usage_run
        FOREIGN KEY (run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_run_usage_stage
        FOREIGN KEY (stage_id) REFERENCES cl_ai_schema.ai_run_stage (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_run_usage_run_id
    ON cl_ai_schema.ai_run_usage (run_id);

COMMENT ON TABLE cl_ai_schema.ai_run_usage IS 'Per-request token and provider usage for AI runs';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_hint_change_set (
    id              BIGINT      PRIMARY KEY,
    run_id          BIGINT      NOT NULL,
    change_set_type VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    title           VARCHAR(200),
    summary         TEXT,
    total_items     INTEGER     NOT NULL DEFAULT 0,
    applied_items   INTEGER     NOT NULL DEFAULT 0,
    rejected_items  INTEGER     NOT NULL DEFAULT 0,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at      TIMESTAMPTZ,
    rejected_at     TIMESTAMPTZ,
    CONSTRAINT fk_ai_hint_change_set_run
        FOREIGN KEY (run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_change_set_type
        CHECK (change_set_type IN ('REVIEW', 'GENERATION')),
    CONSTRAINT chk_ai_change_set_status
        CHECK (status IN ('DRAFT', 'PARTIALLY_APPLIED', 'APPLIED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_hint_change_set_run_id
    ON cl_ai_schema.ai_hint_change_set (run_id);

COMMENT ON TABLE cl_ai_schema.ai_hint_change_set IS 'Draft change sets awaiting manual approval';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_generated_hint_proposal (
    id                      BIGINT      PRIMARY KEY,
    run_id                  BIGINT      NOT NULL,
    stage_id                BIGINT,
    change_set_id           BIGINT,
    country_id              BIGINT      NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    category                VARCHAR(30),
    hint_type               VARCHAR(30),
    hint_text               TEXT,
    image_resource_id       BIGINT,
    difficulty              INTEGER,
    quality_score           NUMERIC(5, 2),
    source                  VARCHAR(30),
    source_indicator        VARCHAR(100),
    source_year             INTEGER,
    fact_summary            TEXT,
    evidence_summary        TEXT,
    duplicate_check_summary TEXT,
    search_terms            JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ai_generated_hint_proposal_run
        FOREIGN KEY (run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_generated_hint_proposal_stage
        FOREIGN KEY (stage_id) REFERENCES cl_ai_schema.ai_run_stage (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_generated_hint_proposal_change_set
        FOREIGN KEY (change_set_id) REFERENCES cl_ai_schema.ai_hint_change_set (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_generated_hint_proposal_country
        FOREIGN KEY (country_id) REFERENCES cl_schema.countries (id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_generated_hint_proposal_status
        CHECK (status IN ('DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'DUPLICATE', 'REJECTED', 'APPLIED')),
    CONSTRAINT chk_ai_generated_hint_proposal_difficulty
        CHECK (difficulty IS NULL OR (difficulty >= 1 AND difficulty <= 100)),
    CONSTRAINT chk_ai_generated_hint_proposal_quality
        CHECK (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100))
);

CREATE INDEX IF NOT EXISTS idx_ai_generated_hint_proposal_run_id
    ON cl_ai_schema.ai_generated_hint_proposal (run_id);

CREATE INDEX IF NOT EXISTS idx_ai_generated_hint_proposal_country_status
    ON cl_ai_schema.ai_generated_hint_proposal (country_id, status);

COMMENT ON TABLE cl_ai_schema.ai_generated_hint_proposal IS 'Generated hint drafts before merge into hints';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_hint_change_item (
    id                BIGINT      PRIMARY KEY,
    change_set_id     BIGINT      NOT NULL,
    run_id            BIGINT      NOT NULL,
    proposal_id       BIGINT,
    item_type         VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    hint_id           BIGINT,
    country_id        BIGINT,
    reason_summary    TEXT,
    diff_summary      TEXT,
    original_snapshot JSONB,
    proposed_snapshot JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at        TIMESTAMPTZ,
    CONSTRAINT fk_ai_hint_change_item_change_set
        FOREIGN KEY (change_set_id) REFERENCES cl_ai_schema.ai_hint_change_set (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_hint_change_item_run
        FOREIGN KEY (run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_hint_change_item_proposal
        FOREIGN KEY (proposal_id) REFERENCES cl_ai_schema.ai_generated_hint_proposal (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_hint_change_item_hint
        FOREIGN KEY (hint_id) REFERENCES cl_schema.hints (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_hint_change_item_country
        FOREIGN KEY (country_id) REFERENCES cl_schema.countries (id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_hint_change_item_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED', 'CANCELLED')),
    CONSTRAINT chk_ai_hint_change_item_type
        CHECK (item_type IN ('HINT_UPDATE', 'HINT_DISABLE', 'HINT_CREATE', 'IMAGE_REPLACE', 'HINT_ESCALATE'))
);

CREATE INDEX IF NOT EXISTS idx_ai_hint_change_item_change_set
    ON cl_ai_schema.ai_hint_change_item (change_set_id, status);

CREATE INDEX IF NOT EXISTS idx_ai_hint_change_item_hint
    ON cl_ai_schema.ai_hint_change_item (hint_id);

COMMENT ON TABLE cl_ai_schema.ai_hint_change_item IS 'Individual draft hint changes inside a change set';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_hint_review_result (
    id                   BIGINT      PRIMARY KEY,
    run_id               BIGINT      NOT NULL,
    stage_id             BIGINT,
    hint_id              BIGINT      NOT NULL,
    source_revision_hash VARCHAR(64),
    decision             VARCHAR(30) NOT NULL,
    confidence           NUMERIC(4, 3),
    summary_reason       TEXT,
    evidence_summary     TEXT,
    proposed_text        TEXT,
    proposed_image_url   TEXT,
    disable_reason       TEXT,
    change_set_id        BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    proposed_difficulty  INTEGER,
    superseded_at        TIMESTAMPTZ,
    retry_after_days     INTEGER     CHECK (retry_after_days BETWEEN 1 AND 365),
    CONSTRAINT fk_ai_hint_review_result_run
        FOREIGN KEY (run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_hint_review_result_stage
        FOREIGN KEY (stage_id) REFERENCES cl_ai_schema.ai_run_stage (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_hint_review_result_hint
        FOREIGN KEY (hint_id) REFERENCES cl_schema.hints (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_hint_review_result_change_set
        FOREIGN KEY (change_set_id) REFERENCES cl_ai_schema.ai_hint_change_set (id) ON DELETE SET NULL,
    CONSTRAINT chk_ai_hint_review_decision
        CHECK (decision IN ('KEEP', 'EDIT_TEXT', 'DISABLE', 'REPLACE_IMAGE', 'EDIT_TEXT_AND_IMAGE', 'ESCALATE', 'ERROR')),
    CONSTRAINT chk_ai_hint_review_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT chk_ai_hint_review_proposed_difficulty
        CHECK (proposed_difficulty IS NULL OR proposed_difficulty BETWEEN 1 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_ai_hint_review_result_run_id
    ON cl_ai_schema.ai_hint_review_result (run_id);

CREATE INDEX IF NOT EXISTS idx_ai_hint_review_result_hint_id
    ON cl_ai_schema.ai_hint_review_result (hint_id);

CREATE UNIQUE INDEX uq_ai_hint_review_current_revision
    ON cl_ai_schema.ai_hint_review_result (hint_id, source_revision_hash)
    WHERE source_revision_hash IS NOT NULL AND superseded_at IS NULL;

COMMENT ON TABLE cl_ai_schema.ai_hint_review_result IS 'Per-hint review verdicts produced by AI';

COMMENT ON COLUMN cl_ai_schema.ai_hint_review_result.superseded_at IS 'Non-null only for historical duplicate verdicts retained from before revision-aware review claims';

CREATE TABLE IF NOT EXISTS cl_ai_schema.ai_budget_daily (
    budget_date         DATE        PRIMARY KEY,
    requests_limit      INTEGER     NOT NULL,
    requests_used       INTEGER     NOT NULL DEFAULT 0,
    input_tokens_limit  BIGINT      NOT NULL,
    input_tokens_used   BIGINT      NOT NULL DEFAULT 0,
    output_tokens_limit BIGINT      NOT NULL,
    output_tokens_used  BIGINT      NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE cl_ai_schema.ai_budget_daily IS 'Shared daily request/token budget across manual and scheduled AI runs';

ALTER TABLE cl_schema.hints
    ADD CONSTRAINT fk_hints_last_review_run FOREIGN KEY (last_review_run_id) REFERENCES cl_ai_schema.ai_run (id) ON DELETE SET NULL;
