SELECT question.iso2
FROM cl_api_schema.flag_game_player_state state
JOIN cl_schema.flag_game_question question ON question.id = state.current_question_id
WHERE state.user_id = :userId AND state.round_number = :round
