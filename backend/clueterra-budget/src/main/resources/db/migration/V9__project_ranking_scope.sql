CREATE TABLE cl_budget_schema.project_ranking_scope (
    wikidata_id VARCHAR(20) PRIMARY KEY
        REFERENCES cl_budget_schema.project_seo_page (wikidata_id) ON DELETE CASCADE,
    ranking_scope VARCHAR(20) NOT NULL,
    CONSTRAINT chk_budget_project_ranking_scope CHECK (ranking_scope IN ('EXPANSION', 'ACCESS_LINK'))
);
