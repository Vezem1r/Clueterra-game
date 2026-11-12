INSERT INTO cl_staging.global_brands
(country_code, brand_name, industry, founded_year, global_recognition, rank, last_fetched, fetch_timestamp)
VALUES (:countryCode, :brandName, :industry, :foundedYear, :globalRecognition, :rank, TRUE, :fetchTimestamp)
ON CONFLICT (country_code, brand_name) DO UPDATE SET industry           = EXCLUDED.industry,
                                                     founded_year       = EXCLUDED.founded_year,
                                                     global_recognition = EXCLUDED.global_recognition,
                                                     rank               = EXCLUDED.rank,
                                                     last_fetched       = TRUE,
                                                     fetch_timestamp    = EXCLUDED.fetch_timestamp
