SELECT COUNT(*)
FROM cl_schema.river_game_question q
JOIN cl_schema.countries c ON c.id = q.country_id
