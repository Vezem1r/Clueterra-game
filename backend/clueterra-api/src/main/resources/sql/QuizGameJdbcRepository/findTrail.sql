SELECT trail.stop_number, trail.iso2, trail.from_iso2, trail.arrival_mode, trail.route, trail.level
FROM (
    SELECT visit.stop_number, country.iso2, origin.iso2 AS from_iso2, visit.arrival_mode, visit.route, visit.level
    FROM cl_api_schema.quiz_game_visit visit
    JOIN cl_schema.quiz_game_country country ON country.id = visit.quiz_country_id
    LEFT JOIN cl_schema.quiz_game_country origin ON origin.id = visit.from_country_id
    WHERE visit.user_id = :userId
    ORDER BY visit.stop_number DESC
    LIMIT :limit
) trail
ORDER BY trail.stop_number
