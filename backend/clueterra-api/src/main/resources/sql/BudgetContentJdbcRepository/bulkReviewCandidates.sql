SELECT
    cl_budget_schema.review_wikidata_candidates(
        CAST(
            :candidates AS JSONB
        ),
        :reviewStatus,
        :reviewNote,
        :reviewedBy
    )
