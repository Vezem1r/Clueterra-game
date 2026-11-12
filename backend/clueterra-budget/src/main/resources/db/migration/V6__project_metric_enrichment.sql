CREATE TABLE cl_budget_schema.project_metric_enrichment_seed (
    wikidata_id             VARCHAR(20)    NOT NULL,
    metric_type             VARCHAR(30)    NOT NULL,
    normalized_value        NUMERIC(24, 6) NOT NULL,
    normalized_unit         VARCHAR(30)    NOT NULL,
    source_value            NUMERIC(24, 6) NOT NULL,
    source_unit             VARCHAR(40)    NOT NULL,
    source_code             VARCHAR(40)    NOT NULL REFERENCES cl_budget_schema.source_registry (code),
    source_title            VARCHAR(500)   NOT NULL,
    source_url              TEXT           NOT NULL,
    claim_note              VARCHAR(500)   NOT NULL,
    replace_matching_source BOOLEAN        NOT NULL DEFAULT FALSE,
    PRIMARY KEY (wikidata_id, metric_type),
    CONSTRAINT chk_budget_metric_enrichment_wikidata CHECK (wikidata_id ~ '^Q[1-9][0-9]*$'),
    CONSTRAINT chk_budget_metric_enrichment_type CHECK (metric_type IN (
        'LENGTH', 'MAIN_SPAN', 'HEIGHT', 'FLOOR_AREA', 'CAPACITY',
        'POWER', 'ANNUAL_PASSENGERS', 'STORAGE_VOLUME', 'MASS')),
    CONSTRAINT chk_budget_metric_enrichment_value CHECK (normalized_value > 0 AND source_value > 0),
    CONSTRAINT chk_budget_metric_enrichment_url CHECK (source_url ~ '^https://')
);

CREATE OR REPLACE FUNCTION cl_budget_schema.materialize_project_metric_enrichments(
    p_project_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_project RECORD;
    v_seed RECORD;
    v_registry RECORD;
    v_snapshot_id BIGINT;
    v_fingerprint CHAR(32);
BEGIN
    SELECT project.id, project.wikidata_id
    INTO v_project
    FROM cl_budget_schema.project project
    WHERE project.id = p_project_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    FOR v_seed IN
        SELECT seed.*
        FROM cl_budget_schema.project_metric_enrichment_seed seed
        WHERE seed.wikidata_id = v_project.wikidata_id
    LOOP
        SELECT registry.*
        INTO v_registry
        FROM cl_budget_schema.source_registry registry
        WHERE registry.code = v_seed.source_code
            AND registry.is_active;

        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        v_fingerprint := MD5(CONCAT_WS('|',
            v_seed.wikidata_id,
            v_seed.metric_type,
            v_seed.normalized_value,
            v_seed.normalized_unit,
            v_seed.source_url));

        INSERT INTO cl_budget_schema.source_snapshot (
            source_code,
            external_id,
            title,
            canonical_url,
            reference_url,
            retrieved_at,
            content_fingerprint,
            license_label,
            license_url,
            terms_url,
            attribution_text,
            commercial_reuse_allowed,
            payload)
        VALUES (
            v_seed.source_code,
            v_seed.wikidata_id || ':CURATED_METRIC:' || v_seed.metric_type,
            v_seed.source_title,
            v_seed.source_url,
            v_seed.source_url,
            TIMESTAMPTZ '2026-09-24 00:00:00+00',
            v_fingerprint,
            v_registry.license_label,
            v_registry.license_url,
            v_registry.terms_url,
            v_registry.attribution_text,
            v_registry.commercial_reuse_allowed,
            JSONB_BUILD_OBJECT(
                'metricType', v_seed.metric_type,
                'normalizedValue', v_seed.normalized_value,
                'normalizedUnit', v_seed.normalized_unit,
                'sourceValue', v_seed.source_value,
                'sourceUnit', v_seed.source_unit,
                'claimNote', v_seed.claim_note,
                'curationVersion', 'V8'))
        ON CONFLICT (source_code, external_id, content_fingerprint) DO NOTHING;

        SELECT snapshot.id
        INTO v_snapshot_id
        FROM cl_budget_schema.source_snapshot snapshot
        WHERE snapshot.source_code = v_seed.source_code
            AND snapshot.external_id = v_seed.wikidata_id || ':CURATED_METRIC:' || v_seed.metric_type
            AND snapshot.content_fingerprint = v_fingerprint;

        IF v_seed.replace_matching_source THEN
            UPDATE cl_budget_schema.project_metric metric
            SET source_snapshot_id = v_snapshot_id
            WHERE metric.project_id = v_project.id
                AND metric.metric_type = v_seed.metric_type
                AND metric.is_primary
                AND metric.normalized_value = v_seed.normalized_value
                AND metric.normalized_unit = v_seed.normalized_unit;
        END IF;

        INSERT INTO cl_budget_schema.project_metric (
            project_id,
            metric_type,
            normalized_value,
            normalized_unit,
            source_value,
            source_unit,
            source_snapshot_id,
            claim_fingerprint,
            is_primary)
        SELECT
            v_project.id,
            v_seed.metric_type,
            v_seed.normalized_value,
            v_seed.normalized_unit,
            v_seed.source_value,
            v_seed.source_unit,
            v_snapshot_id,
            MD5(CONCAT_WS('|',
                v_project.id,
                v_seed.metric_type,
                v_seed.normalized_value,
                v_seed.normalized_unit,
                v_snapshot_id)),
            TRUE
        WHERE NOT EXISTS (
            SELECT 1
            FROM cl_budget_schema.project_metric existing
            WHERE existing.project_id = v_project.id
                AND existing.metric_type = v_seed.metric_type
                AND existing.is_primary)
        ON CONFLICT (project_id, metric_type, claim_fingerprint) DO NOTHING;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.materialize_project_metric_enrichments_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM cl_budget_schema.materialize_project_metric_enrichments(NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budget_project_materialize_metric_enrichments
AFTER INSERT OR UPDATE OF wikidata_id ON cl_budget_schema.project
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.materialize_project_metric_enrichments_trigger();
