CREATE OR REPLACE FUNCTION cl_budget_schema.assess_editorial_budget_candidate (
    p_category TEXT,
    p_description TEXT,
    p_catalog_quality_score INTEGER,
    p_content JSONB
)
RETURNS TABLE (
    quality_status TEXT,
    evidence_tier TEXT,
    cost_confidence_score INTEGER,
    quality_flags JSONB
)
AS $$
DECLARE
    v_answer JSONB;
    v_flags TEXT[] := ARRAY[]::TEXT[];
    v_evidence_tier TEXT;
    v_base_confidence INTEGER;
    v_confidence INTEGER;
    v_category_cost_ceiling NUMERIC;
    v_answer_cost_usd NUMERIC;
    v_cost_year INTEGER;
    v_source_currency TEXT;
    v_source_code TEXT;
    v_source_key TEXT;
    v_source_name TEXT;
    v_reference_url TEXT;
    v_scope_note TEXT;
    v_verification_status TEXT;
    v_declared_evidence_tier TEXT;
    v_blocked BOOLEAN;
BEGIN
    SELECT claim.value
    INTO v_answer
    FROM JSONB_ARRAY_ELEMENTS(COALESCE(p_content -> 'costClaims', '[]'::JSONB)) claim(value)
    WHERE COALESCE((claim.value ->> 'answer')::BOOLEAN, FALSE)
    LIMIT 1;

    IF v_answer IS NULL THEN
        RAISE EXCEPTION 'Editorial quality assessment requires a playable cost claim';
    END IF;

    v_answer_cost_usd := (
        (v_answer ->> 'amountUsdLow')::NUMERIC
        + (v_answer ->> 'amountUsdHigh')::NUMERIC
    ) / 2;
    v_cost_year := NULLIF(v_answer ->> 'costYear', '')::INTEGER;
    v_source_currency := v_answer ->> 'sourceCurrency';
    v_source_code := v_answer ->> 'sourceCode';
    v_source_key := COALESCE(v_answer ->> 'sourceKey', '');
    v_source_name := COALESCE(v_answer ->> 'sourceName', '');
    v_reference_url := NULLIF(BTRIM(v_answer ->> 'referenceUrl'), '');
    v_scope_note := COALESCE(v_answer ->> 'scopeNote', '');
    v_verification_status := COALESCE(v_answer ->> 'verificationStatus', 'SOURCE_BACKED');
    v_declared_evidence_tier := p_content ->> 'evidenceTier';

    IF p_description IS NULL
        OR LENGTH(BTRIM(p_description)) < 40
        OR LOWER(BTRIM(p_description)) ~ '^(airport|bridge|dam|energy project|metro|railway|skyscraper|stadium|tunnel)[.]?$' THEN
        v_flags := ARRAY_APPEND(v_flags, 'WEAK_DESCRIPTION');
    END IF;

    v_category_cost_ceiling := CASE p_category
        WHEN 'BRIDGE' THEN 100000000000::NUMERIC
        WHEN 'METRO_RAIL' THEN 250000000000::NUMERIC
        WHEN 'STADIUM' THEN 15000000000::NUMERIC
        WHEN 'SKYSCRAPER' THEN 20000000000::NUMERIC
        WHEN 'AIRPORT' THEN 150000000000::NUMERIC
        WHEN 'TUNNEL' THEN 100000000000::NUMERIC
        WHEN 'DAM' THEN 150000000000::NUMERIC
        WHEN 'ENERGY' THEN 250000000000::NUMERIC
        WHEN 'SPACE' THEN 250000000000::NUMERIC
        ELSE NULL
    END;
    IF v_category_cost_ceiling IS NOT NULL AND v_answer_cost_usd > v_category_cost_ceiling THEN
        v_flags := ARRAY_APPEND(v_flags, 'CATEGORY_COST_OUTLIER');
    END IF;

    IF v_cost_year IS NOT NULL
        AND (v_source_currency = 'EUR' AND v_cost_year < 1999
            OR v_source_currency = 'DEM' AND v_cost_year > 2001) THEN
        v_flags := ARRAY_APPEND(v_flags, 'CURRENCY_ERA_MISMATCH');
    END IF;
    IF v_scope_note LIKE 'Applies to project part%' THEN
        v_flags := ARRAY_APPEND(v_flags, 'PROJECT_PART_COST');
    END IF;
    IF v_cost_year IS NULL THEN
        v_flags := ARRAY_APPEND(v_flags, 'COST_YEAR_MISSING');
    END IF;

    v_evidence_tier := CASE
        WHEN v_declared_evidence_tier = 'OFFICIAL_SOURCE'
            AND v_source_code NOT IN ('WIKIPEDIA', 'WIKIDATA')
            AND v_reference_url IS NOT NULL THEN 'OFFICIAL_SOURCE'
        WHEN v_declared_evidence_tier IN (
            'REFERENCED_KNOWLEDGE_BASE',
            'CITED_ENCYCLOPEDIA',
            'UNREFERENCED_KNOWLEDGE_BASE',
            'ENCYCLOPEDIA_SNAPSHOT',
            'ENCYCLOPEDIA_LEAD') THEN v_declared_evidence_tier
        WHEN v_source_code = 'NASA' OR v_source_key LIKE 'NASA_%' THEN 'OFFICIAL_SOURCE'
        WHEN v_source_code = 'WIKIDATA' AND v_reference_url IS NOT NULL THEN 'REFERENCED_KNOWLEDGE_BASE'
        WHEN v_source_code = 'WIKIDATA' THEN 'UNREFERENCED_KNOWLEDGE_BASE'
        WHEN v_source_key LIKE 'WIKIPEDIA_LEAD%' THEN 'ENCYCLOPEDIA_LEAD'
        WHEN v_source_code = 'WIKIPEDIA' AND v_reference_url IS NOT NULL THEN 'CITED_ENCYCLOPEDIA'
        WHEN v_source_name LIKE '%(cited)%' THEN 'CITED_ENCYCLOPEDIA'
        ELSE 'ENCYCLOPEDIA_SNAPSHOT'
    END;

    IF v_evidence_tier = 'ENCYCLOPEDIA_LEAD' THEN
        v_flags := ARRAY_APPEND(v_flags, 'LEAD_ONLY_COST');
    END IF;
    IF v_evidence_tier = 'ENCYCLOPEDIA_SNAPSHOT' AND p_catalog_quality_score < 60 THEN
        v_flags := ARRAY_APPEND(v_flags, 'LOW_AUTHORITY_COST');
    END IF;
    IF v_verification_status = 'DISPUTED' THEN
        v_flags := ARRAY_APPEND(v_flags, 'COST_SOURCE_CONFLICT');
    END IF;
    IF v_evidence_tier <> 'OFFICIAL_SOURCE' THEN
        v_flags := ARRAY_APPEND(v_flags, 'NON_AUTHORITATIVE_COST_SOURCE');
    END IF;

    v_base_confidence := CASE v_evidence_tier
        WHEN 'OFFICIAL_SOURCE' THEN 94
        WHEN 'REFERENCED_KNOWLEDGE_BASE' THEN 86
        WHEN 'CITED_ENCYCLOPEDIA' THEN 82
        WHEN 'UNREFERENCED_KNOWLEDGE_BASE' THEN 72
        WHEN 'ENCYCLOPEDIA_SNAPSHOT' THEN 68
        ELSE 52
    END;
    v_confidence := GREATEST(
        0,
        LEAST(
            100,
            v_base_confidence
                + GREATEST(-10, LEAST(6, (p_catalog_quality_score - 60) / 5))
                + CASE WHEN v_verification_status = 'CORROBORATED' THEN 8 ELSE 0 END
                - CARDINALITY(v_flags) * 8
        )
    );
    v_blocked := v_flags && ARRAY[
        'WEAK_DESCRIPTION',
        'CATEGORY_COST_OUTLIER',
        'CURRENCY_ERA_MISMATCH',
        'PROJECT_PART_COST',
        'LEAD_ONLY_COST',
        'LOW_AUTHORITY_COST',
        'COST_SOURCE_CONFLICT',
        'NON_AUTHORITATIVE_COST_SOURCE'
    ]::TEXT[];

    RETURN QUERY SELECT
        CASE WHEN v_blocked OR v_confidence < 65 THEN 'NEEDS_REVIEW' ELSE 'AUTO_APPROVED' END,
        v_evidence_tier,
        v_confidence,
        TO_JSONB(v_flags);
