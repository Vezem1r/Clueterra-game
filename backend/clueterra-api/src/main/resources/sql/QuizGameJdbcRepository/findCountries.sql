SELECT country.iso2, progress.level, country.levels
FROM cl_api_schema.quiz_game_progress progress
JOIN cl_schema.quiz_game_country country ON country.id = progress.quiz_country_id
WHERE progress.user_id = :userId
  AND progress.level > 0
ORDER BY country.iso2
