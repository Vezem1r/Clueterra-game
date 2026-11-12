SELECT DISTINCT question.name_en
FROM cl_schema.river_game_question question
WHERE question.is_active
  AND LOWER(question.name_en) LIKE '%' || LOWER(:query) || '%'
ORDER BY question.name_en
LIMIT 12
