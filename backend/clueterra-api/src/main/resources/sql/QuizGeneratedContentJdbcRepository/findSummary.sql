SELECT (SELECT COUNT(*) FROM cl_schema.quiz_game_country WHERE is_active) AS active_countries,
       (SELECT COUNT(*) FROM cl_schema.quiz_game_question WHERE is_active) AS active_questions,
       (SELECT COUNT(DISTINCT user_id) FROM cl_api_schema.quiz_game_progress) AS players,
       (SELECT COUNT(*) FROM cl_api_schema.quiz_game_progress WHERE explored_at IS NOT NULL) AS explored,
       (SELECT COUNT(*) FROM cl_api_schema.quiz_game_answer) AS answers,
       (SELECT COUNT(*) FROM cl_api_schema.quiz_game_answer WHERE correct) AS correct
