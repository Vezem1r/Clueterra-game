SELECT COUNT(*) FILTER (WHERE q.is_active) AS active_questions,
       COUNT(*) FILTER (WHERE q.is_active AND EXISTS (
           SELECT 1 FROM cl_schema.silhouette_game_hint h WHERE h.question_id = q.id AND h.kind = 'LANDMARK'
       )) AS landmark_clues,
       (SELECT COUNT(DISTINCT user_id) FROM cl_api_schema.silhouette_game_progress) AS players,
       (SELECT COUNT(*) FROM cl_api_schema.silhouette_game_progress WHERE first_solved_at IS NOT NULL) AS solves,
       (SELECT COALESCE(SUM(attempt_count), 0) FROM cl_api_schema.silhouette_game_progress) AS guesses
FROM cl_schema.silhouette_game_question q
