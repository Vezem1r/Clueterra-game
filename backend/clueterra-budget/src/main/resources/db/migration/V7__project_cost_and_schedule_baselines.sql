CREATE TABLE cl_budget_schema.project_cost_baseline_seed (
    wikidata_id         VARCHAR(20)    PRIMARY KEY,
    original_budget_usd NUMERIC(24, 2) NOT NULL,
    source_title        VARCHAR(500)   NOT NULL,
    source_code VARCHAR(40) NOT NULL DEFAULT 'GAO'
        REFERENCES cl_budget_schema.source_registry (code),
    source_url          TEXT           NOT NULL DEFAULT 'https://www.gao.gov/products/gao-25-107591',
    reference_url       TEXT           NOT NULL DEFAULT 'https://www.gao.gov/assets/gao-25-107591.pdf',
    CONSTRAINT chk_budget_cost_baseline_seed_amount CHECK (original_budget_usd > 0)
);

CREATE TABLE cl_budget_schema.project_schedule_baseline_seed (
    wikidata_id             VARCHAR(20)  PRIMARY KEY,
    planned_completion_year INTEGER      NOT NULL,
    source_title            VARCHAR(500) NOT NULL,
    source_code VARCHAR(40) NOT NULL DEFAULT 'GAO'
        REFERENCES cl_budget_schema.source_registry (code),
    source_url              TEXT         NOT NULL DEFAULT 'https://www.gao.gov/products/gao-25-107591',
    reference_url           TEXT         NOT NULL DEFAULT 'https://www.gao.gov/assets/gao-25-107591.pdf',
    CONSTRAINT chk_budget_schedule_baseline_seed_year
        CHECK (planned_completion_year BETWEEN 1850 AND 2200)
);

CREATE OR REPLACE FUNCTION cl_budget_schema.materialize_project_cost_baseline(
    p_project_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_project RECORD;
    v_seed RECORD;
    v_snapshot_id BIGINT;
    v_payload JSONB;
BEGIN
    SELECT project.id, project.wikidata_id
    INTO v_project
    FROM cl_budget_schema.project project
    WHERE project.id = p_project_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT seed.*
    INTO v_seed
    FROM cl_budget_schema.project_cost_baseline_seed seed
    WHERE seed.wikidata_id = v_project.wikidata_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    v_payload := JSONB_BUILD_OBJECT(
        'costKind', 'ORIGINAL_BUDGET',
        'amountUsd', v_seed.original_budget_usd,
        'evidenceText', 'Original project life-cycle cost baseline in GAO-25-107591',
        'curationVersion', 'V9');

    v_snapshot_id := cl_budget_schema.save_budget_source_snapshot(
        v_seed.source_code,
        v_seed.wikidata_id || ':GAO_25_107591:ORIGINAL_BUDGET',
        v_seed.source_title || ' original cost baseline',
        v_seed.source_url,
        v_seed.reference_url,
        TIMESTAMPTZ '2026-09-24 00:00:00+00',
        v_payload);

    UPDATE cl_budget_schema.project_cost cost
    SET is_primary = FALSE
    WHERE cost.project_id = v_project.id
        AND cost.cost_kind = 'ORIGINAL_BUDGET'
        AND cost.is_primary;

    INSERT INTO cl_budget_schema.project_cost (
        project_id, cost_kind, source_amount_low, source_amount_high,
        source_currency, amount_usd_low, amount_usd_high, cost_year,
        units_per_usd, exchange_rate_date, exchange_rate_basis,
        exchange_rate_provider, source_snapshot_id, evidence_text, scope_note,
        verification_status, claim_fingerprint, is_primary)
    VALUES (
        v_project.id, 'ORIGINAL_BUDGET', v_seed.original_budget_usd,
        v_seed.original_budget_usd, 'USD', v_seed.original_budget_usd,
        v_seed.original_budget_usd, NULL, 1, DATE '2025-05-20',
        'ANNUAL_AVERAGE', 'NONE', v_snapshot_id,
        'Original project life-cycle cost baseline in GAO-25-107591',
        'NASA project life-cycle cost baseline', 'SOURCE_BACKED',
        MD5(CONCAT_WS('|', v_project.id, 'ORIGINAL_BUDGET',
            v_seed.original_budget_usd, v_snapshot_id)), TRUE)
    ON CONFLICT (project_id, cost_kind, claim_fingerprint) DO UPDATE
    SET source_snapshot_id = EXCLUDED.source_snapshot_id,
        is_primary = TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.materialize_project_schedule_baseline(
    p_project_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_project RECORD;
    v_seed RECORD;
    v_snapshot_id BIGINT;
    v_payload JSONB;
BEGIN
    SELECT project.id, project.wikidata_id
    INTO v_project
    FROM cl_budget_schema.project project
    WHERE project.id = p_project_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT seed.*
    INTO v_seed
    FROM cl_budget_schema.project_schedule_baseline_seed seed
    WHERE seed.wikidata_id = v_project.wikidata_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    v_payload := JSONB_BUILD_OBJECT(
        'milestoneType', 'PLANNED_COMPLETION',
        'milestoneYear', v_seed.planned_completion_year,
        'evidenceText', 'Original schedule baseline in GAO-25-107591',
        'curationVersion', 'V9');

    v_snapshot_id := cl_budget_schema.save_budget_source_snapshot(
        v_seed.source_code,
        v_seed.wikidata_id || ':GAO_25_107591:PLANNED_COMPLETION',
        v_seed.source_title || ' original schedule baseline',
        v_seed.source_url,
        v_seed.reference_url,
        TIMESTAMPTZ '2026-09-24 00:00:00+00',
        v_payload);

    UPDATE cl_budget_schema.project_schedule_milestone milestone
    SET is_primary = FALSE
    WHERE milestone.project_id = v_project.id
        AND milestone.milestone_type = 'PLANNED_COMPLETION'
        AND milestone.is_primary;

    INSERT INTO cl_budget_schema.project_schedule_milestone (
        project_id, milestone_type, milestone_date, milestone_year,
        date_precision, source_snapshot_id, claim_fingerprint, is_primary)
    VALUES (
        v_project.id, 'PLANNED_COMPLETION', NULL,
        v_seed.planned_completion_year, 'YEAR', v_snapshot_id,
        MD5(CONCAT_WS('|', v_project.id, 'PLANNED_COMPLETION',
            v_seed.planned_completion_year, v_snapshot_id)), TRUE)
    ON CONFLICT (project_id, milestone_type, claim_fingerprint) DO UPDATE
    SET source_snapshot_id = EXCLUDED.source_snapshot_id,
        is_primary = TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.materialize_project_baselines_after_image()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM cl_budget_schema.materialize_project_cost_baseline(NEW.project_id);
    PERFORM cl_budget_schema.materialize_project_schedule_baseline(NEW.project_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budget_project_image_materialize_baselines
AFTER INSERT OR UPDATE OF image_resource_id ON cl_budget_schema.project_image
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.materialize_project_baselines_after_image();
