SELECT question.iso2
FROM cl_api_schema.silhouette_game_progress progress
JOIN cl_schema.silhouette_game_question question ON question.id = progress.question_id
WHERE progress.user_id = :userId AND progress.first_solved_at IS NOT NULL
ORDER BY progress.first_solved_at, question.iso2
