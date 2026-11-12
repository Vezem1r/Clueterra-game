SELECT * FROM cl_staging.global_brands
WHERE last_fetched = TRUE
ORDER BY country_code, rank