END;
$$
LANGUAGE plpgsql
IMMUTABLE;

CREATE OR REPLACE FUNCTION cl_budget_schema.revalidate_editorial_budget_candidate ()
RETURNS TRIGGER
AS $$
DECLARE
    v_quality_status TEXT;
    v_evidence_tier TEXT;
    v_cost_confidence_score INTEGER;
    v_quality_flags JSONB;
BEGIN
    IF NEW.editorial_content IS NULL
        OR NEW.editorial_content IS NOT DISTINCT FROM OLD.editorial_content THEN
        RETURN NEW;
    END IF;

    SELECT assessment.quality_status,
           assessment.evidence_tier,
           assessment.cost_confidence_score,
           assessment.quality_flags
    INTO v_quality_status,
         v_evidence_tier,
         v_cost_confidence_score,
         v_quality_flags
    FROM cl_budget_schema.assess_editorial_budget_candidate(
        NEW.category,
        NEW.description,
        NEW.quality_score,
        NEW.editorial_content
    ) assessment;

    NEW.quality_status := v_quality_status;
    NEW.evidence_tier := v_evidence_tier;
    NEW.cost_confidence_score := v_cost_confidence_score;
    NEW.quality_flags := v_quality_flags;
    NEW.source_payload := COALESCE(NEW.source_payload, '{}'::JSONB)
        || JSONB_BUILD_OBJECT(
            'qualityAssessment', JSONB_BUILD_OBJECT(
                'status', v_quality_status,
                'evidenceTier', v_evidence_tier,
                'confidenceScore', v_cost_confidence_score,
                'flags', v_quality_flags
            )
        );

    RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_revalidate_editorial_budget_candidate
BEFORE UPDATE OF editorial_content
ON cl_budget_staging.wikidata_candidate
FOR EACH ROW
EXECUTE FUNCTION cl_budget_schema.revalidate_editorial_budget_candidate();
