SELECT NOT EXISTS (
    SELECT 1
    FROM cl_schema.countries c
    WHERE c.is_active = TRUE
      AND NOT EXISTS (
          SELECT 1
          FROM user_country_solutions solved
          WHERE solved.user_id = :userId
            AND solved.country_id = c.id
      )
)
