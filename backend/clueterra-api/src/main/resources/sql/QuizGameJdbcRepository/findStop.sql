SELECT state.stop_number,
       country.iso2,
       country.names::TEXT AS names,
       country.region,
       country.subregion,
       country.tier,
       state.current_level,
       country.levels,
       state.arrival_mode,
       origin.iso2 AS from_iso2,
       destination.iso2 AS next_iso2,
       destination.names::TEXT AS next_names,
       destination.region AS next_region,
       destination.subregion AS next_subregion,
       destination.tier AS next_tier,
       state.next_mode,
       state.next_route,
       COALESCE(state.next_level, 0) AS next_level,
       questions.questions,
       xp.answer_xp
FROM cl_api_schema.quiz_game_player_state state
JOIN cl_schema.quiz_game_country country ON country.id = state.current_country_id
LEFT JOIN cl_schema.quiz_game_country origin ON origin.id = state.from_country_id
LEFT JOIN cl_schema.quiz_game_country destination ON destination.id = state.next_country_id
JOIN cl_api_schema.quiz_game_visit visit
    ON visit.user_id = state.user_id AND visit.stop_number = state.stop_number
CROSS JOIN LATERAL (
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'position', item.position,
               'id', question.id,
               'kind', question.kind,
               'subject', question.subject,
               'options', question.options,
               'answerIndex', question.answer_index,
               'fact', question.fact,
               'answer', CASE WHEN answer.question_id IS NULL OR answer.answered_at < visit.visited_at THEN NULL ELSE jsonb_build_object(
                   'selectedIndex', answer.selected_index,
                   'correct', answer.correct,
                   'xpAwarded', answer.xp_awarded) END
           ) ORDER BY item.position), '[]'::JSONB)::TEXT AS questions
    FROM unnest(state.question_ids) WITH ORDINALITY AS item(question_id, position)
    JOIN cl_schema.quiz_game_question question ON question.id = item.question_id
    LEFT JOIN cl_api_schema.quiz_game_answer answer
        ON answer.user_id = state.user_id AND answer.question_id = question.id
) questions
CROSS JOIN LATERAL (
    SELECT COALESCE(MAX(value::INTEGER), 50) * state.current_level AS answer_xp
    FROM cl_api_schema.app_settings
    WHERE key = 'quiz_game_answer_xp'
) xp
WHERE state.user_id = :userId
