SELECT question.id,
       question.rank_in_country,
       question.plain_image_key,
       question.borders_image_key,
       question.course::TEXT AS course,
       state.current_hints_used,
       state.current_attempts,
       state.round_number,
       EXISTS (
           SELECT 1 FROM cl_api_schema.user_xp_events xp
           WHERE xp.user_id = state.user_id AND xp.source_type = 'RIVER_GAME'
             AND xp.source_id = SUBSTRING(question.river_key FROM 2)::BIGINT
       ) AS already_rewarded,
       COALESCE((
           SELECT JSON_AGG(JSON_BUILD_OBJECT(
                      'position', hint.position, 'kind', hint.kind,
                      'text', hint.hint_text, 'place', hint.place)
                  ORDER BY hint.position)
           FROM cl_schema.river_game_hint hint
           WHERE hint.question_id = question.id
             AND hint.position <= state.current_hints_used
       ), '[]'::JSON)::TEXT AS revealed_hints,
       xp_settings.base_xp,
       xp_settings.hint_penalty_percent
FROM cl_api_schema.river_game_player_state state
JOIN cl_schema.river_game_question question
    ON question.id = state.current_question_id AND question.is_active
CROSS JOIN (
    SELECT COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'river_game_base_xp'), 200) AS base_xp,
           COALESCE(MAX(value::INTEGER) FILTER (WHERE key = 'river_game_hint_penalty_percent'), 15)
               AS hint_penalty_percent
    FROM cl_api_schema.app_settings
    WHERE key IN ('river_game_base_xp', 'river_game_hint_penalty_percent')
) xp_settings
WHERE state.user_id = :userId
