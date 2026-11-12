SELECT * FROM cl_staging.global_brands
WHERE country_code = :countryCode AND last_fetched = TRUE
ORDER BY rank
