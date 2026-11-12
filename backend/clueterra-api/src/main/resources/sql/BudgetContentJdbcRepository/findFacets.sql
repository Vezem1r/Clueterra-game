WITH catalog AS (
    SELECT
        candidate.*,
        COUNT(*) OVER (PARTITION BY LOWER(BTRIM(candidate.name))) AS duplicate_name_count,
        CASE WHEN candidate.image_source_sha1 IS NULL THEN 1
            ELSE COUNT(*) OVER (PARTITION BY candidate.image_source_sha1)
        END AS duplicate_image_count
    FROM cl_budget_schema.candidate_review candidate
    WHERE candidate.last_fetched
), filtered_catalog AS (
    SELECT *
    FROM catalog
    WHERE review_status IN (:reviewStatuses)
), facets AS (
    SELECT 'TOTAL' AS dimension, 'ALL' AS key, 'All candidates' AS label, COUNT(*) AS count
    FROM catalog

    UNION ALL
    SELECT 'STATUS', review_status, review_status, COUNT(*)
    FROM catalog
    GROUP BY review_status

    UNION ALL
    SELECT 'CATEGORY', category, category, COUNT(*)
    FROM filtered_catalog
    GROUP BY category

    UNION ALL
    SELECT 'COUNTRY', country_code, MAX(country_name), COUNT(*)
    FROM filtered_catalog
    GROUP BY country_code

    UNION ALL
    SELECT 'DIFFICULTY', difficulty, difficulty, COUNT(*)
    FROM filtered_catalog
    GROUP BY difficulty

    UNION ALL
    SELECT 'REGION', region_code, region_code, COUNT(*)
    FROM filtered_catalog
    GROUP BY region_code

    UNION ALL
    SELECT
        'QUESTION_STATUS',
        CASE WHEN is_active THEN 'ACTIVE' ELSE 'RETIRED' END,
        CASE WHEN is_active THEN 'Active questions' ELSE 'Retired questions' END,
        COUNT(*)
    FROM cl_budget_schema.question_variant
    GROUP BY is_active

    UNION ALL
    SELECT 'CHALLENGE_STATUS', status, status, COUNT(*)
    FROM cl_budget_schema.daily_challenge
    GROUP BY status

    UNION ALL
    SELECT 'ISSUE', issue.key, issue.label, COUNT(*)
    FROM filtered_catalog
    CROSS JOIN LATERAL (
        VALUES
            ('DUPLICATE_NAME', 'Duplicate name', duplicate_name_count > 1),
            ('DUPLICATE_IMAGE', 'Duplicate image', duplicate_image_count > 1),
            ('SUSPICIOUS_NAME', 'Suspicious name',
                name ~* '(^|[[:space:]])(unnamed|unknown)([[:space:]]|$)|^energy project$|^project[[:space:]]*[0-9]+$'),
            ('MISSING_DESCRIPTION', 'Missing description', LENGTH(BTRIM(description)) < 60),
            ('LOW_QUALITY', 'Low quality', quality_score < 50),
            ('COST_OUTLIER', 'Cost outlier',
                JSONB_EXISTS(quality_flags, 'CATEGORY_COST_OUTLIER')),
            ('WEAK_COST_EVIDENCE', 'Weak cost evidence', JSONB_EXISTS_ANY(quality_flags, ARRAY[
                'LEAD_ONLY_COST', 'LOW_AUTHORITY_COST', 'PROJECT_PART_COST',
                'CURRENCY_ERA_MISMATCH', 'COST_SOURCE_CONFLICT'
            ]::TEXT[])),
            ('COST_REVIEW_REQUIRED', 'Cost review required',
                quality_status = 'NEEDS_REVIEW'
                    AND review_status NOT IN ('APPROVED', 'EDITORIAL_APPROVED'))
    ) issue(key, label, present)
    WHERE issue.present
    GROUP BY issue.key, issue.label
)
SELECT dimension, key, label, count
FROM facets
ORDER BY dimension, count DESC, label
