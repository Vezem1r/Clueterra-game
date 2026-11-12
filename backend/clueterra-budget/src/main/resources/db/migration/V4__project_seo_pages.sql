CREATE TABLE cl_budget_schema.project_seo_page (
    wikidata_id                   VARCHAR(20)              PRIMARY KEY,
    slug                          VARCHAR(120)             UNIQUE,
    cost_question                 VARCHAR(240)             NOT NULL,
    cost_kind                     VARCHAR(30)              DEFAULT 'REPORTED_COST',
    seo_title                     VARCHAR(90)              NOT NULL,
    meta_description              VARCHAR(220)             NOT NULL,
    keywords                      VARCHAR(600)             NOT NULL,
    intro                         TEXT,
    answer_detail                 TEXT,
    scope_text                    TEXT,
    context_text                  TEXT,
    methodology_text              TEXT,
    source_name                   VARCHAR(240)             NOT NULL,
    source_url                    TEXT                     NOT NULL,
    publication_status            VARCHAR(20)              NOT NULL DEFAULT 'DRAFT',
    published_at                  TIMESTAMP WITH TIME ZONE,
    content_origin                VARCHAR(20)              NOT NULL DEFAULT 'EDITORIAL',
    updated_by                    VARCHAR(200)             NOT NULL,
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    generated_content_fingerprint CHAR(32),
    CONSTRAINT chk_budget_project_seo_wikidata_id CHECK (wikidata_id ~ '^Q[1-9][0-9]*$'),
    CONSTRAINT chk_budget_project_seo_slug CHECK (
        (publication_status = 'DRAFT' AND (slug IS NULL OR slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'))
        OR (publication_status = 'PUBLISHED'
            AND slug IS NOT NULL
            AND slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')),
    CONSTRAINT chk_budget_project_seo_title CHECK (
        publication_status = 'DRAFT' OR LENGTH(BTRIM(seo_title)) BETWEEN 20 AND 90),
    CONSTRAINT chk_budget_project_seo_question CHECK (
        publication_status = 'DRAFT' OR LENGTH(BTRIM(cost_question)) BETWEEN 20 AND 240),
    CONSTRAINT chk_budget_project_seo_cost_kind CHECK (
        publication_status = 'DRAFT'
        OR (cost_kind IS NOT NULL AND cost_kind IN (
            'REPORTED_COST',
            'ORIGINAL_BUDGET',
            'APPROVED_BUDGET',
            'FINAL_COST',
            'CURRENT_ESTIMATE',
            'REVISED_BUDGET',
            'EXPENDITURE_TO_DATE',
            'FINANCING_AMOUNT',
            'CONTRACT_VALUE'))),
    CONSTRAINT chk_budget_project_seo_description CHECK (
        publication_status = 'DRAFT' OR LENGTH(BTRIM(meta_description)) BETWEEN 70 AND 220),
    CONSTRAINT chk_budget_project_seo_keywords CHECK (
        publication_status = 'DRAFT' OR BTRIM(keywords) <> ''),
    CONSTRAINT chk_budget_project_seo_content CHECK (
        (intro IS NULL OR BTRIM(intro) <> '')
        AND (answer_detail IS NULL OR BTRIM(answer_detail) <> '')
        AND (scope_text IS NULL OR BTRIM(scope_text) <> '')
        AND (context_text IS NULL OR BTRIM(context_text) <> '')
        AND (methodology_text IS NULL OR BTRIM(methodology_text) <> '')
        AND (
            publication_status = 'DRAFT'
            OR content_origin = 'GENERATED'
            OR (
                intro IS NOT NULL
                AND answer_detail IS NOT NULL
                AND scope_text IS NOT NULL
                AND context_text IS NOT NULL
                AND methodology_text IS NOT NULL))),
    CONSTRAINT chk_budget_project_seo_source CHECK (
        publication_status = 'DRAFT'
        OR (BTRIM(source_name) <> '' AND source_url ~ '^https?://')),
    CONSTRAINT chk_budget_project_seo_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT chk_budget_project_seo_content_origin CHECK (
        content_origin IN ('EDITORIAL', 'GENERATED')),
    CONSTRAINT chk_budget_project_seo_publication CHECK (
        (publication_status = 'DRAFT' AND published_at IS NULL)
        OR (publication_status = 'PUBLISHED' AND published_at IS NOT NULL)),
    CONSTRAINT chk_budget_project_seo_generated_fingerprint CHECK (
        generated_content_fingerprint IS NULL OR generated_content_fingerprint ~ '^[0-9a-f]{32}$')
);

CREATE INDEX idx_budget_project_seo_status_updated
    ON cl_budget_schema.project_seo_page (publication_status, updated_at DESC);

CREATE INDEX idx_budget_project_seo_published_directory
    ON cl_budget_schema.project_seo_page (published_at DESC, wikidata_id)
    WHERE publication_status = 'PUBLISHED';

CREATE OR REPLACE FUNCTION cl_budget_schema.require_active_project_for_published_page()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.publication_status = 'PUBLISHED'
       AND NOT EXISTS (
           SELECT 1
           FROM cl_budget_schema.project project
           WHERE project.wikidata_id = NEW.wikidata_id
             AND project.is_active) THEN
        RAISE EXCEPTION 'Cannot publish project page % without an active project',
            NEW.wikidata_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_project_seo_page_requires_active_project
BEFORE INSERT OR UPDATE OF publication_status, wikidata_id
ON cl_budget_schema.project_seo_page
FOR EACH ROW
EXECUTE FUNCTION cl_budget_schema.require_active_project_for_published_page();

CREATE OR REPLACE FUNCTION cl_budget_schema.unpublish_inactive_project_page()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE cl_budget_schema.project_seo_page
        SET publication_status = 'DRAFT',
            published_at = NULL,
            updated_at = NOW()
        WHERE wikidata_id = OLD.wikidata_id
          AND publication_status = 'PUBLISHED';
        RETURN OLD;
    END IF;
    IF NOT NEW.is_active THEN
        UPDATE cl_budget_schema.project_seo_page
        SET publication_status = 'DRAFT',
            published_at = NULL,
            updated_at = NOW()
        WHERE wikidata_id = NEW.wikidata_id
          AND publication_status = 'PUBLISHED';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_project_unpublishes_inactive_page
AFTER UPDATE OF is_active OR DELETE
ON cl_budget_schema.project
FOR EACH ROW
EXECUTE FUNCTION cl_budget_schema.unpublish_inactive_project_page();

CREATE TABLE cl_budget_schema.project_seo_reference (
    id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    wikidata_id VARCHAR(20) NOT NULL
        REFERENCES cl_budget_schema.project_seo_page (wikidata_id) ON DELETE CASCADE,
    source_name   VARCHAR(240) NOT NULL,
    source_url    TEXT         NOT NULL,
    claim_context VARCHAR(500) NOT NULL,
    display_order SMALLINT     NOT NULL,
    CONSTRAINT uq_budget_project_seo_reference_order UNIQUE (wikidata_id, display_order),
    CONSTRAINT chk_budget_project_seo_reference_name CHECK (BTRIM(source_name) <> ''),
    CONSTRAINT chk_budget_project_seo_reference_url CHECK (source_url ~ '^https?://'),
    CONSTRAINT chk_budget_project_seo_reference_claim CHECK (BTRIM(claim_context) <> ''),
    CONSTRAINT chk_budget_project_seo_reference_order CHECK (display_order BETWEEN 1 AND 12)
);

CREATE TABLE cl_budget_schema.project_seo_fact (
    id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    wikidata_id VARCHAR(20) NOT NULL
        REFERENCES cl_budget_schema.project_seo_page (wikidata_id) ON DELETE CASCADE,
    fact_label    VARCHAR(120) NOT NULL,
    fact_value    VARCHAR(500) NOT NULL,
    source_name   VARCHAR(240) NOT NULL,
    source_url    TEXT         NOT NULL,
    display_order SMALLINT     NOT NULL,
    CONSTRAINT uq_budget_project_seo_fact_order UNIQUE (wikidata_id, display_order),
    CONSTRAINT chk_budget_project_seo_fact_label CHECK (BTRIM(fact_label) <> ''),
    CONSTRAINT chk_budget_project_seo_fact_value CHECK (BTRIM(fact_value) <> ''),
    CONSTRAINT chk_budget_project_seo_fact_source CHECK (
        BTRIM(source_name) <> '' AND source_url ~ '^https?://'),
    CONSTRAINT chk_budget_project_seo_fact_order CHECK (display_order BETWEEN 1 AND 12)
);
