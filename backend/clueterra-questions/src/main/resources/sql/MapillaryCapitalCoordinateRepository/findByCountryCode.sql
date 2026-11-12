SELECT country_code, capital_name, latitude, longitude
FROM cl_staging.capital_coordinates
WHERE country_code = :countryCode
