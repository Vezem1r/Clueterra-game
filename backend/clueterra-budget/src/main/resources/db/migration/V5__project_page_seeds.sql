-- A stable editorial allow-list for project pages. The page itself is materialized only
-- when the reviewed project, the scoped non-disputed primary cost kind, and a complete image exist.
-- This lets a clean database discover dynamic Wikidata projects after Flyway has run
-- without losing the curated URL and cost-question scope.
CREATE TABLE cl_budget_schema.project_seo_seed (
    wikidata_id   VARCHAR(20)              PRIMARY KEY,
    slug          VARCHAR(120)             NOT NULL UNIQUE,
    cost_question VARCHAR(240)             NOT NULL,
    cost_kind     VARCHAR(30)              NOT NULL DEFAULT 'REPORTED_COST',
    is_enabled    BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_budget_project_seo_seed_wikidata CHECK (wikidata_id ~ '^Q[1-9][0-9]*$'),
    CONSTRAINT chk_budget_project_seo_seed_slug CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT chk_budget_project_seo_seed_question CHECK (
        LENGTH(BTRIM(cost_question)) BETWEEN 20 AND 240
        AND RIGHT(BTRIM(cost_question), 1) = '?'),
    CONSTRAINT chk_budget_project_seo_seed_cost_kind CHECK (cost_kind IN (
        'REPORTED_COST',
        'ORIGINAL_BUDGET',
        'APPROVED_BUDGET',
        'FINAL_COST',
        'CURRENT_ESTIMATE',
        'REVISED_BUDGET',
        'EXPENDITURE_TO_DATE',
        'FINANCING_AMOUNT',
        'CONTRACT_VALUE'))
);

CREATE TABLE cl_budget_schema.project_seo_seed_reference (
    wikidata_id VARCHAR(20) NOT NULL
        REFERENCES cl_budget_schema.project_seo_seed (wikidata_id) ON DELETE CASCADE,
    source_name   VARCHAR(240) NOT NULL,
    source_url    TEXT         NOT NULL,
    claim_context VARCHAR(500) NOT NULL,
    display_order SMALLINT     NOT NULL,
    PRIMARY KEY (wikidata_id, display_order),
    CONSTRAINT chk_budget_project_seo_seed_reference_url CHECK (source_url ~ '^https?://'),
    CONSTRAINT chk_budget_project_seo_seed_reference_order CHECK (display_order BETWEEN 1 AND 12)
);

CREATE TABLE cl_budget_schema.project_seo_seed_fact (
    wikidata_id VARCHAR(20) NOT NULL
        REFERENCES cl_budget_schema.project_seo_seed (wikidata_id) ON DELETE CASCADE,
    fact_label    VARCHAR(120) NOT NULL,
    fact_value    VARCHAR(500) NOT NULL,
    source_name   VARCHAR(240) NOT NULL,
    source_url    TEXT         NOT NULL,
    display_order SMALLINT     NOT NULL,
    PRIMARY KEY (wikidata_id, display_order),
    CONSTRAINT chk_budget_project_seo_seed_fact_url CHECK (source_url ~ '^https?://'),
    CONSTRAINT chk_budget_project_seo_seed_fact_order CHECK (display_order BETWEEN 1 AND 12)
);

