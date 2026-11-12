CREATE INDEX idx_countries_name_trgm ON cl_schema.countries USING gin (name_en gin_trgm_ops);
