WITH scoped_countries AS (
    SELECT c.id,
           c.iso_alpha2,
           c.name_en,
           c.flag_emoji,
           c.flag_url
    FROM cl_schema.countries c
    WHERE c.is_active = TRUE
      AND (:scopeType = 'ALL'
        OR (:scopeType = 'REGION' AND c.region = :scopeValue)
        OR (:scopeType = 'SUBREGION' AND c.subregion = :scopeValue))
)
SELECT sc.iso_alpha2 AS code,
       sc.name_en    AS name,
       sc.flag_emoji AS flag,
       sc.flag_url   AS flag_url
FROM scoped_countries sc
WHERE NOT EXISTS (
    SELECT 1
    FROM user_country_solutions solved
    WHERE solved.user_id = :userId
      AND solved.country_id = sc.id
)
ORDER BY sc.name_en
LIMIT :limit
