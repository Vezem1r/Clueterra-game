WITH selected_cost AS (
    SELECT COALESCE(
        (
            SELECT existing.cost_kind
            FROM cl_budget_schema.project_seo_page existing
            JOIN cl_budget_schema.project project
                ON project.wikidata_id = existing.wikidata_id
            JOIN cl_budget_schema.project_cost project_cost
                ON project_cost.project_id = project.id
                AND project_cost.cost_kind = existing.cost_kind
                AND project_cost.is_primary
                AND project_cost.verification_status <> 'DISPUTED'
            WHERE existing.wikidata_id = :wikidataId
            LIMIT 1
        ),
        (
            SELECT project_cost.cost_kind
            FROM cl_budget_schema.project project
            JOIN cl_budget_schema.project_cost project_cost
                ON project_cost.project_id = project.id
                AND project_cost.is_primary
                AND project_cost.verification_status <> 'DISPUTED'
            WHERE project.wikidata_id = :wikidataId
                AND project.is_active
            ORDER BY
                CASE WHEN
                    ROUND((project_cost.amount_usd_low + project_cost.amount_usd_high) / 2, 2)
                        = project.answer_cost_usd
                    AND ROUND((project_cost.source_amount_low + project_cost.source_amount_high) / 2, 2)
                        = project.source_amount
                    AND project_cost.source_currency = project.source_currency
                    AND project_cost.cost_year IS NOT DISTINCT FROM project.cost_year
                THEN 0 ELSE 1 END,
                project_cost.id
            LIMIT 1
        )) AS cost_kind
), saved AS (
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
        updated_by)
    SELECT
        :wikidataId,
        NULLIF(BTRIM(:slug), ''),
        :costQuestion,
        selected_cost.cost_kind,
        :seoTitle,
        :metaDescription,
        :keywords,
        :intro,
        :answerDetail,
        :scopeText,
        :contextText,
        :methodologyText,
        :sourceName,
        :sourceUrl,
        :publicationStatus,
        CASE WHEN :publicationStatus = 'PUBLISHED' THEN NOW() END,
        :updatedBy
    FROM selected_cost
    WHERE :publicationStatus = 'DRAFT'
        OR EXISTS (
            SELECT 1
            FROM cl_budget_schema.project project
            JOIN cl_budget_schema.project_image image ON image.project_id = project.id
            JOIN cl_budget_schema.project_cost cost
                ON cost.project_id = project.id
                AND cost.is_primary
                AND cost.cost_kind = selected_cost.cost_kind
                AND cost.verification_status <> 'DISPUTED'
            WHERE project.wikidata_id = :wikidataId
                AND project.is_active)
    ON CONFLICT (wikidata_id) DO UPDATE SET
        slug = EXCLUDED.slug,
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
        publication_status = EXCLUDED.publication_status,
        published_at = CASE
            WHEN EXCLUDED.publication_status = 'DRAFT' THEN NULL
            ELSE COALESCE(cl_budget_schema.project_seo_page.published_at, NOW())
        END,
        content_origin = 'EDITORIAL',
        updated_by = EXCLUDED.updated_by,
        updated_at = CASE
            WHEN ROW(
                cl_budget_schema.project_seo_page.slug,
                cl_budget_schema.project_seo_page.cost_question,
                cl_budget_schema.project_seo_page.cost_kind,
                cl_budget_schema.project_seo_page.seo_title,
                cl_budget_schema.project_seo_page.meta_description,
                cl_budget_schema.project_seo_page.keywords,
                cl_budget_schema.project_seo_page.intro,
                cl_budget_schema.project_seo_page.answer_detail,
                cl_budget_schema.project_seo_page.scope_text,
                cl_budget_schema.project_seo_page.context_text,
                cl_budget_schema.project_seo_page.methodology_text,
                cl_budget_schema.project_seo_page.source_name,
                cl_budget_schema.project_seo_page.source_url,
                cl_budget_schema.project_seo_page.publication_status)
                IS DISTINCT FROM ROW(
                    EXCLUDED.slug,
                    EXCLUDED.cost_question,
                    EXCLUDED.cost_kind,
                    EXCLUDED.seo_title,
                    EXCLUDED.meta_description,
                    EXCLUDED.keywords,
                    EXCLUDED.intro,
                    EXCLUDED.answer_detail,
                    EXCLUDED.scope_text,
                    EXCLUDED.context_text,
                    EXCLUDED.methodology_text,
                    EXCLUDED.source_name,
                    EXCLUDED.source_url,
                    EXCLUDED.publication_status)
                OR COALESCE((
                    SELECT JSONB_AGG(JSONB_BUILD_OBJECT(
                        'name', reference.source_name,
                        'url', reference.source_url,
                        'claim', reference.claim_context)
                        ORDER BY reference.display_order)
                    FROM cl_budget_schema.project_seo_reference reference
                    WHERE reference.wikidata_id = cl_budget_schema.project_seo_page.wikidata_id
                ), '[]'::JSONB) IS DISTINCT FROM CAST(:referencesJson AS JSONB)
                OR COALESCE((
                    SELECT JSONB_AGG(JSONB_BUILD_OBJECT(
                        'label', fact.fact_label,
                        'value', fact.fact_value,
                        'sourceName', fact.source_name,
                        'sourceUrl', fact.source_url)
                        ORDER BY fact.display_order)
                    FROM cl_budget_schema.project_seo_fact fact
                    WHERE fact.wikidata_id = cl_budget_schema.project_seo_page.wikidata_id
                ), '[]'::JSONB) IS DISTINCT FROM CAST(:factsJson AS JSONB)
            THEN NOW()
            ELSE cl_budget_schema.project_seo_page.updated_at
        END
    RETURNING *
), deleted_references AS (
    DELETE FROM cl_budget_schema.project_seo_reference reference
    USING saved
    WHERE reference.wikidata_id = saved.wikidata_id
    RETURNING reference.id
), inserted_references AS (
    INSERT INTO cl_budget_schema.project_seo_reference (
        wikidata_id,
        source_name,
        source_url,
        claim_context,
        display_order)
    SELECT
        saved.wikidata_id,
        item.value ->> 'name',
        item.value ->> 'url',
        item.value ->> 'claim',
        item.display_order
    FROM saved
    CROSS JOIN (SELECT COUNT(*) FROM deleted_references) deletion_barrier
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(CAST(:referencesJson AS JSONB))
        WITH ORDINALITY AS item(value, display_order)
    RETURNING id
), deleted_facts AS (
    DELETE FROM cl_budget_schema.project_seo_fact fact
    USING saved
    WHERE fact.wikidata_id = saved.wikidata_id
    RETURNING fact.id
), inserted_facts AS (
    INSERT INTO cl_budget_schema.project_seo_fact (
        wikidata_id,
        fact_label,
        fact_value,
        source_name,
        source_url,
        display_order)
    SELECT
        saved.wikidata_id,
        item.value ->> 'label',
        item.value ->> 'value',
        item.value ->> 'sourceName',
        item.value ->> 'sourceUrl',
        item.display_order
    FROM saved
    CROSS JOIN (SELECT COUNT(*) FROM deleted_facts) deletion_barrier
    CROSS JOIN LATERAL JSONB_ARRAY_ELEMENTS(CAST(:factsJson AS JSONB))
        WITH ORDINALITY AS item(value, display_order)
    RETURNING id
)
SELECT
    saved.wikidata_id,
    project.name AS project_name,
    project.country_name,
    project.category,
    project.id IS NOT NULL AS project_available,
    image.project_id IS NOT NULL AS image_available,
    saved.slug,
    saved.cost_question,
    saved.seo_title,
    saved.meta_description,
    saved.keywords,
    saved.intro,
    saved.answer_detail,
    saved.scope_text,
    saved.context_text,
    saved.methodology_text,
    saved.source_name,
    saved.source_url,
    CAST(:referencesJson AS JSONB) AS references_json,
    CAST(:factsJson AS JSONB) AS facts_json,
    saved.publication_status,
    saved.published_at,
    saved.updated_by,
    saved.updated_at
FROM saved
LEFT JOIN cl_budget_schema.project project
    ON project.wikidata_id = saved.wikidata_id
    AND project.is_active
LEFT JOIN cl_budget_schema.project_image image
    ON image.project_id = project.id