CREATE OR REPLACE FUNCTION cl_budget_schema.format_budget_source_amount(
    p_amount NUMERIC,
    p_currency TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
    SELECT CASE
        WHEN p_amount >= 1000000000
            THEN TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TO_CHAR(p_amount / 1000000000, 'FM999999990.00')))
                || ' billion ' || p_currency
        WHEN p_amount >= 1000000
            THEN TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TO_CHAR(p_amount / 1000000, 'FM999999990.00')))
                || ' million ' || p_currency
        ELSE TO_CHAR(p_amount, 'FM999G999G999G990') || ' ' || p_currency
    END
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.project_source_display_name(
    p_source_name TEXT,
    p_source_url TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    v_host TEXT;
    v_normalized_name TEXT;
BEGIN
    v_host := LOWER(REGEXP_REPLACE(
        REGEXP_REPLACE(BTRIM(p_source_url), '^https?://', '', 'i'),
        '[/?:#].*$',
        ''));
    v_host := REGEXP_REPLACE(v_host, '^www\.', '');
    v_normalized_name := REGEXP_REPLACE(LOWER(BTRIM(p_source_name)), '^www\.', '');

    IF v_host = '' THEN
        RETURN BTRIM(p_source_name);
    END IF;

    IF (v_normalized_name LIKE '%wikipedia%' AND v_host !~ '(^|\.)wikipedia\.org$')
        OR (v_normalized_name LIKE '%wikidata%' AND v_host !~ '(^|\.)wikidata\.org$')
        OR v_normalized_name = v_host
        OR v_normalized_name = 'clueterra source-backed factual compilation' THEN
        RETURN CASE
            WHEN v_host = 'web.archive.org' THEN 'Internet Archive'
            WHEN v_host = 'doi.org' THEN 'DOI record'
            WHEN v_host = 'eia.gov' THEN 'U.S. Energy Information Administration'
            WHEN v_host = 'jpl.nasa.gov' THEN 'NASA Jet Propulsion Laboratory'
            WHEN v_host = 'history.nasa.gov' THEN 'NASA History Office'
            WHEN v_host ~ '(^|\.)nasa\.gov$' THEN 'NASA'
            WHEN v_host = 'news.google.com' THEN 'Google News Archive'
            WHEN v_host = 'books.google.com' THEN 'Google Books'
            WHEN v_host ~ '(^|\.)nytimes\.com$' THEN 'The New York Times'
            WHEN v_host ~ '(^|\.)latimes\.com$' THEN 'Los Angeles Times'
            WHEN v_host ~ '(^|\.)wsj\.com$' THEN 'The Wall Street Journal'
            WHEN v_host ~ '(^|\.)bbc\.(com|co\.uk)$' THEN 'BBC'
            WHEN v_host ~ '(^|\.)cnn\.com$' THEN 'CNN'
            WHEN v_host ~ '(^|\.)structurae\.net$' THEN 'Structurae'
            WHEN v_host ~ '(^|\.)euractiv\.com$' THEN 'Euractiv'
            WHEN v_host ~ '(^|\.)post-gazette\.com$' THEN 'Pittsburgh Post-Gazette'
            WHEN v_host ~ '(^|\.)newspapers\.com$' THEN 'Newspapers.com'
            WHEN v_host ~ '(^|\.)skyscrapercenter\.com$' THEN 'The Skyscraper Center'
            WHEN v_host ~ '(^|\.)(wsp|wspgroup)\.com$' THEN 'WSP'
            ELSE v_host
        END;
    END IF;

    RETURN BTRIM(p_source_name);
END;
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.normalize_project_source_display_name()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.source_name := cl_budget_schema.project_source_display_name(
        NEW.source_name,
        NEW.source_url);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_project_seo_page_source_name
BEFORE INSERT OR UPDATE OF source_name, source_url
ON cl_budget_schema.project_seo_page
FOR EACH ROW
EXECUTE FUNCTION cl_budget_schema.normalize_project_source_display_name();

CREATE TRIGGER trg_project_seo_reference_source_name
BEFORE INSERT OR UPDATE OF source_name, source_url
ON cl_budget_schema.project_seo_reference
FOR EACH ROW
EXECUTE FUNCTION cl_budget_schema.normalize_project_source_display_name();

CREATE TRIGGER trg_project_seo_fact_source_name
BEFORE INSERT OR UPDATE OF source_name, source_url
ON cl_budget_schema.project_seo_fact
FOR EACH ROW
EXECUTE FUNCTION cl_budget_schema.normalize_project_source_display_name();

CREATE OR REPLACE FUNCTION cl_budget_schema.materialize_seeded_project_page(
    p_project_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_materializer_actor CONSTANT TEXT := 'migration:V7-seed-materializer';
    v_generated_origin CONSTANT TEXT := 'GENERATED';
    v_page RECORD;
    v_amount TEXT;
    v_source_name TEXT;
    v_source_url TEXT;
    v_meta_description TEXT;
    v_content_origin TEXT;
    v_content_fingerprint CHAR(32);
BEGIN
    SELECT
        project.*,
        seed.slug AS seed_slug,
        seed.cost_question AS seed_cost_question,
        seed.cost_kind AS seed_cost_kind,
        cost.source_amount_low,
        cost.source_amount_high,
        cost.source_currency AS primary_source_currency,
        COALESCE(snapshot.title, registry.display_name, project.cost_source_name) AS primary_source_name,
        COALESCE(snapshot.reference_url, snapshot.canonical_url, project.cost_reference_url, project.cost_source_url) AS primary_source_url
    INTO v_page
    FROM cl_budget_schema.project project
    JOIN cl_budget_schema.project_seo_seed seed
        ON seed.wikidata_id = project.wikidata_id
        AND seed.is_enabled
    JOIN LATERAL (
        SELECT primary_cost.*
        FROM cl_budget_schema.project_cost primary_cost
        WHERE primary_cost.project_id = project.id
            AND primary_cost.is_primary
            AND primary_cost.cost_kind = seed.cost_kind
            AND primary_cost.verification_status <> 'DISPUTED'
    ) cost ON TRUE
    LEFT JOIN cl_budget_schema.source_snapshot snapshot
        ON snapshot.id = cost.source_snapshot_id
    LEFT JOIN cl_budget_schema.source_registry registry
        ON registry.code = snapshot.source_code
    JOIN cl_budget_schema.project_image_projection image
        ON image.project_id = project.id
        AND BTRIM(image.object_key) <> ''
        AND BTRIM(image.alt_text) <> ''
        AND BTRIM(image.author_name) <> ''
        AND BTRIM(image.license_label) <> ''
        AND image.width >= 800
        AND image.height >= 450
    WHERE project.id = p_project_id
        AND project.is_active
        AND (
            project.content_quality_status = 'EDITORIALLY_APPROVED'
            OR (
                project.cost_confidence_score >= 70
                AND project.cost_evidence_tier IN (
                    'OFFICIAL_SOURCE',
                    'REFERENCED_KNOWLEDGE_BASE',
                    'CITED_ENCYCLOPEDIA')))
        AND COALESCE(cost.scope_note, '') NOT LIKE 'IMPORTANT SCOPE CAVEAT:%';

    IF NOT FOUND OR v_page.primary_source_url !~ '^https?://' THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM cl_budget_schema.project_seo_page existing_page
        WHERE existing_page.slug = v_page.seed_slug
            AND existing_page.wikidata_id <> v_page.wikidata_id) THEN
        RETURN;
    END IF;

    v_amount := cl_budget_schema.format_budget_source_amount(
        (v_page.source_amount_low + v_page.source_amount_high) / 2,
        v_page.primary_source_currency);
    v_source_url := BTRIM(v_page.primary_source_url);
    v_source_name := LEFT(
        cl_budget_schema.project_source_display_name(
            v_page.primary_source_name,
            v_source_url),
        240);
    v_meta_description := v_page.seed_cost_question
        || ' The cited figure is ' || v_amount
        || '. See the project scope, timeline and original sources.';
    IF LENGTH(v_meta_description) > 180 THEN
        v_meta_description := 'Explore the cost, scope, timeline and sources for '
            || v_page.name || '. Featured figure: ' || v_amount || '.';
    END IF;

    SELECT MD5(JSONB_BUILD_OBJECT(
        'page', TO_JSONB(v_page) - 'created_at' - 'updated_at',
        'sourceName', v_source_name,
        'sourceUrl', v_source_url,
        'metaDescription', v_meta_description,
        'image', COALESCE((
            SELECT TO_JSONB(image) - 'project_id' - 'created_at' - 'updated_at'
            FROM cl_budget_schema.project_image_projection image
            WHERE image.project_id = p_project_id), '{}'::JSONB),
        'costs', COALESCE((
            SELECT JSONB_AGG(
                TO_JSONB(cost) - 'id' - 'project_id' - 'created_at'
                ORDER BY cost.cost_kind, cost.claim_fingerprint)
            FROM cl_budget_schema.project_cost cost
            WHERE cost.project_id = p_project_id
                AND cost.is_primary
                AND cost.verification_status <> 'DISPUTED'), '[]'::JSONB),
        'metrics', COALESCE((
            SELECT JSONB_AGG(
                TO_JSONB(metric) - 'id' - 'project_id' - 'created_at'
                ORDER BY metric.metric_type, metric.claim_fingerprint)
            FROM cl_budget_schema.project_metric metric
            WHERE metric.project_id = p_project_id
                AND metric.is_primary), '[]'::JSONB),
        'milestones', COALESCE((
            SELECT JSONB_AGG(
                TO_JSONB(milestone) - 'id' - 'project_id' - 'created_at'
                ORDER BY milestone.milestone_type, milestone.claim_fingerprint)
            FROM cl_budget_schema.project_schedule_milestone milestone
            WHERE milestone.project_id = p_project_id
                AND milestone.is_primary), '[]'::JSONB),
        'references', COALESCE((
            SELECT JSONB_AGG(
                TO_JSONB(reference) - 'id' - 'created_at'
                ORDER BY reference.display_order, reference.source_url)
            FROM cl_budget_schema.project_seo_seed_reference reference
            WHERE reference.wikidata_id = v_page.wikidata_id), '[]'::JSONB),
        'facts', COALESCE((
            SELECT JSONB_AGG(
                TO_JSONB(fact) - 'id' - 'created_at'
                ORDER BY fact.display_order, fact.fact_label)
            FROM cl_budget_schema.project_seo_seed_fact fact
            WHERE fact.wikidata_id = v_page.wikidata_id), '[]'::JSONB)
    )::TEXT)
    INTO v_content_fingerprint;

    UPDATE cl_budget_schema.project_seo_page existing_page
    SET publication_status = 'PUBLISHED',
        published_at = COALESCE(existing_page.published_at, NOW()),
        updated_at = NOW()
    WHERE existing_page.wikidata_id = v_page.wikidata_id
        AND existing_page.content_origin = 'EDITORIAL'
    RETURNING existing_page.content_origin INTO v_content_origin;

    IF v_content_origin = 'EDITORIAL' THEN
        RETURN;
    END IF;

    INSERT INTO cl_budget_schema.project_seo_page (
        wikidata_id,
        slug,
        cost_question,
        cost_kind,
        seo_title,
        meta_description,
        keywords,
        intro,
        answer_detail,
        scope_text,
        context_text,
        methodology_text,
        source_name,
        source_url,
        publication_status,
        published_at,
        content_origin,
        generated_content_fingerprint,
        updated_by)
    VALUES (
        v_page.wikidata_id,
        v_page.seed_slug,
        v_page.seed_cost_question,
        v_page.seed_cost_kind,
        CASE
            WHEN LENGTH(v_page.seed_cost_question) + 13 <= 90
                THEN v_page.seed_cost_question || ' | Clueterra'
            ELSE LEFT(v_page.name, 60) || ' project cost | Clueterra'
        END,
        v_meta_description,
        LEFT(
            v_page.name || ' cost, how much did ' || v_page.name || ' cost, '
                || LOWER(REPLACE(v_page.category, '_', ' ')) || ' cost, megaproject costs',
            600),
        NULLIF(BTRIM(v_page.description), ''),
        NULL,
        NULL,
        NULL,
        NULL,
        v_source_name,
        v_source_url,
        'PUBLISHED',
        NOW(),
        v_generated_origin,
        v_content_fingerprint,
        v_materializer_actor)
    ON CONFLICT (wikidata_id) DO UPDATE
    SET slug = EXCLUDED.slug,
        cost_question = EXCLUDED.cost_question,
        cost_kind = EXCLUDED.cost_kind,
        seo_title = EXCLUDED.seo_title,
        meta_description = EXCLUDED.meta_description,
        keywords = EXCLUDED.keywords,
        intro = EXCLUDED.intro,
        answer_detail = EXCLUDED.answer_detail,
        scope_text = EXCLUDED.scope_text,
        context_text = EXCLUDED.context_text,
        methodology_text = EXCLUDED.methodology_text,
        source_name = EXCLUDED.source_name,
        source_url = EXCLUDED.source_url,
        publication_status = 'PUBLISHED',
        published_at = COALESCE(cl_budget_schema.project_seo_page.published_at, NOW()),
        content_origin = EXCLUDED.content_origin,
        generated_content_fingerprint = EXCLUDED.generated_content_fingerprint,
        updated_at = CASE
            WHEN cl_budget_schema.project_seo_page.generated_content_fingerprint
                    IS DISTINCT FROM EXCLUDED.generated_content_fingerprint
                THEN NOW()
            ELSE cl_budget_schema.project_seo_page.updated_at
        END
    WHERE cl_budget_schema.project_seo_page.content_origin = v_generated_origin
    RETURNING content_origin INTO v_content_origin;

    IF v_content_origin IS DISTINCT FROM v_generated_origin THEN
        RETURN;
    END IF;

    DELETE FROM cl_budget_schema.project_seo_reference reference
    WHERE reference.wikidata_id = v_page.wikidata_id;

    WITH source_candidates AS (
        SELECT
            10 AS priority,
            seed_reference.source_name,
            seed_reference.source_url,
            seed_reference.claim_context
        FROM cl_budget_schema.project_seo_seed_reference seed_reference
        WHERE seed_reference.wikidata_id = v_page.wikidata_id
        UNION ALL
        SELECT
            20 AS priority,
            COALESCE(v_page.description_source_name, 'Project description source'),
            v_page.description_source_url,
            'Background source for ' || v_page.name || '.'
        WHERE v_page.description_source_url ~ '^https?://'
    ), unique_sources AS (
        SELECT DISTINCT ON (source_url)
            priority,
            LEFT(BTRIM(source_name), 240) AS source_name,
            BTRIM(source_url) AS source_url,
            claim_context
        FROM source_candidates
        WHERE source_url ~ '^https?://'
            AND BTRIM(source_name) <> ''
            AND source_url <> v_source_url
        ORDER BY source_url, priority
    ), ordered_sources AS (
        SELECT
            source_name,
            source_url,
            claim_context,
            ROW_NUMBER() OVER (ORDER BY priority, source_name, source_url) AS display_order
        FROM unique_sources
        ORDER BY priority, source_name, source_url
        LIMIT 12
    )
    INSERT INTO cl_budget_schema.project_seo_reference (
        wikidata_id,
        source_name,
        source_url,
        claim_context,
        display_order)
    SELECT
        v_page.wikidata_id,
        source_name,
        source_url,
        claim_context,
        display_order
    FROM ordered_sources;

    DELETE FROM cl_budget_schema.project_seo_fact fact
    WHERE fact.wikidata_id = v_page.wikidata_id;

    INSERT INTO cl_budget_schema.project_seo_fact (
        wikidata_id,
        fact_label,
        fact_value,
        source_name,
        source_url,
        display_order)
    SELECT
        seed_fact.wikidata_id,
        seed_fact.fact_label,
        seed_fact.fact_value,
        seed_fact.source_name,
        seed_fact.source_url,
        seed_fact.display_order
    FROM cl_budget_schema.project_seo_seed_fact seed_fact
    WHERE seed_fact.wikidata_id = v_page.wikidata_id
    ORDER BY seed_fact.display_order;
END;
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.refresh_seeded_project_page_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_project_id BIGINT;
BEGIN
    IF TG_OP = 'UPDATE'
        AND TO_JSONB(NEW) - 'created_at' - 'updated_at'
            IS NOT DISTINCT FROM TO_JSONB(OLD) - 'created_at' - 'updated_at' THEN
        RETURN NEW;
    END IF;

    v_project_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.project_id ELSE NEW.project_id END;
    PERFORM cl_budget_schema.materialize_seeded_project_page(v_project_id);
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION cl_budget_schema.refresh_seeded_project_from_project_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
        NEW.name,
        NEW.description,
        NEW.country_name,
        NEW.category,
        NEW.project_year,
        NEW.is_active)
        IS NOT DISTINCT FROM ROW(
            OLD.name,
            OLD.description,
            OLD.country_name,
            OLD.category,
            OLD.project_year,
            OLD.is_active) THEN
        RETURN NEW;
    END IF;

    PERFORM cl_budget_schema.materialize_seeded_project_page(NEW.id);
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_budget_project_cost_refresh_seeded_page
AFTER INSERT OR UPDATE OR DELETE ON cl_budget_schema.project_cost
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.refresh_seeded_project_page_trigger();

CREATE CONSTRAINT TRIGGER trg_budget_project_metric_refresh_seeded_page
AFTER INSERT OR UPDATE OR DELETE ON cl_budget_schema.project_metric
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.refresh_seeded_project_page_trigger();

CREATE CONSTRAINT TRIGGER trg_budget_project_milestone_refresh_seeded_page
AFTER INSERT OR UPDATE OR DELETE ON cl_budget_schema.project_schedule_milestone
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.refresh_seeded_project_page_trigger();

CREATE CONSTRAINT TRIGGER trg_budget_project_image_refresh_seeded_page
AFTER INSERT OR UPDATE OR DELETE ON cl_budget_schema.project_image
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.refresh_seeded_project_page_trigger();

CREATE TRIGGER trg_budget_project_refresh_seeded_page
AFTER UPDATE OF name, description, country_name, category, project_year, is_active
ON cl_budget_schema.project
FOR EACH ROW EXECUTE FUNCTION cl_budget_schema.refresh_seeded_project_from_project_trigger();
