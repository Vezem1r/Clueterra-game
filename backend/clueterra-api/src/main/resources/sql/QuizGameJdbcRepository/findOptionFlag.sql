SELECT question.options -> CAST(:option AS INTEGER) ->> 'iso'
FROM cl_api_schema.quiz_game_player_state state
JOIN cl_schema.quiz_game_question question
    ON question.id = state.question_ids[CAST(:position AS INTEGER)]
WHERE state.user_id = :userId
  AND state.stop_number = :stop
  AND question.options -> CAST(:option AS INTEGER) ->> 'type' = 'FLAG'
