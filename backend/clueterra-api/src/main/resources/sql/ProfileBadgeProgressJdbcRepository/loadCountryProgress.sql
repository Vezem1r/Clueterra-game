WITH scoped_countries AS (
    SELECT c.id
    FROM cl_schema.countries c
    WHERE c.is_active = TRUE
      AND (:scopeType = 'ALL'
        OR (:scopeType = 'REGION' AND c.region = :scopeValue)
        OR (:scopeType = 'SUBREGION' AND c.subregion = :scopeValue))
)
SELECT COUNT(sc.id)::INT              AS total_count,
       COUNT(solved.country_id)::INT  AS completed_count
FROM scoped_countries sc
         LEFT JOIN user_country_solutions solved
                   ON solved.user_id = :userId
                  AND solved.country_id = sc.id
